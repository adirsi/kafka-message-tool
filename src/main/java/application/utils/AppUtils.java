package application.utils;

import application.logging.AppLogger;
import application.root.Executable;
import org.apache.kafka.clients.admin.ConfigEntry;

import java.util.Collection;

public class AppUtils {
    public static String realHash(Object o) {
        return String.valueOf(System.identityHashCode(o));
    }

    public static String configEntriesToPrettyString(Collection<ConfigEntry> entries) {
        StringBuilder b = new StringBuilder();
        entries.forEach(entry -> {
            b.append(String.format("%s\n", entry));
        });
        return b.toString();
    }

    public static void runAndSwallowExceptions(Executable executable) {
        try {
            executable.execute();
        } catch (Exception e) {
            AppLogger.warn("Exception happened " + ThrowableUtils.getMessage(e));
        }
    }
}
