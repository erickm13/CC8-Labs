import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DnsWorker
 * ---------
 * Tarea (Runnable) que procesa UNA consulta DNS. El servidor recibe los
 * paquetes en el hilo principal y los despacha a un ThreadPool; cada tarea:
 *
 *   1. Parsea la Question.
 *   2. Busca en la zona local (A / AAAA / PTR construidos manualmente).
 *   3. Si no hay match local y el cliente pidio recursion, reenvia a un
 *      DNS publico y retransmite la respuesta.
 *   4. Envia la respuesta al cliente y registra el request/response en el log.
 */
public class DnsWorker implements Runnable {

    private final byte[] data;          // copia del paquete recibido
    private final int length;
    private final InetAddress clientAddr;
    private final int clientPort;
    private final DatagramSocket serverSocket; // socket compartido para responder
    private final DnsResolver resolver;
    private final Logger log;

    public DnsWorker(DatagramPacket packet, DatagramSocket serverSocket,
                     DnsResolver resolver, Logger log) {
        // Copiamos los bytes porque el buffer del servidor se reutiliza.
        this.length = packet.getLength();
        this.data = new byte[length];
        System.arraycopy(packet.getData(), packet.getOffset(), this.data, 0, length);
        this.clientAddr = packet.getAddress();
        this.clientPort = packet.getPort();
        this.serverSocket = serverSocket;
        this.resolver = resolver;
        this.log = log;
    }

    @Override
    public void run() {
        String client = clientAddr.getHostAddress() + ":" + clientPort;
        try {
            DnsMessage.Query q = DnsMessage.parseQuery(data, length);
            log.info("REQUEST  <- " + client + " | id=" + q.id
                    + " " + DnsMessage.typeName(q.qtype) + " " + q.qname
                    + " (RD=" + (q.rd ? 1 : 0) + ")");

            // Solo manejamos la clase IN
            if (q.qclass != DnsMessage.CLASS_IN) {
                sendResponse(q, DnsMessage.buildResponse(
                        data, q, DnsMessage.newAnswerList(), false, DnsMessage.RCODE_SERVFAIL),
                        client, "SERVFAIL (clase no soportada)");
                return;
            }

            // --- 1) Intento local ---
            List<DnsMessage.Answer> local = resolver.resolveLocal(q.qname, q.qtype);
            if (local != null) {
                byte[] resp = DnsMessage.buildResponse(
                        data, q, local, true,
                        local.isEmpty() ? DnsMessage.RCODE_OK : DnsMessage.RCODE_OK);
                sendResponse(q, resp, client,
                        "LOCAL (" + local.size() + " registro(s), AA=1)");
                return;
            }

            // --- 2) Recursividad hacia DNS publico ---
            if (q.rd) {
                byte[] resp = resolver.forward(data, length);
                if (resp != null) {
                    send(resp, client);
                    log.info("RESPONSE -> " + client + " | id=" + q.id
                            + " FORWARD via " + resolver.getUpstream()
                            + " (" + resp.length + " bytes)");
                } else {
                    sendResponse(q, DnsMessage.buildResponse(
                            data, q, DnsMessage.newAnswerList(), false, DnsMessage.RCODE_SERVFAIL),
                            client, "SERVFAIL (fallo/timeout en upstream)");
                }
                return;
            }

            // --- 3) Sin recursion y sin datos locales -> NXDOMAIN ---
            sendResponse(q, DnsMessage.buildResponse(
                    data, q, DnsMessage.newAnswerList(), true, DnsMessage.RCODE_NXDOMAIN),
                    client, "NXDOMAIN");

        } catch (Exception e) {
            // Nunca dejamos que un paquete malformado tumbe el hilo.
            log.log(Level.WARNING, "Error procesando paquete de " + client
                    + ": " + e.getMessage(), e);
        }
    }

    /** Envia una respuesta ya construida y la registra en el log. */
    private void sendResponse(DnsMessage.Query q, byte[] resp, String client, String note) {
        send(resp, client);
        log.info("RESPONSE -> " + client + " | id=" + q.id
                + " " + DnsMessage.typeName(q.qtype) + " " + q.qname + " | " + note);
    }

    /** Envia bytes crudos al cliente usando el socket del servidor. */
    private void send(byte[] resp, String client) {
        try {
            DatagramPacket out = new DatagramPacket(resp, resp.length, clientAddr, clientPort);
            serverSocket.send(out);
        } catch (Exception e) {
            log.warning("No se pudo enviar respuesta a " + client + ": " + e.getMessage());
        }
    }
}
