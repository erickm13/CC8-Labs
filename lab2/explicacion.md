# Laboratorio 02 - HTTP RFC 2616

## ¿Qué se implementó?

El laboratorio consistió en implementar un servidor web HTTP en Java.
La parte de concurrencia (ThreadPool, sockets TCP) ya venía hecha.
Lo que se tuvo que implementar fue cómo el servidor **entiende** lo que
pide el browser (`Request.java`) y cómo **responde** correctamente (`Response.java`).

---

# PERSONA 1 — Request.java

## ¿Qué hace Request.java?

Lee el texto crudo que llega por el socket TCP y lo convierte en datos
estructurados que `Response.java` puede usar.

Cuando el browser hace una petición, manda texto así:

```
POST /test02/displayFields.cc8 HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded
Content-Length: 73

name=Juan&email=lab02%40galileo.edu
```

Tiene tres partes:
1. **Request line** — método + ruta + versión HTTP
2. **Headers** — metadatos separados por `:`
3. **Body** — solo en POST, los datos del formulario

---

## Cómo se lee la Request Line

```java
String requestLine = dataIn.readLine();
String[] parts = requestLine.trim().split("\\s+", 3);
method  = parts[0];  // "GET" o "POST"
path    = parts[1];  // "/test02/displayFields.cc8"
version = parts[2];  // "HTTP/1.1"
```

Para GET, la ruta puede traer query string:
```
GET /test02/displayFields.cc8?name=Juan&email=lab02@galileo.edu HTTP/1.1
```
Se separa el `?` para quedarse solo con el path y parsear los parámetros aparte.

---

## Cómo se leen los Headers

Se leen línea por línea hasta encontrar la línea vacía que indica el fin de headers:

```java
while ((line = dataIn.readLine()) != null) {
    if (line.trim().isEmpty()) break; // línea vacía = fin de headers
    int colonIndex = line.indexOf(':');
    headers.put(key, value); // "Content-Type" → "application/x-www-form-urlencoded"
}
```

---

## Cómo se parsea el Body según Content-Type

El body solo existe en POST. Su formato depende del `Content-Type`:

### application/x-www-form-urlencoded
```
name=Juan&email=lab02%40galileo.edu
```
Se divide por `&`, luego por `=`, y se aplica URL decode (`%40` → `@`).

### application/json
```json
{"name":"Juan","email":"lab02@galileo.edu"}
```
Se quitan las llaves y comillas, se divide por `,` y luego por `:`.

### text/plain
```
name=Juan
email=lab02@galileo.edu
```
Se divide por línea y luego por `=`.

### multipart/form-data
Es el más complejo. El browser inventa un **boundary** aleatorio y lo usa
como separador entre campos:

```
Content-Type: multipart/form-data; boundary=----geckoformboundary4726a3a7
```

El body se ve así:
```
------geckoformboundary4726a3a7
Content-Disposition: form-data; name="name"

Juan
------geckoformboundary4726a3a7
Content-Disposition: form-data; name="email"

lab02@galileo.edu
------geckoformboundary4726a3a7--
```

El boundary es random para que nunca coincida con el contenido del formulario.
El código lo extrae del header con regex y divide el body por ese separador:

```java
Pattern boundaryPat = Pattern.compile("boundary=(.+)");
String boundary = "--" + matcher.group(1).trim();
String[] parts  = body.split(Pattern.quote(boundary));
```

Luego de cada parte extrae el nombre del campo con otro regex:
```java
Pattern namePat = Pattern.compile("name=\"([^\"]*)\"");
// y el valor está después del doble salto de línea \r\n\r\n
```

---

## Resultado final de Request.java

Retorna un `Map` con todo estructurado:
```java
{
  method  = "POST",
  path    = "/test02/displayFields.cc8",
  version = "HTTP/1.1",
  headers = { "Content-Type": "application/x-www-form-urlencoded", ... },
  params  = { "name": "Juan", "email": "lab02@galileo.edu" },
  body    = "name=Juan&email=lab02%40galileo.edu"
}
```

---
---

# PERSONA 2 — Response.java

## ¿Qué hace Response.java?

