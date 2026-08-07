import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DnsServer
 * ---------
 * Servidor DNS sobre UDP.
 *
 * A diferencia de TCP (Lab #2, un socket por conexion), UDP usa un UNICO
 * DatagramSocket. El hilo principal solo hace de "dispatcher":
 *
 *     bucle: recibir paquete  ->  entregarlo al ThreadPool
 *
 * Cada paquete se procesa en un DnsWorker dentro del pool de hilos, igual que
 * el ThreadPool del Lab #2. Asi el servidor atiende multiples consultas en
 * paralelo sin bloquearse mientras espera respuestas recursivas.
 */
public class DnsServer {

    private final int port;
    private final int threads;
    private final DnsResolver resolver;
    private final Logger log;

    public DnsServer(int port, int threads, DnsResolver resolver, Logger log) {
        this.port = port;
        this.threads = threads;
        this.resolver = resolver;
        this.log = log;
    }

    public void start() throws Exception {
        // Pool de hilos fijo (equivalente al ThreadPool del Lab #2)
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            log.info("Servidor DNS escuchando en UDP:" + port
                    + " | threads=" + threads
                    + " | upstream=" + resolver.getUpstream());

            byte[] buffer = new byte[65535]; // maximo de un datagrama UDP

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // bloquea hasta recibir un datagrama

                // Delegamos el procesamiento al pool (el paquete se copia dentro
                // del worker antes de que el buffer se reutilice).
                pool.execute(new DnsWorker(packet, socket, resolver, log));
            }
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Configura el Logger: salida a consola + archivo en logs/ con formato
     * estructurado (LogFormatter).
     */
    public static Logger buildLogger() {
        Logger log = Logger.getLogger("DnsServer");
        log.setUseParentHandlers(false); // evitamos el handler por defecto
        log.setLevel(Level.ALL);

        LogFormatter fmt = new LogFormatter();

        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(fmt);
        console.setLevel(Level.ALL);
        log.addHandler(console);

        try {
            new File("logs").mkdirs();
            long stamp = System.currentTimeMillis();
            FileHandler file = new FileHandler("logs/dns-" + stamp + ".log", true);
            file.setFormatter(fmt);
            file.setLevel(Level.ALL);
            log.addHandler(file);
        } catch (Exception e) {
            log.warning("No se pudo crear el archivo de log: " + e.getMessage());
        }
        return log;
    }
}
