package art.arcane.adapt.client.insight;

/** Section {@code insight} of {@code adapt-client.json}. */
public final class InsightSettings {
    public static final String SECTION = "insight";
    public static final double MIN_SCALE = 0.5D;
    public static final double MAX_SCALE = 2.0D;

    private boolean enabled = true;
    private double scale = 0.75D;
    private boolean showStats = true;
    private boolean showEffects = true;
    private boolean showOwner = true;
    private boolean showMarks = true;

    /** False leaves Insight to the server's action bar or Gloss readout. */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean value) {
        enabled = value;
    }

    /** Plate size relative to the GUI scale, snapped to whole pixels when drawn. */
    public double scale() {
        return Double.isFinite(scale) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) : 0.75D;
    }

    public void scale(double value) {
        scale = value;
    }

    public boolean showStats() {
        return showStats;
    }

    public void showStats(boolean value) {
        showStats = value;
    }

    public boolean showEffects() {
        return showEffects;
    }

    public void showEffects(boolean value) {
        showEffects = value;
    }

    public boolean showOwner() {
        return showOwner;
    }

    public void showOwner(boolean value) {
        showOwner = value;
    }

    public boolean showMarks() {
        return showMarks;
    }

    public void showMarks(boolean value) {
        showMarks = value;
    }
}
