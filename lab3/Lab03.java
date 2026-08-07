import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Lab03 - Servidor DNS sobre UDP
 * ------------------------------
 * Punto de entrada. Parsea los argumentos de linea de comandos, carga la zona
 * local y arranca el DnsServer (UDP + ThreadPool).
 *
 * Argumentos (todos opcionales, en cualquier orden):
 *
 *   -port <int>      Puerto UDP en el que escucha el servidor.   (default: 53)
 *   -threads <int>   Numero de hilos del ThreadPool.             (default: 4)
 *   -upstream <ip>   DNS publico para recursividad.              (default: 8.8.8.8)
 *   -upport <int>    Puerto del DNS publico.                     (default: 53)
 *   -timeout <ms>    Timeout de espera del upstream (ms).        (default: 3000)
 *   -zone <path>     Archivo de zona local.                      (default: records.db)
 *   -help            Muestra esta ayuda y no inicia el servidor.
 *
 * Ejemplos:
 *   sudo java Lab03                         (escucha en el puerto 53)
 *   java Lab03 -port 5353                   (puerto alto, sin sudo, para pruebas)
 *   java Lab03 -port 5353 -upstream 1.1.1.1 -threads 8
 */
public class Lab03 {

    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);

        if (opts.containsKey("-help")) {
            printHelp();
            return;
        }

        int port      = intOpt(opts, "-port", 53);
        int threads   = intOpt(opts, "-threads", 4);
        String upstream = opts.getOrDefault("-upstream", "8.8.8.8");
        int upPort    = intOpt(opts, "-upport", 53);
        int timeout   = intOpt(opts, "-timeout", 3000);
        String zone   = opts.getOrDefault("-zone", "records.db");

        Logger log = DnsServer.buildLogger();
        log.info("== Lab03 :: Servidor DNS/UDP iniciando ==");

        DnsResolver resolver = new DnsResolver(upstream, upPort, timeout);
        resolver.loadZone(zone, log);

        DnsServer server = new DnsServer(port, threads, resolver, log);
        try {
            server.start();
        } catch (java.net.BindException e) {
            log.severe("No se pudo enlazar al puerto " + port
                    + ". El puerto 53 requiere privilegios (usa sudo) o ya esta en uso. "
                    + "Prueba con -port 5353. Detalle: " + e.getMessage());
        } catch (Exception e) {
            log.severe("Error fatal del servidor: " + e.getMessage());
        }
    }

    /** Convierte los argumentos en un mapa clave->valor. Las banderas sin valor
     *  (como -help) quedan con valor "true". */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("-")) continue;
            if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                opts.put(key, args[i + 1]);
                i++; // consumimos el valor
            } else {
                opts.put(key, "true"); // bandera sin valor
            }
        }
        return opts;
    }

    private static int intOpt(Map<String, String> opts, String key, int def) {
        try {
            return opts.containsKey(key) ? Integer.parseInt(opts.get(key)) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void printHelp() {
        System.out.println("Uso: java Lab03 [opciones...]");
        System.out.println("  -port <int>     Puerto UDP de escucha        (default: 53)");
        System.out.println("  -threads <int>  Hilos del ThreadPool         (default: 4)");
        System.out.println("  -upstream <ip>  DNS publico para recursion   (default: 8.8.8.8)");
        System.out.println("  -upport <int>   Puerto del DNS publico       (default: 53)");
        System.out.println("  -timeout <ms>   Timeout del upstream en ms   (default: 3000)");
        System.out.println("  -zone <path>    Archivo de zona local        (default: records.db)");
        System.out.println("  -help           Muestra esta ayuda");
    }
}
