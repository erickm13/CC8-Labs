
# Laboratorio 3 - Servidor DNS sobre UDP

Este Laboratorio Consiste en la implementación de un servidor DNS utilizando el protocolo UDP. El laboratorio contiene dos componentes principales:

- Código en **C** (`lab_3.c`) para manejar sockets UDP.
- Código en **Java** (`UdpBroadcastSender.java`) para enviar mensajes en modo broadcast sobre UDP.
- Un Archivo Makefile para agiliar la compilacion y manejo de archivos.

Este código es proporcionado para que lo utilice como ejemplo para inicar en la lectura del protocolo de DNS que utiliza su sistema operativo para comunicarse. 

---

## 🛠️ Makefile - Compilación y ejecución

El `Makefile` incluido facilita la compilación, ejecución y limpieza del entorno. A continuación, se describen los comandos disponibles:

### Cómo usar el Makefile

- **Compilar el programa en C:**
  ```bash
  make
  ```
  Genera el binario `udp_test`.

- **Ejecutar el binario compilado:**
  ```bash
  make run
  ```

- **Ejecutar el programa Java (broadcast sender):**
  ```bash
  make runjava
  ```

- **Liberar el puerto UDP 9999 si está ocupado:**
  ```bash
  make killudp
  ```
  Si no esta ocupado deberia lanzar un Error por no encontrar ninguna concidencia

- **Limpiar archivos compilados:**
  ```bash
  make clean
  ```

---

## Descripción del laboratorio

### Objetivo

Implementar un **Servidor DNS** en Java que escuche en el puerto `53` (UDP), sea capaz de interpretar correctamente consultas DNS, construir respuestas válidas, y manejar recursividad hacia servidores DNS públicos.

### 🔧 Requisitos principales

