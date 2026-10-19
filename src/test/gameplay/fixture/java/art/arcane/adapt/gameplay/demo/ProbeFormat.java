package art.arcane.adapt.gameplay.demo;

import java.util.Locale;
import java.util.Map;

public final class ProbeFormat {
    private static final String HEADER = "ADAPT_QA DEMO PROBE refunds=%d golems=%d";
    private static final String LEVELS = " | levels";
    private static final String GOLEM = " | golem %s %s cores=%s at=%.1f,%.1f,%.1f dormant=%b";

    private ProbeFormat() {
    }

    public static String header(int refunds, int golems) {
        return String.format(Locale.ROOT, HEADER, refunds, golems);
    }

    public static String levels(Map<String, Integer> levels) {
        StringBuilder out = new StringBuilder(LEVELS);
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            if (entry.getValue() > 0) {
                out.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        return out.toString();
    }

    public static String golem(String body, String mode, String cores, double x, double y, double z, boolean dormant) {
        return String.format(Locale.ROOT, GOLEM, body, mode, cores, x, y, z, dormant);
    }
}
