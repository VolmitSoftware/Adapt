package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lays out and draws a profile's widgets. Keeps the rectangles of the last frame for the editor and for tests. */
public final class HudRenderer {
    static final double GHOST_OPACITY = 0.35;
    static final int PLACEHOLDER_WIDTH = 64;
    static final int PLACEHOLDER_HEIGHT = 16;
    /** The crosshair stays clear even with the protected zone turned off. */
    static final double CROSSHAIR = 16.0;
    static final int ZONE_FILL = 0x26FF7A6E;
    static final int ZONE_EDGE = 0xA0FF9A8E;
    private static final HudOptions.Skills DEFAULT_SKILLS = (HudOptions.Skills) HudDefaults.options(HudWidgetKind.SKILLS);
    private static final HudOptions.Toasts DEFAULT_TOASTS = (HudOptions.Toasts) HudDefaults.options(HudWidgetKind.TOASTS);

    private final HudModel model;
    private final HudText text;
    private final Map<HudWidgetKind, HudWidget> widgets = new EnumMap<>(HudWidgetKind.class);
    private final HudWidget.Frame frame = new HudWidget.Frame();
    private final HudWidget.Size size = new HudWidget.Size();
    private final HudFit fit = new HudFit();
    private final HudCompact compact = new HudCompact();
    private final HudBox preferred = new HudBox();
    private final List<Placed> placed = new ArrayList<>();
    private int placedCount;
    private int screenWidth;
    private int screenHeight;

    public HudRenderer(HudModel model, HudText text) {
        this.model = Objects.requireNonNull(model, "model");
        this.text = Objects.requireNonNull(text, "text");
        for (HudWidgetKind kind : HudWidgetKind.values()) {
            widgets.put(kind, HudWidget.of(kind));
        }
    }

    /** A widget as laid out in the last frame. */
    public static final class Placed {
        final HudBox box = new HudBox();
        HudWidgetConfig config;
        boolean drawn;
        boolean placeholder;
        boolean compact;
        boolean crowded;
        final double[] cells = new double[(HudOptions.Abilities.MAX_SHOWN + 1) * 4];
        int cellCount;

        public HudWidgetConfig config() {
            return config;
        }

        public HudBox box() {
            return box;
        }

        /** Whether anything was drawn (visible, with content or shown by the editor). */
        public boolean drawn() {
            return drawn;
        }

        /** Whether auto-fit drew the widget in its one-line pill form, placed in free space. */
        public boolean compact() {
            return compact;
        }

        /** Whether a pill found no free space and was drawn over something else rather than dropped. */
        public boolean crowded() {
            return crowded;
        }

        /** Glyph cells drawn by a glyph widget in the last frame. */
        public int cellCount() {
            return cellCount;
        }

        /** Cell {@code index} as x, y, width, height in scaled units. */
        public HudRect cell(int index) {
            return new HudRect(cells[index * 4], cells[index * 4 + 1], cells[index * 4 + 2], cells[index * 4 + 3]);
        }

        /** Whether the editor drew a stand-in because the widget had nothing to show. */
        public boolean placeholder() {
            return placeholder;
        }
    }

    public HudModel model() {
        return model;
    }

    /** Draws the in-game HUD: visible widgets with content only. Nothing at all while the model is inactive. */
    public void render(UiDraw draw, HudLayout layout) {
        if (!model.active()) {
            clear();
            return;
        }
        draw(draw, layout, false);
    }

    /** Forgets the last frame's layout, for a frame where the HUD is not drawn. */
    public void clear() {
        placedCount = 0;
    }

    /**
     * Draws every widget for the editor; hidden ones (by the user or by auto-fit) faint, empty ones as a labelled
     * stand-in; and the protected view zone as a tinted box.
     */
    public void renderEditing(UiDraw draw, HudLayout layout) {
        draw(draw, layout, true);
        HudBox zone = frame.zone;
        if (layout.activeProfile().protect() <= 0.0) {
            return;
        }
        int left = (int) Math.round(zone.x);
        int top = (int) Math.round(zone.y);
        int right = (int) Math.round(zone.x + zone.width);
        int bottom = (int) Math.round(zone.y + zone.height);
        draw.fill(left, top, right, bottom, ZONE_FILL);
        draw.outline(left, top, right - left, bottom - top, ZONE_EDGE);
        String label = HudPaint.fit(draw, text.get("adapt.hud.editor.protected"), right - left - 4);
        HudPaint.text(draw, label, (left + right - draw.textWidth(label)) / 2, top + 3, ZONE_EDGE, 1.0);
    }

    public int placedCount() {
        return placedCount;
    }

