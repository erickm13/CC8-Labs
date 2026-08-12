/* main.c
 * ------
 * Cliente NTP (Lab04). Orquesta el algoritmo de sincronizacion:
 *
 *   1. Inicializa un reloj interno simulado con una fecha configurable (GT).
 *   2. En cada ronda consulta a >=7 servidores NTP a la vez.
 *   3. Elige el mejor servidor (menor delay) y calcula offset/delay.
 *   4. Ajusta el reloj interno con ese offset.
 *   5. Repite hasta que el offset sea ~cero (umbral en ms configurable) y
 *      luego verifica cada cierto intervalo (por defecto 1 minuto).
 *
 * Todo el proceso se registra en un LOG (consola + archivo).
 */
#include "ntpclient.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <math.h>
#include <time.h>
#include <stdarg.h>
#include <sys/stat.h>

#define MAX_SERVERS 32

/* Lista por defecto de servidores NTP (el enunciado pide minimo 7). */
static const char *DEFAULT_SERVERS[] = {
    "pool.ntp.org",
    "time.google.com",
    "time.cloudflare.com",
    "time.apple.com",
    "time.windows.com",
    "time.nist.gov",
    "0.south-america.pool.ntp.org",
    "1.pool.ntp.org",
};
#define DEFAULT_SERVER_COUNT ((int)(sizeof(DEFAULT_SERVERS)/sizeof(DEFAULT_SERVERS[0])))

/* ---------------------------------------------------------------------------
 * Configuracion (con valores por defecto), poblada desde los argumentos.
 * ------------------------------------------------------------------------- */
typedef struct {
    const char *servers[MAX_SERVERS];
    int    server_count;
    int    tz_hours;        /* zona horaria local (Guatemala = -6)           */
    double threshold_ms;    /* umbral de sincronizacion en milisegundos      */
    int    interval_s;      /* espera entre rondas ya sincronizado (verificar)*/
    int    retry_s;         /* espera entre rondas mientras NO sincroniza     */
    int    timeout_ms;      /* espera por respuestas en cada ronda           */
    int    max_rounds;      /* 0 = infinito                                  */
    int    has_date;        /* 1 si el usuario fijo una fecha inicial         */
    double start_unix;      /* fecha inicial del reloj interno (UNIX/UTC)     */
    const char *logfile;    /* ruta del log, o "none" para desactivar        */
} config_t;

static FILE *g_logf = NULL;   /* archivo de log (ademas de la consola) */

/* ---------------------------------------------------------------------------
 * Logging: escribe a consola y (si esta abierto) al archivo, con timestamp
 * real al inicio de cada linea. Formato estructurado.
 * ------------------------------------------------------------------------- */
static void logmsg(const char *fmt, ...) {
    char stamp[32];
    time_t now = time(NULL);
    struct tm tm;
    gmtime_r(&now, &tm);
    strftime(stamp, sizeof(stamp), "%Y-%m-%d %H:%M:%S", &tm);

    char line[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);

    printf("[%s UTC] %s\n", stamp, line);
    fflush(stdout);
    if (g_logf) {
        fprintf(g_logf, "[%s UTC] %s\n", stamp, line);
        fflush(g_logf);
    }
}

/* Formatea un tiempo UNIX a "YYYY-MM-DD HH:MM:SS.mmm" desplazado 'tz_hours'.  */
static void fmt_time(double unix_time, int tz_hours, char *out, size_t n) {
    time_t secs = (time_t) floor(unix_time);
    int ms = (int) round((unix_time - (double) secs) * 1000.0);
    if (ms >= 1000) { ms -= 1000; secs += 1; }
    if (ms < 0)     { ms += 1000; secs -= 1; }

    time_t shifted = secs + (time_t) tz_hours * 3600;
    struct tm tm;
    gmtime_r(&shifted, &tm);

    char base[32];
    strftime(base, sizeof(base), "%Y-%m-%d %H:%M:%S", &tm);
    snprintf(out, n, "%s.%03d", base, ms);
}

/* Imprime la hora del reloj interno en formato Guatemala y UTC. */
static void log_internal_clock(const char *etiqueta, const config_t *cfg) {
    double t = vclock_now();
    char gt[48], utc[48];
    fmt_time(t, cfg->tz_hours, gt, sizeof(gt));
    fmt_time(t, 0,             utc, sizeof(utc));
    logmsg("  Reloj interno (%s): %s GT  |  %s UTC", etiqueta, gt, utc);
}

/* ---------------------------------------------------------------------------
 * Parseo de la fecha inicial "YYYY-MM-DD HH:MM:SS[.mmm]" (hora local GT).
 * La convertimos a tiempo UNIX (UTC) usando la zona horaria configurada.
 * ------------------------------------------------------------------------- */
