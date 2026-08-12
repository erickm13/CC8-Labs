#ifndef NTPCLIENT_H
#define NTPCLIENT_H

#include <stdint.h>
#include <stddef.h>

/* ---------------------------------------------------------------------------
 * Constantes del protocolo NTP (RFC 5905)
 * ------------------------------------------------------------------------- */
#define NTP_PACKET_SIZE 48          /* El encabezado NTP son 48 bytes         */
#define NTP_PORT        "123"       /* Puerto UDP estandar de NTP             */

/* Diferencia entre la epoca NTP (1-ene-1900) y la epoca UNIX (1-ene-1970).
 * Son 70 anios (incluyendo bisiestos) = 2,208,988,800 segundos.             */
#define NTP_UNIX_DELTA  2208988800.0

/* 2^32, usado para convertir la fraccion de segundo (32 bits) a decimal.    */
#define NTP_FRAC_SCALE  4294967296.0

/* ---------------------------------------------------------------------------
 * Estado de la consulta a un servidor NTP en una ronda.
 *
 * Los 4 timestamps del algoritmo de sincronizacion:
 *   t1: el cliente ENVIA la solicitud      (reloj interno)
 *   t2: el servidor RECIBE la solicitud    (viene en la respuesta)
 *   t3: el servidor RESPONDE               (viene en la respuesta)
 *   t4: el cliente RECIBE la respuesta     (reloj interno)
 * ------------------------------------------------------------------------- */
typedef struct {
    const char *host;   /* nombre del servidor (ej: "pool.ntp.org")          */
    int    sock;        /* descriptor del socket UDP (-1 si fallo)           */
    int    ok;          /* 1 si recibimos una respuesta valida               */
    double t1, t2, t3, t4;   /* los cuatro timestamps (segundos UNIX)        */
    double offset;      /* offset (theta) calculado, en segundos             */
    double delay;       /* delay (delta) calculado, en segundos              */
} ntp_server_t;

/* ---------------------------------------------------------------------------
 * ntp.c  -  protocolo y timestamps
 * ------------------------------------------------------------------------- */

/* Convierte un tiempo UNIX (segundos con fraccion) a un timestamp NTP de
 * 64 bits en orden de red, escrito en los 8 bytes 'out8'.                   */
void   ntp_ts_to_bytes(double unix_time, uint8_t *out8);

/* Convierte un timestamp NTP de 64 bits (8 bytes en orden de red) a tiempo
 * UNIX en segundos con fraccion.                                            */
double ntp_bytes_to_ts(const uint8_t *in8);

/* Rellena un paquete de solicitud NTP (modo cliente) de 48 bytes en 'buf',
 * colocando 't1' como Transmit Timestamp.                                   */
void   ntp_build_request(uint8_t *buf, double t1);

/* Consulta a los 'n' servidores CASI SIMULTANEAMENTE:
 *   1) envia la solicitud a todos
 *   2) espera las respuestas con select() hasta 'timeout_ms' milisegundos
 * Para cada respuesta valida rellena t2,t3,t4, offset y delay.
 * 'now' es la funcion del reloj interno (para t1 y t4).
 * Devuelve cuantos servidores respondieron correctamente.                   */
int    ntp_query_all(ntp_server_t *servers, int n, int timeout_ms,
                     double (*now)(void));

/* ---------------------------------------------------------------------------
 * vclock.c  -  reloj interno simulado
 * ------------------------------------------------------------------------- */

/* Tiempo real del sistema (UNIX, segundos con fraccion).                    */
double vclock_real_now(void);

/* Tiempo del reloj INTERNO simulado = tiempo real + offset acumulado.       */
double vclock_now(void);

/* Inicializa el reloj interno para que marque 'target' (tiempo UNIX).       */
void   vclock_init_to(double target);

/* Ajusta el reloj interno sumando 'delta' segundos (aplica un offset NTP).  */
void   vclock_adjust(double delta);

/* Offset acumulado actual del reloj interno respecto al tiempo real.        */
double vclock_get_offset(void);

#endif /* NTPCLIENT_H */
