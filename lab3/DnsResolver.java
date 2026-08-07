import java.io.BufferedReader;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DnsResolver
 * -----------
 * Resuelve consultas DNS en dos etapas:
 *
 *   1. ZONA LOCAL: registros cargados desde un archivo (records.db). Aqui
 *      implementamos de forma COMPLETA los tipos A, AAAA y PTR: construimos
 *      nosotros mismos el RDATA de la respuesta.
 *
 *   2. RECURSIVIDAD (forwarding): si el nombre no esta en la zona local y el
 *      cliente pidio recursion (RD=1), reenviamos la consulta cruda a un
 *      servidor DNS publico (por defecto 8.8.8.8) y retransmitimos su respuesta.
 *      Esto cubre cualquier otro tipo de registro automaticamente.
 */
public class DnsResolver {

    /** Un registro de la zona local. */
    private static final class LocalRecord {
        final int type;
        final long ttl;
        final String value; // IP (A/AAAA) o nombre de dominio (PTR)

        LocalRecord(int type, long ttl, String value) {
            this.type = type;
            this.ttl = ttl;
            this.value = value;
        }
    }

    // Mapa: nombre (en minusculas) -> lista de registros de ese nombre
    private final Map<String, List<LocalRecord>> zone = new HashMap<>();

    private final String upstream;      // DNS publico para recursion
    private final int upstreamPort;     // normalmente 53
    private final int upstreamTimeoutMs; // timeout de espera de la respuesta

    public DnsResolver(String upstream, int upstreamPort, int upstreamTimeoutMs) {
        this.upstream = upstream;
        this.upstreamPort = upstreamPort;
        this.upstreamTimeoutMs = upstreamTimeoutMs;
    }

    /**
     * Carga la zona local desde un archivo de texto simple.
     * Formato por linea (separado por espacios/tabs):
     *
     *     nombre    TIPO    valor    [ttl]
     *
     * Lineas vacias o que empiezan con '#' se ignoran.
     */
    public void loadZone(String path, Logger log) {
        int loaded = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;

                String name = parts[0].toLowerCase();
                int type = parseType(parts[1]);
                String value = parts[2];
                long ttl = (parts.length >= 4) ? parseLong(parts[3], 300) : 300;

                if (type < 0) continue;

                zone.computeIfAbsent(name, k -> new ArrayList<>())
                    .add(new LocalRecord(type, ttl, value));
                loaded++;
            }
            log.info("Zona local cargada desde '" + path + "': " + loaded + " registros");
        } catch (Exception e) {
            log.warning("No se pudo cargar la zona local '" + path + "': " + e.getMessage()
                    + " (se seguira funcionando solo con recursividad)");
        }
    }

    /**
     * Intenta resolver la consulta desde la zona local.
     * Devuelve la lista de Answers si hay coincidencia, o null si no hay nada
     * local (el caller decidira si reenvia a un DNS publico).
     */
    public List<DnsMessage.Answer> resolveLocal(String qname, int qtype) {
        List<LocalRecord> records = zone.get(qname.toLowerCase());
        if (records == null) return null;

        List<DnsMessage.Answer> answers = new ArrayList<>();
        for (LocalRecord r : records) {
            if (r.type != qtype) continue;
            byte[] rdata = buildRData(r);
            if (rdata != null) {
                answers.add(new DnsMessage.Answer(r.type, r.ttl, rdata));
            }
        }
        // Si el nombre existe pero no para ese tipo, devolvemos lista vacia
        // (NOERROR con 0 respuestas) en lugar de null, para no reenviar.
        return answers;
    }

    /** Convierte el valor textual de un registro local a su RDATA en bytes. */
    private byte[] buildRData(LocalRecord r) {
        try {
            switch (r.type) {
                case DnsMessage.TYPE_A:
                    // IPv4: 4 bytes
                    return InetAddress.getByName(r.value).getAddress();
                case DnsMessage.TYPE_AAAA:
                    // IPv6: 16 bytes
                    return InetAddress.getByName(r.value).getAddress();
                case DnsMessage.TYPE_PTR:
                    // El RDATA de un PTR es un nombre de dominio codificado
                    return DnsMessage.encodeName(r.value);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null; // valor invalido en el archivo de zona
        }
    }

    /**
     * Reenvia la consulta cruda a un servidor DNS publico y devuelve la
     * respuesta cruda (para retransmitirla tal cual al cliente).
     * Devuelve null si falla o hay timeout.
     */
    public byte[] forward(byte[] query, int length) {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(upstreamTimeoutMs);
            InetAddress up = InetAddress.getByName(upstream);

            DatagramPacket out = new DatagramPacket(query, length, up, upstreamPort);
            sock.send(out);

            byte[] buf = new byte[65535];
            DatagramPacket in = new DatagramPacket(buf, buf.length);
            sock.receive(in);

            byte[] resp = new byte[in.getLength()];
            System.arraycopy(in.getData(), 0, resp, 0, in.getLength());
            return resp;
        } catch (Exception e) {
            return null;
        }
    }

    public String getUpstream() {
        return upstream + ":" + upstreamPort;
    }

    private static int parseType(String s) {
        switch (s.toUpperCase()) {
            case "A":     return DnsMessage.TYPE_A;
            case "AAAA":  return DnsMessage.TYPE_AAAA;
            case "PTR":   return DnsMessage.TYPE_PTR;
            case "NS":    return DnsMessage.TYPE_NS;
            case "CNAME": return DnsMessage.TYPE_CNAME;
            default:      return -1;
        }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }
}