    /**
     * Writes the boxes of the widgets drawn in the last frame as x, y, width, height into {@code out}, as many as fit,
     * and returns how many it wrote. Glyph widgets give their cells rather than their whole box, after every other box.
     */
    public int occupied(double[] out) {
        int written = 0;
        int capacity = out.length / 4;
        for (int index = 0; index < placedCount && written < capacity; index++) {
            Placed entry = placed.get(index);
            if (entry.drawn && entry.cellCount == 0) {
                out[written * 4] = entry.box.x;
                out[written * 4 + 1] = entry.box.y;
                out[written * 4 + 2] = entry.box.width;
                out[written * 4 + 3] = entry.box.height;
                written++;
            }
        }
        for (int index = 0; index < placedCount && written < capacity; index++) {
            Placed entry = placed.get(index);
            for (int cell = 0; entry.drawn && cell < entry.cellCount && written < capacity; cell++) {
                System.arraycopy(entry.cells, cell * 4, out, written * 4, 4);
                written++;
            }
        }
        return written;
    }

    public Placed placed(int index) {
        return placed.get(index);
    }

    public Placed placed(String id) {
        for (int index = 0; index < placedCount; index++) {
            if (placed.get(index).config.id().equals(id)) {
                return placed.get(index);
            }
        }
        return null;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    private void draw(UiDraw draw, HudLayout layout, boolean editing) {
        HudProfile profile = layout.activeProfile();
        long now = model.clock().millis();
        screenWidth = draw.width();
        screenHeight = draw.height();
        fit.update(profile.autoFit(), screenWidth / profile.scale(), screenHeight / profile.scale());
        configure(profile, layout.reducedMotion());
        model.expire(now);
        frame.set(model, text, now, layout.reducedMotion(), fit);
        frame.screenWidth = screenWidth;
        frame.screenHeight = screenHeight;
        double zoneWidth = Math.max(CROSSHAIR, Math.round(screenWidth * profile.protect()));
        double zoneHeight = Math.max(CROSSHAIR, Math.round(screenHeight * profile.protect()));
        frame.zone.set(Math.round((screenWidth - zoneWidth) / 2.0), Math.round((screenHeight - zoneHeight) / 2.0), zoneWidth,
                zoneHeight, 1.0);
        double guiScale = draw.guiScale();
        List<HudWidgetConfig> configs = profile.widgets();
        placedCount = 0;
        for (int index = 0; index < configs.size(); index++) {
            HudWidgetConfig config = configs.get(index);
            Placed entry = next();
            entry.config = config;
            entry.drawn = false;
            entry.placeholder = false;
            entry.crowded = false;
            entry.compact = fit.compact() && pill(config.kind());
            entry.cellCount = 0;
            HudPlacement placement = config.placement();
            boolean shown = placement.visible() && fit.shows(config.kind());
            if (!shown && !editing || entry.compact) {
                entry.box.set(0.0, 0.0, 0.0, 0.0, 0.0);
                continue;
            }
            HudWidget widget = widgets.get(config.kind());
            frame.anchor = placement.anchor();
            frame.opacity = placement.opacity() * (shown ? 1.0 : GHOST_OPACITY);
            frame.unitScale = placement.scale() * profile.scale();
            widget.measure(draw, frame, config, size);
            boolean empty = size.empty();
            if (empty && !editing) {
                HudGeometry.place(placement, 0.0, 0.0, profile.scale(), screenWidth, screenHeight, guiScale, entry.box);
                continue;
            }
            int contentWidth = empty ? PLACEHOLDER_WIDTH : size.width;
            int contentHeight = empty ? PLACEHOLDER_HEIGHT : size.height;
            if (empty || !widget.placesItself(frame, config, entry.box)) {
                HudGeometry.place(placement, contentWidth, contentHeight, profile.scale(), screenWidth, screenHeight, guiScale, entry.box);
                protect(entry.box);
            }
            entry.drawn = true;
            entry.placeholder = empty;
            draw.pushPose();
            draw.translate((float) entry.box.x, (float) entry.box.y);
            draw.scale((float) entry.box.scale, (float) entry.box.scale);
            frame.pixelScale = Math.max(1.0, guiScale * entry.box.scale);
            if (empty) {
                placeholder(draw, config);
            } else {
                widget.render(draw, frame, config, size);
                if (widget instanceof AbilityWidget glyphs) {
                    recordCells(entry, glyphs.layout(config));
                }
            }
            draw.popPose();
        }
        if (fit.compact()) {
            drawPills(draw, profile, editing, guiScale, HudWidgetKind.TOASTS);
            drawPills(draw, profile, editing, guiScale, HudWidgetKind.NOTICES);
        }
    }

    /**
     * Moves a box that touches the protected view zone out of it, away from the crosshair along the axis it is
     * further out on, so a widget that grows toward the centre ends up growing away from it instead.
     */
    private void protect(HudBox box) {
        HudBox zone = frame.zone;
        if (zone.width <= 0.0 || zone.height <= 0.0
                || !HudVanilla.hit(box.x, box.y, box.width, box.height, zone.x, zone.y, zone.width, zone.height)) {
            return;
        }
        double centreX = box.x + box.width / 2.0 - screenWidth / 2.0;
        double centreY = box.y + box.height / 2.0 - screenHeight / 2.0;
        double outX = Math.abs(centreX) / (zone.width / 2.0 + box.width / 2.0);
        double outY = Math.abs(centreY) / (zone.height / 2.0 + box.height / 2.0);
        if (outY >= outX) {
            box.y = centreY >= 0.0 ? zone.y + zone.height : zone.y - box.height;
        } else {
            box.x = centreX >= 0.0 ? zone.x + zone.width : zone.x - box.width;
        }
        box.x = Math.max(0.0, Math.min(screenWidth - box.width, box.x));
        box.y = Math.max(0.0, Math.min(screenHeight - box.height, box.y));
    }

    private static void recordCells(Placed entry, GlyphLayout layout) {
        int cells = Math.min(layout.cells(), entry.cells.length / 4);
        double scale = entry.box.scale;
        for (int index = 0; index < cells; index++) {
            entry.cells[index * 4] = entry.box.x + layout.cellX[index] * scale;
            entry.cells[index * 4 + 1] = entry.box.y + layout.cellY[index] * scale;
            entry.cells[index * 4 + 2] = layout.cellWidth * scale;
            entry.cells[index * 4 + 3] = layout.cellHeight * scale;
        }
        entry.cellCount = cells;
    }

    /** The layout the glyph widget {@code id} drew with in the last frame. */
    GlyphLayout glyphLayout(String id) {
        return ((AbilityWidget) widgets.get(HudWidgetKind.ABILITIES)).layout(placed(id).config());
    }

    /** The protected view zone of the last frame, in scaled units. */
    public HudBox zone() {
        return frame.zone;
    }

    private static boolean pill(HudWidgetKind kind) {
        return kind == HudWidgetKind.TOASTS || kind == HudWidgetKind.NOTICES;
    }

    /** Pills go last, toasts before notices, so they fit around everything else and a level-up gets the best spot. */
    private void drawPills(UiDraw draw, HudProfile profile, boolean editing, double guiScale, HudWidgetKind kind) {
        for (int index = 0; index < placedCount; index++) {
            Placed entry = placed.get(index);
            HudWidgetConfig config = entry.config;
            if (!entry.compact || config.kind() != kind || !config.placement().visible() && !editing) {
                continue;
            }
            HudPlacement placement = config.placement();
            frame.anchor = placement.anchor();
            frame.opacity = placement.opacity() * (placement.visible() ? 1.0 : GHOST_OPACITY);
            HudWidget widget = widgets.get(kind);
            widget.measure(draw, frame, config, size);
            boolean empty = size.empty();
            if (empty && !editing) {
                continue;
            }
            int contentWidth = empty ? PLACEHOLDER_WIDTH : size.width;
            int contentHeight = empty ? PLACEHOLDER_HEIGHT : size.height;
            HudGeometry.place(placement, contentWidth, contentHeight, profile.scale(), screenWidth, screenHeight, guiScale, preferred);
            entry.crowded = !compact.place(preferred, preferred.width, preferred.height, preferred.scale, screenWidth, screenHeight,
                    frame.zone, placed, placedCount, entry, entry.box);
            int fittedWidth = (int) Math.floor(entry.box.width / entry.box.scale + 1e-6);
            entry.box.width = fittedWidth * entry.box.scale;
            size.set(fittedWidth, contentHeight);
            entry.drawn = true;
            entry.placeholder = empty;
            draw.pushPose();
            draw.translate((float) entry.box.x, (float) entry.box.y);
            draw.scale((float) entry.box.scale, (float) entry.box.scale);
            frame.pixelScale = Math.max(1.0, guiScale * entry.box.scale);
            if (empty) {
                placeholder(draw, config);
            } else {
                widget.render(draw, frame, config, size);
            }
            draw.popPose();
        }
    }

    private void placeholder(UiDraw draw, HudWidgetConfig config) {
        double opacity = frame.opacity;
        HudPaint.panel(draw, 0, 0, PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT, opacity);
        String label = HudPaint.fit(draw, text.get(config.kind().translationKey()), PLACEHOLDER_WIDTH - 8);
        HudPaint.text(draw, label, (PLACEHOLDER_WIDTH - draw.textWidth(label)) / 2, 4, HudPaint.TEXT_DIM, opacity);
    }

    private void configure(HudProfile profile, boolean reducedMotion) {
        HudOptions.Skills skills = null;
        HudOptions.Toasts toasts = null;
        List<HudWidgetConfig> configs = profile.widgets();
        for (int index = 0; index < configs.size(); index++) {
            HudWidgetConfig config = configs.get(index);
            if (skills == null && config.options() instanceof HudOptions.Skills options && config.placement().visible()) {
                skills = options;
            }
            if (toasts == null && config.options() instanceof HudOptions.Toasts options && config.placement().visible()) {
                toasts = options;
            }
        }
        model.xpWindow(skills == null ? DEFAULT_SKILLS.lingerMillis() : skills.lingerMillis());
        HudOptions.Toasts toastOptions = toasts == null ? DEFAULT_TOASTS : toasts;
        model.toasts().configure(toastOptions.durationMillis(), fit.toasts(toastOptions.maxVisible()), reducedMotion);
    }

    private Placed next() {
        if (placedCount == placed.size()) {
            placed.add(new Placed());
        }
        return placed.get(placedCount++);
    }
}
