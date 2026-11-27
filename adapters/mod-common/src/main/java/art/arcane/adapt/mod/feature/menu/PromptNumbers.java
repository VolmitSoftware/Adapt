package art.arcane.adapt.mod.feature.menu;

/** Text forms of prompt values: numbers typed or shown as text, and option ids. */
final class PromptNumbers {
    private PromptNumbers() {
    }

    static String format(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0E15 ? Long.toString((long) value) : Double.toString(value);
    }

    /** The number in {@code text} when it parses and lies within the bounds that are set, else null. */
    static Double parse(String text, Double min, Double max) {
        if (text == null) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException invalid) {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || (min != null && value < min) || (max != null && value > max)) {
            return null;
        }
        return value;
    }

    /** The option index an option id names, or -1. */
    static int index(String id, int options) {
        try {
            int index = Integer.parseInt(id.trim());
            return index >= 0 && index < options ? index : -1;
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }
}
