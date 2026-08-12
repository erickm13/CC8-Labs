/* ntp.c
 * -----
 * Implementacion del protocolo NTP (RFC 5905): construccion/parseo del paquete
 * de 48 bytes, conversion de timestamps y consulta concurrente a varios
 * servidores usando select().
 */
#include "ntpclient.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <netdb.h>
#include <arpa/inet.h>

/* ---------------------------------------------------------------------------
 * Conversion de timestamps NTP <-> UNIX
 *
 * Un timestamp NTP son 64 bits:
 *   - 32 bits de segundos desde 1900 (parte entera)
 *   - 32 bits de fraccion de segundo  (fraccion / 2^32)
 * ------------------------------------------------------------------------- */

void ntp_ts_to_bytes(double unix_time, uint8_t *out8) {
    /* Pasamos de epoca UNIX (1970) a epoca NTP (1900). */
    double ntp = unix_time + NTP_UNIX_DELTA;

    uint32_t secs = (uint32_t) ntp;                       /* parte entera    */
    uint32_t frac = (uint32_t) ((ntp - secs) * NTP_FRAC_SCALE); /* fraccion  */

    /* NTP usa orden de red (big-endian). */
    uint32_t nsecs = htonl(secs);
    uint32_t nfrac = htonl(frac);

    memcpy(out8,     &nsecs, 4);
    memcpy(out8 + 4, &nfrac, 4);
}

double ntp_bytes_to_ts(const uint8_t *in8) {
    uint32_t nsecs, nfrac;
    memcpy(&nsecs, in8,     4);
    memcpy(&nfrac, in8 + 4, 4);

    uint32_t secs = ntohl(nsecs);
    uint32_t frac = ntohl(nfrac);

    /* Regresamos a epoca UNIX restando el delta de 70 anios. */
    return (double) secs - NTP_UNIX_DELTA + (double) frac / NTP_FRAC_SCALE;
}

/* ---------------------------------------------------------------------------
 * Construccion de la solicitud
 *
 * Primer byte = LI(2) | VN(3) | Mode(3):
 *   LI   = 0   -> sin aviso de salto
 *   VN   = 3   -> version 3 (binario 011)
 *   Mode = 3   -> cliente  (binario 011)
 *   => 00 011 011 = 0x1B
 * El resto del paquete va en cero, salvo el Transmit Timestamp (nuestro T1).
 * ------------------------------------------------------------------------- */
void ntp_build_request(uint8_t *buf, double t1) {
    memset(buf, 0, NTP_PACKET_SIZE);
    buf[0] = 0x1B;                         /* LI=0, VN=3, Mode=3 (cliente)   */
    ntp_ts_to_bytes(t1, buf + 40);         /* Transmit Timestamp (bytes 40..47) */
}

/* Segundos transcurridos (reloj MONOTONO) desde un instante de referencia.
 * Se usa solo para medir el timeout de select(), no para los timestamps NTP. */
static double mono_elapsed(const struct timespec *start) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec - start->tv_sec) +
           (now.tv_nsec - start->tv_nsec) / 1e9;
}

/* Abre un socket UDP conectado al host indicado (resuelve DNS con getaddrinfo).
 * 'connect' en UDP fija el destino y hace que recv() solo acepte datagramas
 * de ese servidor, lo que nos permite emparejar respuesta<->servidor.        */
static int open_udp_socket(const char *host) {
    struct addrinfo hints, *res = NULL, *rp;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family   = AF_UNSPEC;      /* IPv4 o IPv6 */
    hints.ai_socktype = SOCK_DGRAM;

    if (getaddrinfo(host, NTP_PORT, &hints, &res) != 0)
        return -1;

    int fd = -1;
    for (rp = res; rp != NULL; rp = rp->ai_next) {
        fd = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
        if (fd < 0) continue;
        if (connect(fd, rp->ai_addr, rp->ai_addrlen) == 0) break; /* exito */
        close(fd);
        fd = -1;
    }
    freeaddrinfo(res);
    return fd;
}

int ntp_query_all(ntp_server_t *servers, int n, int timeout_ms,
                  double (*now)(void)) {
    uint8_t buf[NTP_PACKET_SIZE];
    int pending = 0;   /* cuantas respuestas seguimos esperando */

    /* --- Fase 1: enviar la solicitud a TODOS los servidores casi a la vez --- */
    for (int i = 0; i < n; i++) {
        servers[i].ok   = 0;
        servers[i].sock = open_udp_socket(servers[i].host);
        if (servers[i].sock < 0) continue;

        servers[i].t1 = now();                 /* T1: instante de envio      */
        ntp_build_request(buf, servers[i].t1);

        if (send(servers[i].sock, buf, NTP_PACKET_SIZE, 0) == NTP_PACKET_SIZE) {
            pending++;
        } else {
            close(servers[i].sock);
            servers[i].sock = -1;
        }
    }

    /* --- Fase 2: recoger respuestas con select() hasta agotar el timeout --- */
    struct timespec start;
    clock_gettime(CLOCK_MONOTONIC, &start);
    double timeout_s = timeout_ms / 1000.0;

    while (pending > 0) {
        double remain = timeout_s - mono_elapsed(&start);
        if (remain <= 0) break;

        fd_set rfds;
        FD_ZERO(&rfds);
        int maxfd = -1;
        for (int i = 0; i < n; i++) {
            if (servers[i].sock >= 0 && !servers[i].ok) {
                FD_SET(servers[i].sock, &rfds);
                if (servers[i].sock > maxfd) maxfd = servers[i].sock;
            }
        }
        if (maxfd < 0) break;

        struct timeval tv;
        tv.tv_sec  = (long) remain;
        tv.tv_usec = (long) ((remain - tv.tv_sec) * 1e6);

        int r = select(maxfd + 1, &rfds, NULL, NULL, &tv);
        if (r < 0) {
            if (errno == EINTR) continue;   /* interrumpido: reintentar      */
            break;
        }
        if (r == 0) break;                  /* timeout global                */

        /* Procesamos cada socket que tenga datos listos. */
        for (int i = 0; i < n; i++) {
            if (servers[i].sock < 0 || servers[i].ok) continue;
            if (!FD_ISSET(servers[i].sock, &rfds)) continue;

            ssize_t got = recv(servers[i].sock, buf, sizeof(buf), 0);
            double t4 = now();              /* T4: instante de recepcion     */
            if (got != NTP_PACKET_SIZE) continue;  /* respuesta invalida     */

            servers[i].t4 = t4;
            servers[i].t2 = ntp_bytes_to_ts(buf + 32); /* Receive  Timestamp */
            servers[i].t3 = ntp_bytes_to_ts(buf + 40); /* Transmit Timestamp */

            /* Formulas del RFC 5905 (y del enunciado):
             *   offset = ((T2 - T1) + (T3 - T4)) / 2
             *   delay  = (T4 - T1) - (T3 - T2)                              */
            servers[i].offset =
                ((servers[i].t2 - servers[i].t1) +
                 (servers[i].t3 - servers[i].t4)) / 2.0;
            servers[i].delay =
                (servers[i].t4 - servers[i].t1) -
                (servers[i].t3 - servers[i].t2);

            servers[i].ok = 1;
            pending--;
        }
    }

    /* Cerramos todos los sockets y contamos exitos. */
    int nok = 0;
    for (int i = 0; i < n; i++) {
        if (servers[i].sock >= 0) close(servers[i].sock);
        servers[i].sock = -1;
        if (servers[i].ok) nok++;
    }
    return nok;
}
