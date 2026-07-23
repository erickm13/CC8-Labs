import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.*;
import java.util.logging.*;

public class Request {
    // ======================================
    // No modificar la firma de la función.
    public Object getData (Logger LOGGER, BufferedReader dataIn, Integer nThreadServer) throws Exception  {
    // ======================================

        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> params  = new LinkedHashMap<>();
        String method  = "";
        String path    = "";
        String version = "";
        String body    = "";
        int contentLength = 0;

        // Leer línea de solicitud HTTP
        String requestLine = dataIn.readLine();
        if (requestLine != null && !requestLine.trim().isEmpty()) {
            String[] parts = requestLine.trim().split("\\s+", 3);
            if (parts.length >= 3) {
                method  = parts[0];
                path    = parts[1];
                version = parts[2];
            }
        }

        // Leer encabezados
        String line;
        while ((line = dataIn.readLine()) != null) {
            if (line.trim().isEmpty()) break;
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key   = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
                if (key.equalsIgnoreCase("Content-Length")) {
                    try { contentLength = Integer.parseInt(value); }
                    catch (NumberFormatException e) { contentLength = 0; }
                }
            }
        }

        // Extraer query string del GET y limpiar el path
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            parseUrlEncoded(path.substring(queryIndex + 1), params);
            path = path.substring(0, queryIndex);
        }

        // Leer y procesar el cuerpo del POST
        if (contentLength > 0 && (method.equals("POST") || method.equals("PUT"))) {
            char[] buffer = new char[contentLength];
            int bytesRead = dataIn.read(buffer, 0, contentLength);
            body = new String(buffer, 0, Math.max(bytesRead, 0));

            String contentType = headers.getOrDefault("Content-Type", "");
            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                parseUrlEncoded(body, params);
            } else if (contentType.startsWith("application/json")) {
                parseJsonData(body, params);
            } else if (contentType.startsWith("text/plain")) {
                parseTextPlain(body, params);
            } else if (contentType.startsWith("multipart/form-data")) {
                parseMultipart(body, contentType, params);
            }
        }

        // Armar estructura de datos de la solicitud
        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("method",  method);
        requestData.put("path",    path);
        requestData.put("version", version);
        requestData.put("headers", headers);
        requestData.put("params",  params);
        requestData.put("body",    body);

        LOGGER.info("(" + nThreadServer + ") REQUEST: " + requestData);
        return requestData;

    }// getData

    // ── Parsers ───────────────────────────────────────────────────────────────

    private void parseUrlEncoded(String data, Map<String, String> params) {
        if (data == null || data.isEmpty()) return;
        for (String pair : data.split("&")) {
            int idx = pair.indexOf('=');
            try {
                if (idx > 0) {
                    String key   = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(key, value);
                } else if (!pair.isEmpty()) {
                    params.put(URLDecoder.decode(pair, "UTF-8"), "");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseJsonData(String jsonBody, Map<String, String> params) {
        // Parser simple para JSON plano: {"key":"value", ...}
        jsonBody = jsonBody.trim();
        if (jsonBody.startsWith("{")) jsonBody = jsonBody.substring(1);
        if (jsonBody.endsWith("}"))   jsonBody = jsonBody.substring(0, jsonBody.length() - 1);

        boolean inStr = false;
        int depth = 0, start = 0;
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < jsonBody.length(); i++) {
            char c = jsonBody.charAt(i);
            if (c == '"' && (i == 0 || jsonBody.charAt(i - 1) != '\\')) inStr = !inStr;
            if (!inStr) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    pairs.add(jsonBody.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        if (start < jsonBody.length()) pairs.add(jsonBody.substring(start).trim());

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx <= 0) continue;
            String key = pair.substring(0, colonIdx).trim().replaceAll("\"", "");
            String val = pair.substring(colonIdx + 1).trim().replaceAll("\"", "");
            params.put(key, val);
        }
    }

    private void parseTextPlain(String data, Map<String, String> params) {
        for (String line : data.split("\r\n|\n")) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                params.put(parts[0].trim(), parts[1].trim());
            } else if (!line.trim().isEmpty()) {
                params.put(line.trim(), "");
            }
        }
    }

    private void parseMultipart(String body, String contentType, Map<String, String> params) {
        // Extraer boundary del Content-Type header
        Pattern boundaryPat = Pattern.compile("boundary=(.+)");
        Matcher matcher = boundaryPat.matcher(contentType);
        if (!matcher.find()) return;

        String boundary = "--" + matcher.group(1).trim();
        String[] parts  = body.split(Pattern.quote(boundary));

        for (String part : parts) {
            if (part.trim().isEmpty() || part.contains("--")) continue;

            // Buscar nombre del campo en Content-Disposition
            Pattern namePat = Pattern.compile("name=\"([^\"]*)\"");
            Matcher nameMatcher = namePat.matcher(part);
            if (!nameMatcher.find()) continue;
            String fieldName = nameMatcher.group(1);

            // El valor está después del doble salto de línea
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd == -1) continue;
            String fieldValue = part.substring(headerEnd + 4).trim();
            if (fieldValue.endsWith("\r\n"))
                fieldValue = fieldValue.substring(0, fieldValue.length() - 2);

            params.put(fieldName, fieldValue);
        }
    }
}
