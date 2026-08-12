# Laboratorio 4 — Cliente NTP (RFC 5905)

Cliente NTP escrito **en C** que construye manualmente el paquete NTP de 48 bytes,
lo envía por UDP (puerto 123) a varios servidores de tiempo, aplica el algoritmo
de sincronización del reloj y ajusta un **reloj interno simulado** hasta dejar el
offset por debajo de un umbral configurable.

## Requisitos

- `gcc` y `make`
- Conexión a internet (para llegar a los servidores NTP)

## Compilar y ejecutar

```bash
make            # compila -> ./ntp_client
make run        # ejecuta con la configuración por defecto (hora real)
make demo       # demo: reloj interno arranca el 1-sep-2020 y se sincroniza
make clean      # borra binario, objetos y logs

# Argumentos personalizados con ARGS:
make run ARGS="-date \"2020-09-01 21:00:17.123\" -interval 10 -rounds 3"

# O directamente:
./ntp_client -date "2020-09-01 21:00:17.123" -threshold 50 -rounds 3
./ntp_client -help
```

### Argumentos

| Argumento              | Descripción                                             | Default   |
|------------------------|--------------------------------------------------------|-----------|
| `-date "…"`            | Fecha inicial del reloj interno (hora **GT**, UTC-6). Si se omite usa la hora real. | — |
| `-tz <horas>`          | Zona horaria local (Guatemala = -6)                    | `-6`      |
| `-threshold <ms>`      | Umbral de sincronización en milisegundos               | `100`     |
| `-interval <seg>`      | Espera entre rondas ya sincronizado (verificación)     | `60`      |
| `-retry <seg>`         | Espera entre rondas mientras **no** sincroniza         | `5`       |
| `-timeout <ms>`        | Espera por respuestas en cada ronda                    | `2000`    |
| `-rounds <n>`          | Número máximo de rondas (0 = infinito)                 | `0`       |
| `-servers a,b,c`       | Lista de servidores NTP (separados por coma)           | 8 por def.|
| `-logfile <ruta>`      | Archivo de log (`none` para desactivar)                | `logs/ntp-<pid>.log` |
| `-help`                | Muestra la ayuda                                        | —         |

El formato de fecha admite `YYYY-MM-DD HH:MM:SS` con milisegundos opcionales (`.mmm`).

## Estructura del proyecto

| Archivo        | Rol |
|----------------|-----|
| `ntpclient.h`  | Constantes del protocolo, estructura `ntp_server_t` y prototipos |
| `ntp.c`        | Protocolo NTP: conversión de timestamps, armado del paquete de 48 bytes y consulta concurrente con `select()` |
| `vclock.c`     | Reloj interno simulado (`tiempo_real + offset_acumulado`) |
| `main.c`       | CLI, bucle de sincronización y logging estructurado |
| `Makefile`     | Compilación (`make`, `make run`, `make demo`, `make clean`) |

## El algoritmo de sincronización

En cada ronda se consultan **≥ 7 servidores a la vez** (por defecto 8). Para cada
respuesta se registran los cuatro timestamps y se calcula, según el RFC 5905:

```
offset (θ) = ( (T2 - T1) + (T3 - T4) ) / 2
delay  (δ) = (T4 - T1) - (T3 - T2)
```

- **T1** — el cliente envía la solicitud (reloj interno)
- **T2** — el servidor recibe la solicitud (viene en la respuesta)
- **T3** — el servidor responde (viene en la respuesta)
- **T4** — el cliente recibe la respuesta (reloj interno)

Se elige el servidor con **menor delay** (mejor calidad de medición) y su offset se
aplica al reloj interno. El proceso se repite hasta que `|offset| ≤ umbral`
(sincronizado) y luego verifica cada `-interval` segundos.

### El paquete NTP (48 bytes)

El primer byte codifica `LI(2) | VN(3) | Mode(3)`; enviamos `0x1B` = versión 3,
modo cliente. El único campo que llenamos es el **Transmit Timestamp** (T1); el
resto va en cero. Los timestamps son de 64 bits: 32 de segundos desde 1900 (época
NTP) y 32 de fracción (`fracción / 2^32`). La diferencia entre la época NTP (1900)
y la UNIX (1970) es de `2 208 988 800` segundos.

### Reloj interno simulado

En lugar de tocar el reloj del sistema operativo (requiere privilegios), se
mantiene un reloj virtual: `reloj_interno = tiempo_real_del_SO + offset_acumulado`.
Se inicializa a la fecha configurada y cada offset de NTP se suma al desfase,
acercándolo a la hora real de los servidores.

## Ejemplo de salida (`make demo`)

Reloj interno arrancando el **1-sep-2020**: en la ronda 1 el offset es de ~6 años
y el reloj salta a la fecha real; en la ronda 2 el offset ya es de milisegundos y
queda **SINCRONIZADO**.

```
---------------- Ronda 1 ----------------
  Reloj interno (antes ): 2020-09-01 21:00:17.123 GT | 2020-09-02 03:00:17.123 UTC
  [OK] 1.pool.ntp.org      offset=+187047670355.901 ms  delay=61.592 ms
  => Mejor servidor: 1.pool.ntp.org (delay=61.592 ms)
  Reloj interno (despues): 2026-08-06 18:41:27.820 GT
  >>> AUN NO sincronizado: |offset|=187047670355.901 ms > umbral 100.000 ms
---------------- Ronda 2 ----------------
  => Mejor servidor: 0.south-america.pool.ntp.org (delay=29.684 ms, offset=-1.394 ms)
  >>> SINCRONIZADO: |offset|=1.394 ms <= umbral 100.000 ms
```

Cada ejecución guarda el log completo en `logs/ntp-<pid>.log`.

## Servidores por defecto

`pool.ntp.org`, `time.google.com`, `time.cloudflare.com`, `time.apple.com`,
`time.windows.com`, `time.nist.gov`, `0.south-america.pool.ntp.org`, `1.pool.ntp.org`.