Recibe el `Map` que armó `Request.java` y genera la respuesta HTTP
correcta — busca el archivo en disco, determina su tipo, lo procesa
si es necesario y lo manda al browser.

---

## Cómo encuentra el archivo en disco

Toda la carpeta de contenido está en `./www/`. El `BASE_DIR` apunta ahí:

```java
private static final Path BASE_DIR = Paths.get("www").toAbsolutePath().normalize();
// → /home/.../lab2/www
```

Cuando llega el path `/test01/images/galileo.jpg`, lo resuelve así:

```java
Path resolved = BASE_DIR.resolve("test01/images/galileo.jpg");
// → /home/.../lab2/www/test01/images/galileo.jpg
```

Tiene protección contra **path traversal** — si alguien manda
`/../../../etc/passwd` el servidor lo bloquea con un 403:

```java
return resolved.startsWith(BASE_DIR) ? resolved : null;
// si la ruta se sale de www/ → retorna null → 403 Forbidden
```

Si la ruta es un directorio busca `index.html` dentro.
Si el archivo no existe manda un 404.

---

## Cómo determina el tipo de contenido (MIME Types)

Tiene un mapa que relaciona extensión con Content-Type:

```java
map.put("html", "text/html; charset=UTF-8");
map.put("css",  "text/css");
map.put("js",   "application/javascript");
map.put("jpg",  "image/jpeg");
map.put("png",  "image/png");
map.put("ttf",  "font/ttf");
map.put("cc8",  "text/html; charset=UTF-8");
```

Extrae la extensión del archivo y busca en el mapa:
```java
String ext         = getExtension(filePath); // "jpg"
String contentType = MIME_TYPES.get(ext);    // "image/jpeg"
```

---

## Diferencia entre archivos de texto y binarios

```java
boolean isText = contentType.startsWith("text/") || ...
```

**Archivos de texto** (HTML, CSS, JS, cc8):
- Se leen como `String` con UTF-8
- Se pueden modificar (reemplazar tags)
- Se convierten de vuelta a `byte[]` para enviar

**Archivos binarios** (imágenes, fuentes):
- Se leen directamente como `byte[]`
- No se tocan — si se convirtieran a String se corromperían
- Se mandan tal cual al browser

---

## Procesamiento de archivos .cc8

Los `.cc8` son HTML con tags especiales `{keyValue}`. El servidor los
reemplaza con los valores que mandó el formulario:

```java
// displayFields.cc8 contiene:
// <p>{name}</p>
// <p>{email}</p>

for (Map.Entry<String, String> entry : params.entrySet()) {
    content = content.replace("{" + entry.getKey() + "}", entry.getValue());
}

// resultado:
// <p>Juan</p>
// <p>lab02@galileo.edu</p>
```

Funciona igual para GET (parámetros en la URL) y POST (parámetros en el body).

---

## Cómo se construye y manda la respuesta HTTP

```java
String headers = "HTTP/1.1 200 OK\r\n" +
                 "Content-Type: image/jpeg\r\n" +
                 "Content-Length: 54231\r\n" +
                 "ClaseCC8: Alumnos\r\n" +
                 "Connection: close\r\n\r\n";  // línea vacía obligatoria

dataOut.print(headers);    // headers siempre como texto
dataOut.write(fileBytes);  // body como bytes (funciona para texto y binario)
dataOut.flush();
```

El `\r\n\r\n` al final de los headers es obligatorio — sin él el browser
no sabe dónde terminan los headers y dónde empieza el contenido.

`ClaseCC8: Alumnos` es un header personalizado del laboratorio — no tiene
efecto en el browser pero el profesor lo verifica en las DevTools.

---

## Flujo completo de una petición

```
Browser             ThreadServer         Request          Response
  │                      │                  │                │
  │── GET /test01/ ─────>│                  │                │
  │                      │── getData() ────>│                │
  │                      │                  │ lee method,    │
  │                      │                  │ path, headers  │
  │                      │<── Map ──────────│                │
  │                      │── sendData() ───────────────────>│
  │                      │                                   │ busca archivo
  │                      │                                   │ lee bytes
  │                      │                                   │ arma headers
  │<── HTTP/1.1 200 OK ──────────────────────────────────────│
  │<── [bytes del archivo] ──────────────────────────────────│
```