static int parse_date(const char *s, int tz_hours, double *out_unix) {
    int Y, Mo, D, H, Mi, S, ms = 0;
    int got = sscanf(s, "%d-%d-%d %d:%d:%d.%d", &Y, &Mo, &D, &H, &Mi, &S, &ms);
    if (got < 6) return -1;

    struct tm tm;
    memset(&tm, 0, sizeof(tm));
    tm.tm_year = Y - 1900;
    tm.tm_mon  = Mo - 1;
    tm.tm_mday = D;
    tm.tm_hour = H;
    tm.tm_min  = Mi;
    tm.tm_sec  = S;

    /* timegm interpreta los campos como UTC. Como la fecha es hora local
     * (GT, UTC-6), UTC = local - tz_hours (con tz_hours negativo => suma). */
    time_t as_utc = timegm(&tm);
    *out_unix = (double) as_utc - (double) tz_hours * 3600.0 + ms / 1000.0;
    return 0;
}

static void print_help(void) {
    printf(
    "Uso: ./ntp_client [opciones]\n"
    "  -date \"YYYY-MM-DD HH:MM:SS[.mmm]\"  Fecha inicial del reloj interno (hora GT).\n"
    "                                       Si se omite, arranca con la hora real.\n"
    "  -tz <horas>       Zona horaria local (Guatemala = -6).      [def: -6]\n"
    "  -threshold <ms>   Umbral de sincronizacion en milisegundos. [def: 100]\n"
    "  -interval <seg>   Espera entre rondas ya sincronizado.       [def: 60]\n"
    "  -retry <seg>      Espera entre rondas mientras no sincroniza.[def: 5]\n"
    "  -timeout <ms>     Espera por respuestas en cada ronda.       [def: 2000]\n"
    "  -rounds <n>       Numero maximo de rondas (0 = infinito).    [def: 0]\n"
    "  -servers a,b,c    Lista de servidores NTP (separados por coma).\n"
    "  -logfile <ruta>   Archivo de log (\"none\" para desactivar).\n"
    "  -help             Muestra esta ayuda.\n");
}

/* Divide "a,b,c" en la lista de servidores de la config. */
static void parse_servers(char *list, config_t *cfg) {
    cfg->server_count = 0;
    char *tok = strtok(list, ",");
    while (tok && cfg->server_count < MAX_SERVERS) {
        cfg->servers[cfg->server_count++] = tok;
        tok = strtok(NULL, ",");
    }
}

