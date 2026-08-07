import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DnsMessage
 * ----------
 * Utilidades para PARSEAR consultas DNS entrantes y CONSTRUIR respuestas DNS
 * validas segun el formato del RFC 1035 / RFC 5395.
 *
 * Estructura de un mensaje DNS:
 *
 *   +---------------------+
 *   |        Header       |  12 bytes
 *   +---------------------+
 *   |       Question      |  QNAME + QTYPE(2) + QCLASS(2)
 *   +---------------------+
 *   |        Answer       |  0..n Resource Records
 *   +---------------------+
 *   |      Authority      |  (no lo generamos)
 *   +---------------------+
 *   |      Additional     |  (no lo generamos)
 *   +---------------------+
 *
 * Cabecera (12 bytes):
 *   ID(16) | FLAGS(16) | QDCOUNT(16) | ANCOUNT(16) | NSCOUNT(16) | ARCOUNT(16)
 *
 * FLAGS: QR(1) Opcode(4) AA(1) TC(1) RD(1) RA(1) Z(3) RCODE(4)
 */
public final class DnsMessage {

    // Tipos de registro (TYPE)
    public static final int TYPE_A     = 1;   // Direccion IPv4
    public static final int TYPE_NS    = 2;   // Name Server
    public static final int TYPE_CNAME = 5;   // Alias
    public static final int TYPE_SOA   = 6;   // Start Of Authority
    public static final int TYPE_PTR   = 12;  // Reverse DNS (IP -> nombre)
    public static final int TYPE_AAAA  = 28;  // Direccion IPv6

    // Clase (CLASS)
    public static final int CLASS_IN = 1;     // Internet

    // Codigos de respuesta (RCODE)
    public static final int RCODE_OK        = 0;  // Sin error
    public static final int RCODE_SERVFAIL  = 2;  // Fallo del servidor
    public static final int RCODE_NXDOMAIN  = 3;  // Dominio no existe

    private DnsMessage() { } // clase de utilidades, no instanciable

    /**
     * Representa la consulta (Question) extraida de un paquete entrante.
     */
    public static final class Query {
        public final int id;              // ID de la transaccion
        public final boolean rd;          // Recursion Desired
        public final String qname;        // Nombre consultado (ej: "example.local")
        public final int qtype;           // Tipo consultado (A, AAAA, PTR...)
        public final int qclass;          // Clase consultada (normalmente IN)
        public final int questionStart;   // Offset donde inicia la Question (=12)
        public final int questionEnd;     // Offset justo despues de la Question

        Query(int id, boolean rd, String qname, int qtype, int qclass,
              int questionStart, int questionEnd) {
            this.id = id;
            this.rd = rd;
            this.qname = qname;
            this.qtype = qtype;
            this.qclass = qclass;
            this.questionStart = questionStart;
            this.questionEnd = questionEnd;
        }
    }

    /**
     * Representa un Resource Record de respuesta ya listo para serializar.
     * El RDATA se entrega en crudo (bytes) segun el tipo.
     */
    public static final class Answer {
        public final int type;
        public final long ttl;
        public final byte[] rdata;

        public Answer(int type, long ttl, byte[] rdata) {
            this.type = type;
            this.ttl = ttl;
            this.rdata = rdata;
        }
    }

    // -------------------------------------------------------------------------
    // PARSEO
    // -------------------------------------------------------------------------

    /**
     * Parsea la cabecera y la primera Question de un paquete DNS.
     * Solo leemos la primera pregunta (los clientes envian QDCOUNT=1).
     */
    public static Query parseQuery(byte[] data, int len) throws Exception {
        if (len < 12) {
            throw new Exception("Paquete DNS demasiado corto (" + len + " bytes)");
        }

        int id = u16(data, 0);
        int flags = u16(data, 2);
        boolean rd = (flags & 0x0100) != 0; // bit RD
        int qdCount = u16(data, 4);
        if (qdCount < 1) {
            throw new Exception("El paquete no contiene ninguna Question");
        }

        // La Question comienza justo despues de la cabecera (offset 12)
        int[] nameEnd = new int[1];
        String qname = readName(data, 12, len, nameEnd);
        int p = nameEnd[0];

        if (p + 4 > len) {
            throw new Exception("Question incompleta (faltan QTYPE/QCLASS)");
        }
        int qtype = u16(data, p);
        int qclass = u16(data, p + 2);
        int questionEnd = p + 4;

        return new Query(id, rd, qname, qtype, qclass, 12, questionEnd);
    }

    /**
     * Lee un nombre DNS (secuencia de labels) soportando compresion por punteros.
     * Devuelve el nombre en texto ("www.ejemplo.com") y deja en outNextOffset[0]
     * el offset del byte siguiente al nombre EN LA SECUENCIA ORIGINAL (no dentro
     * del puntero seguido).
     */
    public static String readName(byte[] data, int offset, int len, int[] outNextOffset)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        int p = offset;
        boolean jumped = false;
        int nextOffset = -1;
        int safety = 0; // evita bucles infinitos con punteros maliciosos

