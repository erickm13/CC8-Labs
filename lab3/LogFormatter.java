import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * LogFormatter
 * ------------
 * Formato de log estructurado y legible para el servidor DNS.
 * Salida: [YYYY-MM-DD HH:mm:ss.SSS] [NIVEL] mensaje
 */
public class LogFormatter extends Formatter {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(LocalDateTime.now().format(TS)).append("] ");
        sb.append('[').append(record.getLevel()).append("] ");
        sb.append(formatMessage(record));
        sb.append(System.lineSeparator());

        if (record.getThrown() != null) {
            sb.append("    -> ").append(record.getThrown()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
