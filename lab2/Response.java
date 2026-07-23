import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.*;

public class Response {

    private static final Path BASE_DIR = Paths.get("www").toAbsolutePath().normalize();

    private static final Map<String, String> MIME_TYPES = buildMimeTypes();
    private static Map<String, String> buildMimeTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("html",  "text/html; charset=UTF-8");
        map.put("css",   "text/css");
        map.put("js",    "application/javascript");
        map.put("json",  "application/json");
        map.put("jpg",   "image/jpeg");
        map.put("jpeg",  "image/jpeg");
        map.put("png",   "image/png");
        map.put("gif",   "image/gif");
        map.put("ico",   "image/x-icon");
        map.put("svg",   "image/svg+xml");
        map.put("ttf",   "font/ttf");
        map.put("woff",  "font/woff");
        map.put("woff2", "font/woff2");
        map.put("txt",   "text/plain");
        map.put("cc8",   "text/html; charset=UTF-8");
        return Collections.unmodifiableMap(map);
    }

    // ======================================
    // No modificar la firma de la función.
    @SuppressWarnings("unchecked")
    public void sendData (Logger LOGGER, PrintStream dataOut, Integer nThreadServer, Object request) throws Exception {
    // ======================================

        Map<String, Object> req    = (Map<String, Object>) request;
        String method              = (String) req.get("method");
        String path                = (String) req.get("path");
        Map<String, String> params = (Map<String, String>) req.get("params");

        // Ruta raíz → index.html
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // Resolver ruta de forma segura (evitar path traversal)
        Path filePath = resolveSafePath(path);
        if (filePath == null) {
            sendError(403, "Forbidden", dataOut);
            LOGGER.warning("(" + nThreadServer + ") Path traversal bloqueado: " + path);
            return;
        }

        // Directorios → buscar index.html dentro
        if (Files.isDirectory(filePath)) {
            filePath = filePath.resolve("index.html");
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            sendError(404, "Not Found", dataOut);
            LOGGER.warning("(" + nThreadServer + ") Archivo no encontrado: " + filePath);
            return;
        }

        // Determinar tipo de contenido por extensión
        String ext         = getExtension(filePath);
        String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        boolean isText     = contentType.startsWith("text/") ||
                             contentType.startsWith("application/javascript") ||
                             contentType.startsWith("application/json");

        byte[] fileBytes = Files.readAllBytes(filePath);

        if (isText || ext.equals("cc8")) {
            String content = new String(fileBytes, StandardCharsets.UTF_8);

            // Reemplazar {keyValue} con los parámetros del formulario
            for (Map.Entry<String, String> entry : params.entrySet()) {
                content = content.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            // Demo del index principal
            if (path.endsWith("index.html") && content.contains("{fieldTest_DEMO}")) {
                content = content.replace("{fieldTest_DEMO}",
                    "El servidor cambió esto y agregó un número aleatorio: " + new Random().nextInt(1000));
            }

            fileBytes = content.getBytes(StandardCharsets.UTF_8);
        }

        // Construir y enviar respuesta HTTP
        String headers = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: "   + contentType   + "\r\n" +
                         "Content-Length: " + fileBytes.length + "\r\n" +
                         "ClaseCC8: Alumnos\r\n" +
                         "Connection: close\r\n\r\n";

        dataOut.print(headers);
        if (!method.equals("HEAD")) {
            dataOut.write(fileBytes);
        }
        dataOut.flush();

        LOGGER.info("(" + nThreadServer + ") RESPONSE: 200 OK - " + filePath);
        LOGGER.info("(" + nThreadServer + ") REQUEST procesado: path=" + path + " params=" + params);

    }// sendData

    // ── Utilidades ────────────────────────────────────────────────────────────

    /** Resuelve la ruta solo si permanece dentro de BASE_DIR */
    private Path resolveSafePath(String requestPath) {
        try {
            Path resolved = BASE_DIR.resolve(requestPath.substring(1)).normalize();
            return resolved.startsWith(BASE_DIR) ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot + 1).toLowerCase() : "";
    }

    private void sendError(int code, String message, PrintStream out) {
        String body = "<html><body><h1>" + code + " " + message + "</h1></body></html>";
        String response = "HTTP/1.1 " + code + " " + message + "\r\n" +
                          "Content-Type: text/html\r\n" +
                          "Content-Length: " + body.length() + "\r\n" +
                          "Connection: close\r\n\r\n" +
                          body;
        out.print(response);
        out.flush();
    }
}