int main(int argc, char **argv) {
    config_t cfg;
    memset(&cfg, 0, sizeof(cfg));
    /* Valores por defecto */
    cfg.tz_hours     = -6;       /* Guatemala */
    cfg.threshold_ms = 100.0;
    cfg.interval_s   = 60;       /* verificar cada 1 minuto */
    cfg.retry_s      = 5;
    cfg.timeout_ms   = 2000;
    cfg.max_rounds   = 0;        /* infinito */
    cfg.logfile      = NULL;     /* se define abajo si no lo pasan */
    const char *date_str = NULL;

    /* --- Parseo de argumentos --- */
    for (int i = 1; i < argc; i++) {
        if      (!strcmp(argv[i], "-help"))  { print_help(); return 0; }
        else if (!strcmp(argv[i], "-date")      && i+1 < argc) date_str = argv[++i];
        else if (!strcmp(argv[i], "-tz")        && i+1 < argc) cfg.tz_hours = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-threshold") && i+1 < argc) cfg.threshold_ms = atof(argv[++i]);
        else if (!strcmp(argv[i], "-interval")  && i+1 < argc) cfg.interval_s = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-retry")     && i+1 < argc) cfg.retry_s = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-timeout")   && i+1 < argc) cfg.timeout_ms = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-rounds")    && i+1 < argc) cfg.max_rounds = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-logfile")   && i+1 < argc) cfg.logfile = argv[++i];
        else if (!strcmp(argv[i], "-servers")   && i+1 < argc) parse_servers(argv[++i], &cfg);
        else { fprintf(stderr, "Argumento desconocido: %s\n", argv[i]); print_help(); return 1; }
    }

    /* Servidores por defecto si no se especificaron. */
    if (cfg.server_count == 0) {
        for (int i = 0; i < DEFAULT_SERVER_COUNT; i++)
            cfg.servers[i] = DEFAULT_SERVERS[i];
        cfg.server_count = DEFAULT_SERVER_COUNT;
    }

    /* --- Abrir el archivo de log --- */
    if (!cfg.logfile) {
        mkdir("logs", 0755);
        static char path[128];
        snprintf(path, sizeof(path), "logs/ntp-%d.log", (int) getpid());
        cfg.logfile = path;
    }
    if (strcmp(cfg.logfile, "none") != 0) {
        g_logf = fopen(cfg.logfile, "w");
        if (!g_logf) fprintf(stderr, "Aviso: no se pudo abrir el log '%s'\n", cfg.logfile);
    }

    /* --- Inicializar el reloj interno --- */
    if (date_str) {
        if (parse_date(date_str, cfg.tz_hours, &cfg.start_unix) != 0) {
            fprintf(stderr, "Fecha invalida: '%s' (use YYYY-MM-DD HH:MM:SS)\n", date_str);
            return 1;
        }
        cfg.has_date = 1;
        vclock_init_to(cfg.start_unix);
    }

    /* --- Cabecera del log --- */
    logmsg("================ Cliente NTP (Lab04) ================");
    logmsg("Servidores: %d | umbral=%.0f ms | timeout=%d ms | intervalo=%ds | retry=%ds | tz=%d",
           cfg.server_count, cfg.threshold_ms, cfg.timeout_ms,
           cfg.interval_s, cfg.retry_s, cfg.tz_hours);
    if (cfg.has_date)
        logmsg("Fecha inicial del reloj interno: %s GT", date_str);
    else
        logmsg("Reloj interno arranca con la hora real del sistema.");

    /* Reservamos el arreglo de servidores. */
    ntp_server_t srv[MAX_SERVERS];
    for (int i = 0; i < cfg.server_count; i++) {
        memset(&srv[i], 0, sizeof(ntp_server_t));
        srv[i].host = cfg.servers[i];
    }

    double threshold_s = cfg.threshold_ms / 1000.0;
    int round = 0;
    int synced = 0;

    /* ------------------------- Bucle de sincronizacion ------------------- */
    while (cfg.max_rounds == 0 || round < cfg.max_rounds) {
        round++;
        logmsg("");
        logmsg("---------------- Ronda %d ----------------", round);
        log_internal_clock("antes ", &cfg);
        logmsg("  Consultando %d servidores NTP...", cfg.server_count);

        int nok = ntp_query_all(srv, cfg.server_count, cfg.timeout_ms, vclock_now);
        if (nok == 0) {
            logmsg("  [!] Ningun servidor respondio. Reintentando en %ds.", cfg.retry_s);
            sleep(cfg.retry_s);
            continue;
        }

        /* Elegimos el mejor servidor: el de MENOR delay (mejor sincronizacion). */
        int best = -1;
        for (int i = 0; i < cfg.server_count; i++) {
            if (!srv[i].ok) {
                logmsg("  [--] %-28s (sin respuesta)", srv[i].host);
                continue;
            }
            logmsg("  [OK] %-28s offset=%+.6f s (%+.3f ms)  delay=%.6f s (%.3f ms)",
                   srv[i].host,
                   srv[i].offset, srv[i].offset * 1000.0,
                   srv[i].delay,  srv[i].delay  * 1000.0);
            if (best < 0 || srv[i].delay < srv[best].delay) best = i;
        }

        logmsg("  => Mejor servidor: %s  (delay=%.3f ms, offset=%+.3f ms)",
               srv[best].host, srv[best].delay * 1000.0, srv[best].offset * 1000.0);

        /* Mostramos los 4 timestamps del elegido (T1..T4) para el LOG. */
        char s1[48], s2[48], s3[48], s4[48];
        fmt_time(srv[best].t1, cfg.tz_hours, s1, sizeof(s1));
        fmt_time(srv[best].t2, cfg.tz_hours, s2, sizeof(s2));
        fmt_time(srv[best].t3, cfg.tz_hours, s3, sizeof(s3));
        fmt_time(srv[best].t4, cfg.tz_hours, s4, sizeof(s4));
        logmsg("     T1 (envio cliente)   = %s GT", s1);
        logmsg("     T2 (recibe servidor) = %s GT", s2);
        logmsg("     T3 (responde server) = %s GT", s3);
        logmsg("     T4 (recibe cliente)  = %s GT", s4);

        /* Aplicamos el offset del mejor servidor al reloj interno. */
        double applied = srv[best].offset;
        vclock_adjust(applied);
        logmsg("  Aplicando offset de %+.3f ms al reloj interno...", applied * 1000.0);
        log_internal_clock("despues", &cfg);

        /* Comprobamos si ya estamos sincronizados. */
        if (fabs(applied) <= threshold_s) {
            if (!synced) logmsg("  >>> SINCRONIZADO: |offset|=%.3f ms <= umbral %.3f ms",
                                fabs(applied) * 1000.0, cfg.threshold_ms);
            else         logmsg("  >>> Verificacion OK: |offset|=%.3f ms <= umbral %.3f ms",
                                fabs(applied) * 1000.0, cfg.threshold_ms);
            synced = 1;
        } else {
            logmsg("  >>> AUN NO sincronizado: |offset|=%.3f ms > umbral %.3f ms",
                   fabs(applied) * 1000.0, cfg.threshold_ms);
            synced = 0;
        }

        if (cfg.max_rounds != 0 && round >= cfg.max_rounds) break;

        int wait = synced ? cfg.interval_s : cfg.retry_s;
        logmsg("  (esperando %ds antes de la siguiente ronda)", wait);
        sleep(wait);
    }

    logmsg("");
    logmsg("================ Fin (%d rondas) ================", round);
    if (g_logf) fclose(g_logf);
    return 0;
}