- Escuchar en `UDP:53`.
- Construir mensajes DNS respetando la estructura definida por el [RFC 5395](https://datatracker.ietf.org/doc/html/rfc5395).
- Soportar múltiples tipos de registros: `A`, `AAAA`, `PTR`, `SOA`, `SVCB`, `HTTPS`, etc.
- Implementar recursividad con reenvío hacia servidores DNS públicos.
- Registrar logs con cada request/response.
- Multithreading con `ThreadPool` (basado en el Lab #2). (en Java)
- Implementar al menos 3 tipos de registros de forma completa.

### 🧪 Configuración para pruebas

- Configura tu equipo para que use `127.0.0.1` como DNS primario. (Importante)
- Agrega un DNS externo secundario como respaldo (Google, Cloudflare, etc.). (Opcional)
- Limpia cachés DNS del sistema operativo antes de hacer pruebas: 

```bash
# Para Ubuntu 17.04 y superior (18.04)
sudo systemd-resolve --flush-caches

# Para Ubuntu 22.04 y superior
sudo resolvectl flush-caches
```

```bash
# Para MACOS
sudo dscacheutil -flushcache; sudo killall -HUP mDNSResponder"
```

```bash
# Para Windows
•  Asegúrate de que te encuentras en el escritorio de Windows 10 o Windows 11.
•  Haz clic derecho en el menú de inicio y elige ""Símbolo del sistema (Administrador)"" o ""Windows PowerShell (Administrador)"".
•  Introduce el comando ""ipconfig /flushdns"".
•  Pulsa la tecla Intro de tu teclado."

```

> Usa Wireshark para analizar paquetes en `udp.port == 53`.


### Descripcion de Laboratorio y Ejemplos en el siguiente enlace:

> https://docs.google.com/spreadsheets/d/1uCG9dcFXyNORk5xggtNzUZxyhIXZPvKW_mXAM1srDCE/edit?usp=sharing


### Entregable

- Archivos fuente `.java`, `.c`, `Makefile`,  Depende de su implementacion
- Archivo `README.md`.
- Video explicativo (si no presentas presencialmente).
- Compilación y ejecución en sistema Debian (preferentemente sobre BeagleBone Black para obtener hasta 150%).

---

## Requisitos técnicos

- Java versión 21 o 22.
- Código en C compilable con `gcc`. (si elige esta opción)
- Uso de `DatagramSocket` para manejo de UDP. (si usa JAVA)
- Logs estructurados.
- Manejo de errores sin fallos en consola.

---

## ⚠Penalizaciones para calificación Cero

- No usar laboratorios anteriores como base (DatagramSocket, ThreadPool, etc.). (si va por JAVA)
- Logs mal estructurados o inexistentes.
- Menos de 3 tipos de registros funcionales.
- Errores en consola del servidor o cliente.

---

## ⭐ Bonus

+50% si se implementa completamente en C, corriendo sobre Debian en BeagleBone Black con conexión Ethernet.

---

# Implementación (Java)

Servidor DNS sobre UDP en Java, reutilizando el patrón de **ThreadPool** del Lab #2.
Los archivos base del zip (`lab_3.c`, `UdpBroadcastSender.java`) se conservan solo como
ejemplos de referencia de sockets UDP.

## Compilar y ejecutar

```bash
make               # compila todos los .java (servidor DNS)
make run           # ejecuta en el puerto 53 (usa sudo, el puerto 53 es privilegiado)
make runtest       # ejecuta en el puerto 5353 para probar SIN privilegios
make clean         # borra .class, binario C y logs

# Argumentos personalizados con ARGS:
make run ARGS="-port 5353 -threads 8 -upstream 1.1.1.1"

# O directamente tras compilar:
java Lab03 -port 5353 -threads 4 -upstream 8.8.8.8
java Lab03 -help
```

### Argumentos

| Argumento        | Descripción                                   | Default     |
|------------------|-----------------------------------------------|-------------|
| `-port <int>`    | Puerto UDP de escucha                         | `53`        |
| `-threads <int>` | Hilos del ThreadPool                          | `4`         |
| `-upstream <ip>` | DNS público usado para recursividad           | `8.8.8.8`   |
| `-upport <int>`  | Puerto del DNS público                        | `53`        |
| `-timeout <ms>`  | Timeout de espera del upstream                | `3000`      |
| `-zone <path>`   | Archivo de zona local                         | `records.db`|
| `-help`          | Muestra la ayuda y no inicia el servidor      | —           |

## Arquitectura

```
                 ┌───────────── DnsServer ──────────────┐
   cliente  ──►  │  DatagramSocket (UDP:53)              │
   (dig)         │  bucle: receive() ─► pool.execute()   │
                 └──────────────────┬───────────────────┘
                                    │  (un DnsWorker por paquete)
                          ┌─────────▼──────────┐
                          │     DnsWorker      │
                          │  parse ► resolver  │
                          └───┬────────────┬───┘
                       local  │            │ recursivo (RD=1)
                    ┌─────────▼──┐   ┌─────▼─────────┐
                    │ DnsResolver│   │  8.8.8.8:53   │
                    │ (records.db)│  │  (forward)    │
                    └────────────┘   └───────────────┘
```

- **`Lab03`** — punto de entrada; parsea argumentos y arranca el servidor.
- **`DnsServer`** — abre un único `DatagramSocket` UDP y despacha cada datagrama
  recibido a un `ThreadPool` fijo (`Executors.newFixedThreadPool`). En UDP no hay
  conexión, por eso un solo socket atiende a todos los clientes.
- **`DnsWorker`** (`Runnable`) — procesa **un** paquete: parsea la consulta, la
  resuelve (local o recursiva) y envía la respuesta. Los errores de paquetes
  malformados se capturan para que nunca tumben un hilo ni ensucien la consola.
- **`DnsResolver`** — (1) zona local desde `records.db` y (2) reenvío recursivo.
- **`DnsMessage`** — parseo y construcción de mensajes DNS (cabecera, question,
  resource records) según RFC 1035, incluyendo compresión de nombres por punteros.
- **`LogFormatter`** — formato estructurado de logs (consola + `logs/dns-*.log`).

## Tipos de registro implementados

Se construye el RDATA manualmente para tres tipos (requisito de ≥3):

- **A** (IPv4) — 4 bytes.
- **AAAA** (IPv6) — 16 bytes.
- **PTR** (reverse DNS) — nombre de dominio codificado.

Cualquier otro tipo/nombre que no esté en la zona local se resuelve por
**recursividad**: se reenvía la consulta cruda al DNS público y se retransmite su
respuesta al cliente (esto cubre CNAME, MX, TXT, SOA, etc. automáticamente).

## Zona local (`records.db`)

Formato por línea: `nombre  TIPO  valor  [ttl]`. Ejemplo:

```
example.local               A     192.168.1.10   300
example.local               AAAA  fe80::1        300
10.1.168.192.in-addr.arpa   PTR   example.local  300
```

## Pruebas

```bash
# Arranca el servidor en un puerto no privilegiado
java Lab03 -port 5353 &

# Con dig (si está instalado):
dig @127.0.0.1 -p 5353 example.local A
dig @127.0.0.1 -p 5353 example.local AAAA
dig @127.0.0.1 -p 5353 -x 192.168.1.10        # PTR
dig @127.0.0.1 -p 5353 google.com A            # recursivo

# Para usar el servidor como DNS del sistema, ejecútalo en el puerto 53
# (sudo) y pon 127.0.0.1 como DNS primario. Limpia la caché antes de probar.
```

---
