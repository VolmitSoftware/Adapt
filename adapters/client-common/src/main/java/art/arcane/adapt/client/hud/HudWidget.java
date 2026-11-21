package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;

/**
 * Draws one kind of widget in its own unscaled units; the renderer translates and scales. Measuring and drawing must
 * not allocate once the model stops changing.
 */
public interface HudWidget {
    /** Natural size of the content right now; zero when there is nothing to show. */
    void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out);

    void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size);

    /**
     * For widgets that position themselves on screen instead of by anchor, such as glyphs arranged around the
     * crosshair: writes the box for the size just measured and returns true. Called after {@link #measure}.
     */
    default boolean placesItself(Frame frame, HudWidgetConfig config, HudBox out) {
        return false;
    }

    static HudWidget of(HudWidgetKind kind) {
        return switch (kind) {
            case SKILLS -> new SkillTrackerWidget();
            case RESOURCES -> new ResourcesWidget();
            case ABILITIES -> new AbilityWidget();
            case NOTICES -> new NoticeWidget();
            case TOASTS -> new ToastWidget();
        };
    }

    final class Size {
        public int width;
        public int height;

        public void set(int newWidth, int newHeight) {
            width = Math.max(0, newWidth);
            height = Math.max(0, newHeight);
        }

        public boolean empty() {
            return width <= 0 || height <= 0;
        }
    }

    /** Per-frame context, reused. */
    final class Frame {
        HudModel model;
        HudText text;
        long now;
        double opacity;
        boolean reducedMotion;
        HudAnchor anchor;
        HudFit fit;
        double pixelScale = 1.0;
        double unitScale = 1.0;
        double screenWidth;
        double screenHeight;
        final HudBox zone = new HudBox();

        public HudModel model() {
            return model;
        }

        public HudText text() {
            return text;
        }

        public long now() {
            return now;
        }

        public double opacity() {
            return opacity;
        }

        public boolean reducedMotion() {
            return reducedMotion;
        }

        /** Physical pixels per unit of the widget being drawn, for shapes that should stay smooth at any GUI scale. */
        public double pixelScale() {
            return pixelScale;
        }

        /** Scaled GUI units per unit of the widget being drawn (its own scale times the profile scale). */
        public double unitScale() {
            return unitScale;
        }

        public double screenWidth() {
            return screenWidth;
        }

        public double screenHeight() {
            return screenHeight;
        }

        /** The protected view zone around the crosshair, in scaled GUI units. */
        public HudBox zone() {
            return zone;
        }

        /** What auto-fit allows on this screen. */
        public HudFit fit() {
            return fit;
        }

        /** The widget's anchor, so content can grow away from the edge it is attached to. */
        public HudAnchor anchor() {
            return anchor;
        }

        void set(HudModel newModel, HudText newText, long newNow, boolean newReducedMotion, HudFit newFit) {
            model = newModel;
            fit = newFit;
            text = newText;
            now = newNow;
            reducedMotion = newReducedMotion;
        }
    }
}
