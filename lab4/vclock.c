/* vclock.c
 * --------
 * Reloj interno SIMULADO.
 *
 * En lugar de tocar el reloj real del sistema operativo (lo cual necesita
 * privilegios y modifica el equipo), mantenemos un reloj virtual:
 *
 *     reloj_interno = tiempo_real_del_SO + offset_acumulado
 *
 * - Al arrancar lo inicializamos a una fecha configurable (formato Guatemala).
 * - Cada vez que NTP nos da un offset, lo sumamos a 'g_offset', acercando el
 *   reloj interno a la hora real de los servidores.
 *
 * El reloj interno "avanza" solo porque se basa en el tiempo real del SO;
 * nosotros unicamente ajustamos el desfase.
 */
#include "ntpclient.h"
#include <time.h>

/* Desfase (en segundos) que sumamos al tiempo real para obtener el interno. */
static double g_offset = 0.0;

double vclock_real_now(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (double) ts.tv_sec + ts.tv_nsec / 1e9;
}

double vclock_now(void) {
    return vclock_real_now() + g_offset;
}

void vclock_init_to(double target) {
    /* Queremos que vclock_now() == target en este instante. */
    g_offset = target - vclock_real_now();
}

void vclock_adjust(double delta) {
    g_offset += delta;
}

double vclock_get_offset(void) {
    return g_offset;
}