        while (true) {
            if (p >= len) throw new Exception("Nombre DNS fuera de rango");
            int label = data[p] & 0xFF;

            if ((label & 0xC0) == 0xC0) {
                // Puntero de compresion: 2 bytes, offset en los 14 bits bajos
                if (p + 1 >= len) throw new Exception("Puntero de compresion truncado");
                int pointer = ((label & 0x3F) << 8) | (data[p + 1] & 0xFF);
                if (!jumped) nextOffset = p + 2; // el nombre "real" termina aqui
                p = pointer;
                jumped = true;
                if (++safety > 128) throw new Exception("Demasiados saltos de puntero");
                continue;
            }

            if (label == 0) {
                // Fin del nombre
                if (!jumped) nextOffset = p + 1;
                break;
            }

            // Label normal: 'label' bytes de texto
            p++;
            if (p + label > len) throw new Exception("Label fuera de rango");
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < label; i++) {
                sb.append((char) (data[p + i] & 0xFF));
            }
            p += label;
        }

        outNextOffset[0] = nextOffset;
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // CONSTRUCCION DE RESPUESTAS
    // -------------------------------------------------------------------------

    /**
     * Construye una respuesta DNS completa a partir de la consulta original.
     *
     * @param query        paquete original (para copiar la Question tal cual)
     * @param q            Question parseada
     * @param answers      registros de respuesta (puede ir vacio)
     * @param authoritative true si respondemos desde nuestra zona local (AA=1)
     * @param rcode        codigo de respuesta (RCODE_OK, RCODE_NXDOMAIN, ...)
     */
    public static byte[] buildResponse(byte[] query, Query q, List<Answer> answers,
                                       boolean authoritative, int rcode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // --- Cabecera ---
        putU16(out, q.id); // mismo ID de la consulta

        // FLAGS byte 1: QR=1, Opcode=0, AA, TC=0, RD
        int f1 = 0x80;                 // QR = respuesta
        if (authoritative) f1 |= 0x04; // AA
        if (q.rd)          f1 |= 0x01; // RD (se refleja)
        // FLAGS byte 2: RA=1 (soportamos recursion), Z=0, RCODE
        int f2 = 0x80 | (rcode & 0x0F);
        out.write(f1);
        out.write(f2);

        putU16(out, 1);                // QDCOUNT = 1
        putU16(out, answers.size());   // ANCOUNT
        putU16(out, 0);                // NSCOUNT
        putU16(out, 0);                // ARCOUNT

        // --- Question (se copia byte a byte desde la consulta original) ---
        out.write(query, q.questionStart, q.questionEnd - q.questionStart);

        // --- Answers ---
        for (Answer a : answers) {
            // NAME: puntero de compresion al inicio de la Question (offset 12)
            out.write(0xC0);
            out.write(0x0C);
            putU16(out, a.type);       // TYPE
            putU16(out, CLASS_IN);     // CLASS = IN
            putU32(out, a.ttl);        // TTL
            putU16(out, a.rdata.length); // RDLENGTH
            out.write(a.rdata, 0, a.rdata.length); // RDATA
        }

        return out.toByteArray();
    }

    /**
     * Codifica un nombre de dominio a formato DNS (labels con longitud + 0 final).
     * Ej: "ns.example.local" -> [2]ns[7]example[5]local[0]
     */
    public static byte[] encodeName(String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (name != null && !name.isEmpty()) {
            for (String label : name.split("\\.")) {
                byte[] b = label.getBytes();
                out.write(b.length & 0xFF);
                out.write(b, 0, b.length);
            }
        }
        out.write(0); // terminador
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Helpers de lectura/escritura de enteros sin signo (big-endian)
    // -------------------------------------------------------------------------

    public static int u16(byte[] data, int off) {
        return ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
    }

    private static void putU16(ByteArrayOutputStream out, int v) {
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void putU32(ByteArrayOutputStream out, long v) {
        out.write((int) ((v >> 24) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) (v & 0xFF));
    }

    /** Nombre legible de un tipo de registro, para los logs. */
    public static String typeName(int type) {
        switch (type) {
            case TYPE_A:     return "A";
            case TYPE_NS:    return "NS";
            case TYPE_CNAME: return "CNAME";
            case TYPE_SOA:   return "SOA";
            case TYPE_PTR:   return "PTR";
            case TYPE_AAAA:  return "AAAA";
            default:         return "TYPE" + type;
        }
    }

    /** Utilidad para dividir en una lista (evita imports en el resolver). */
    public static List<Answer> newAnswerList() {
        return new ArrayList<>();
    }
}
