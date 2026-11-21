package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiIcons;

import java.util.HashMap;
import java.util.Map;

/**
 * Ability glyphs: the adaptation's icon on a faint paper roundel inside a thin ring edged in ink, one family for every
 * kind. A cooldown shades the icon with a clock wipe and pulses once when ready; charges split the ring into one segment
 * per charge or minion, each lighting as it fills and the whole ring glowing at full; a running effect drains the ring;
 * a toggle is lit or dim. The ring takes the skill's colour; a skill folded into one glyph shows its count on a paper
 * tag. {@link GlyphLayout} decides where glyphs go; they ease to new places rather than jump. No text unless the player
 * turns names on; the editor names a glyph under the pointer.
 */
final class AbilityWidget implements HudWidget {
    static final int RING_PAD = 3;
    static final double ICON_SCALE = 0.9;
    private static final int THICK_RING_ICON = 14;
    private static final int COUNT_BACKING = 0xE6F6EDD8;
    private static final int ROUNDEL = 0x40F6EDD8;
    private static final int GUARD = 0x602E2216;
    static final int MAX_SEGMENTS = 12;
    static final long POP_MILLIS = 220L;
    static final long FLASH_MILLIS = 320L;
    static final long FULL_GLOW_MILLIS = 700L;
    static final int DEFAULT_ACCENT = 0x7FD8FF;
    private static final int TRACK = 0x902E2216;
    private static final int SHADE = 0x9C2E2216;
    private static final int DIM = 0x962E2216;
    private static final int LOSS = 0xFFFF5A4A;
    private static final float OVERLAY_DEPTH = 200.0F;

    private static final int SPAN_CAPACITY = MAX_SEGMENTS * 2 + 2;

    private final double[] spanFrom = new double[SPAN_CAPACITY];
    private final double[] spanTo = new double[SPAN_CAPACITY];
    private final int[] spanColor = new int[SPAN_CAPACITY];
    private int spans;
    private static final String[] NUMBERS = new String[200];
    private static final String[] PLUS = new String[200];
    private static final double REFLOW_TAU = 70.0;

    static {
        for (int index = 0; index < NUMBERS.length; index++) {
            NUMBERS[index] = Integer.toString(index);
            PLUS[index] = "+" + index;
        }
    }

    private final Map<String, GlyphLayout> layouts = new HashMap<>();

    /** The icon size a glyph draws for a configured {@code iconSize}: nine tenths of it, in whole units. */
    static int iconSize(int configured) {
        return (int) Math.round(configured * ICON_SCALE);
    }

    /** Outer size of a glyph whose drawn icon is {@code iconSize}. */
    static int glyphSize(int iconSize) {
        return iconSize + RING_PAD * 2;
    }

    @Override
    public void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out) {
        GlyphLayout layout = layout(config);
        layout.update(draw, frame, config, (HudOptions.Abilities) config.options(), names(frame, (HudOptions.Abilities) config.options()));
        if (layout.cells() == 0) {
            out.set(0, 0);
            return;
        }
        out.set((int) Math.ceil(layout.width), (int) Math.ceil(layout.height));
    }

    @Override
    public boolean placesItself(Frame frame, HudWidgetConfig config, HudBox out) {
        GlyphLayout layout = layout(config);
        if (!layout.arc) {
            return false;
        }
        double scale = frame.unitScale();
        out.set(frame.screenWidth() / 2.0 + layout.originX * scale, frame.screenHeight() / 2.0 + layout.originY * scale,
                layout.width * scale, layout.height * scale, scale);
        return true;
    }

    @Override
    public void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size) {
        GlyphLayout layout = layout(config);
        int cells = layout.cells();
        String owner = config.id();
        long now = frame.now();
        boolean rows = layout.arc || ((HudOptions.Abilities) config.options()).arrangement().rows();
        for (int index = 0; index < cells; index++) {
            double targetX = layout.cellX[index];
            double targetY = layout.cellY[index];
            int offset = (int) Math.round((layout.cellWidth - layout.glyph) / 2.0);
            if (index >= layout.shown.size()) {
                more(draw, frame, layout.hidden, (int) Math.round(targetX) + (rows && layout.names ? offset : 0),
                        (int) Math.round(targetY), layout.glyph);
                continue;
            }
            HudStatusEntry status = layout.shown.get(index);
            if (!owner.equals(status.animOwner) || frame.reducedMotion()) {
                status.animOwner = owner;
                status.animX = targetX;
                status.animY = targetY;
            } else {
                long elapsed = Math.max(0L, now - status.animAt);
                status.animX = HudMotion.approach(status.animX, targetX, elapsed, REFLOW_TAU);
                status.animY = HudMotion.approach(status.animY, targetY, elapsed, REFLOW_TAU);
            }
            status.animAt = now;
            int x = (int) Math.round(status.animX);
            int y = (int) Math.round(status.animY);
            int glyphX = layout.names && rows ? x + offset : x;
            glyph(draw, frame, status, layout.iconSize, glyphX, y, layout.groupCount[index]);
            if (layout.names) {
                String label = status.label();
                int nameX = rows ? x + (int) Math.round((layout.cellWidth - draw.textWidth(label)) / 2.0) : x + layout.glyph + 3;
                int nameY = rows ? y + layout.glyph + 2 : y + (layout.glyph - draw.lineHeight()) / 2 + 1;
                HudPaint.text(draw, label, nameX, nameY, HudPaint.TEXT, frame.opacity());
            }
        }
    }

    /** The current layout of the widget instance {@code config}, created on first use. */
    GlyphLayout layout(HudWidgetConfig config) {
        GlyphLayout layout = layouts.get(config.id());
        if (layout == null) {
            layout = new GlyphLayout();
            layouts.put(config.id(), layout);
        }
        return layout;
    }

    private static boolean names(Frame frame, HudOptions.Abilities options) {
        return options.showNames();
    }

    private void more(UiDraw draw, Frame frame, int hidden, int x, int y, int glyph) {
        double opacity = frame.opacity();
        begin();
        span(0.0, 1.0, HudPaint.fade(TRACK, opacity));
        ring(draw, frame, x, y, glyph, 1.0);
        String text = plus(hidden);
        int width = draw.textWidth(text);
        double scale = fitScale(width, moreRoom(glyph), frame.pixelScale());
        draw.pushPose();
        draw.translate((float) (x + (glyph - width * scale) / 2.0 + scale), (float) (y + (glyph - draw.lineHeight() * scale) / 2.0 + scale));
        draw.scale((float) scale, (float) scale);
        HudPaint.text(draw, text, 0, 0, HudPaint.TEXT_DIM, opacity);
        draw.popPose();
    }

    /** Room for the "+n" text inside a glyph's ring. */
    static double moreRoom(int glyph) {
        return glyph - 4.0;
    }

    /** Room for a count in a glyph's bottom right corner, so most of the icon stays in view. */
    static double countRoom(int glyph) {
        return glyph * 0.6;
    }

    /** The scale of a count: fits {@link #countRoom} across and half the glyph down. */
    static double countScale(int textWidth, int lineHeight, int glyph, double pixelScale) {
        return Math.min(fitScale(textWidth, countRoom(glyph), pixelScale), fitScale(lineHeight, glyph * 0.5, pixelScale));
    }

    /**
     * The largest scale up to 1 at which text {@code width} wide fits {@code room}, rounded down to whole screen pixels
     * so it stays sharp, and never under half.
     */
    static double fitScale(int width, double room, double pixelScale) {
        if (width <= room) {
            return 1.0;
        }
        double pixels = Math.max(1.0, pixelScale);
        return Math.max(0.5, Math.floor(room / width * pixels) / pixels);
    }

    private void glyph(UiDraw draw, Frame frame, HudStatusEntry status, int iconSize, int x, int y, int group) {
        long now = frame.now();
        double opacity = frame.opacity();
        int size = glyphSize(iconSize);
        double thickness = iconSize >= THICK_RING_ICON ? 2.0 : 1.5;
        double scale = frame.reducedMotion() ? 1.0 : HudMotion.easeOutBack(HudMotion.progress(now - status.shownAt(), POP_MILLIS));
        double ready = status.readyProgress(now);
        if (ready > 0.0 && !frame.reducedMotion()) {
            scale *= 1.0 + 0.16 * Math.sin(Math.PI * ready);
        }
        boolean scaled = Math.abs(scale - 1.0) > 0.001;
        if (scaled) {
            float center = size / 2.0F;
            draw.pushPose();
            draw.translate(x + center, y + center);
            draw.scale((float) scale, (float) scale);
            draw.translate(-(x + center), -(y + center));
        }
        int accent = accent(frame.model(), status.skillId());
        int iconX = x + RING_PAD;
        int iconY = y + RING_PAD;
        int overlay = 0;
        double shade = 0.0;
        double halo = 0.0;
        begin();
        switch (status.glyph()) {
            case COOLDOWN -> {
                if (ready > 0.0) {
                    int flash = HudPaint.mix(HudPaint.opaque(accent), 0xFFFFFFFF, 0.6 * (1.0 - ready));
                    span(0.0, 1.0, HudPaint.fade(flash, opacity * (1.0 - ready)));
                    halo = 1.0 - ready;
                } else {
                    double left = status.fractionLeft(now);
                    span(0.0, 1.0 - left, HudPaint.fade(TRACK, opacity));
                    span(1.0 - left, 1.0, HudPaint.fade(0xC0000000 | accent, opacity));
                    shade = left;
                }
            }
            case DURATION -> {
                double left = status.fractionLeft(now);
                span(0.0, 1.0 - left, HudPaint.fade(TRACK, opacity));
                span(1.0 - left, 1.0, HudPaint.fade(HudPaint.opaque(accent), opacity));
            }
            case TOGGLE -> {
                if (status.lit()) {
                    span(0.0, 1.0, HudPaint.fade(HudPaint.opaque(accent), opacity));
                } else {
                    span(0.0, 1.0, HudPaint.fade(TRACK, opacity));
                    overlay = DIM;
                }
            }
            case CHARGES -> {
                halo = charges(frame, status, accent);
                if (status.stacks() == 0) {
                    overlay = DIM;
                }
            }
        }
        ring(draw, frame, x, y, size, thickness);
        begin();
        span(0.0, 1.0, HudPaint.fade(GUARD, opacity));
        ring(draw, frame, x - 1, y - 1, size + 2, 1.0);
        wipe(draw, frame, x, y, size, thickness, 1.0, HudPaint.fade(ROUNDEL, opacity));
        if (halo > 0.02) {
            begin();
            span(0.0, 1.0, HudPaint.fade(HudPaint.opaque(accent), opacity * halo));
            ring(draw, frame, x - 2, y - 2, size + 4, 1.0);
        }
        HudPaint.icon(draw, UiIcons.status(status.purpose(), status.icon()), iconX, iconY, iconSize, opacity);
        boolean count = group > 1 || countShown(status);
        if (shade > 0.0 || overlay != 0 || count) {
            draw.pushPose();
            draw.depth(OVERLAY_DEPTH);
            if (shade > 0.0) {
                wipe(draw, frame, x, y, size, thickness + 0.5, shade, HudPaint.fade(SHADE, opacity));
            }
            if (overlay != 0) {
                wipe(draw, frame, x, y, size, thickness + 0.5, 1.0, HudPaint.fade(overlay, opacity));
            }
            if (count) {
                String text = group > 1 ? number(group) : status.countText();
                int width = draw.textWidth(text);
                double textScale = countScale(width, draw.lineHeight(), size, frame.pixelScale());
                draw.pushPose();
                draw.translate((float) (x + size - width * textScale), (float) (y + size - (draw.lineHeight() - 1) * textScale));
                draw.scale((float) textScale, (float) textScale);
                draw.fill(-1, -1, width, draw.lineHeight() - 1, HudPaint.fade(COUNT_BACKING, opacity));
                HudPaint.ink(draw, text, 0, 0, HudPaint.INK, opacity);
                draw.popPose();
            }
            draw.popPose();
        }
        if (scaled) {
            draw.popPose();
        }
    }

    /**
     * One segment per charge with a small gap, or a single continuous ring when there are too many to tell apart.
     * Returns the strength of the glow around a full glyph.
     */
    private double charges(Frame frame, HudStatusEntry status, int accent) {
        long now = frame.now();
        double opacity = frame.opacity();
        int max = status.maxStacks();
        int stacks = status.stacks();
        boolean full = status.full();
        int lit = HudPaint.opaque(full ? HudPaint.mix(HudPaint.opaque(accent), 0xFFFFFFFF, 0.3) : accent);
        if (max <= 0 || max > MAX_SEGMENTS) {
            double fraction = max <= 0 ? (stacks > 0 ? 1.0 : 0.0) : (double) stacks / max;
            span(0.0, fraction, HudPaint.fade(lit, opacity));
            span(fraction, 1.0, HudPaint.fade(TRACK, opacity));
        } else {
            double segment = 1.0 / max;
            double gap = max == 1 ? 0.0 : Math.min(0.04, segment * 0.25);
            double flash = frame.reducedMotion() || status.stacksChangedAt() == Long.MIN_VALUE ? 0.0
                    : 1.0 - HudMotion.progress(now - status.stacksChangedAt(), FLASH_MILLIS);
            boolean gained = stacks > status.previousStacks();
            int changed = gained ? stacks - 1 : stacks;
            boolean recharging = !status.openEnded() && status.remaining(now) > 0L;
            for (int index = 0; index < max; index++) {
                double from = index * segment + gap / 2.0;
                double to = (index + 1) * segment - gap / 2.0;
                int color = index < stacks ? lit : TRACK;
                if (flash > 0.0 && index == changed) {
                    color = gained ? HudPaint.mix(lit, 0xFFFFFFFF, flash) : HudPaint.mix(TRACK, LOSS, flash);
                }
                if (index == stacks && recharging) {
                    double filled = from + (to - from) * (1.0 - status.fractionLeft(now));
                    span(from, filled, HudPaint.fade(0x78000000 | accent, opacity));
                    span(filled, to, HudPaint.fade(color, opacity));
                } else {
                    span(from, to, HudPaint.fade(color, opacity));
                }
            }
        }
        if (!full) {
            return 0.0;
        }
        double glow = 0.3;
        if (status.fullAt() != Long.MIN_VALUE && !frame.reducedMotion()) {
            glow += 0.6 * (1.0 - HudMotion.progress(now - status.fullAt(), FULL_GLOW_MILLIS));
        }
        return glow;
    }

    private void begin() {
        spans = 0;
    }

    private void span(double from, double to, int argb) {
        if (spans < SPAN_CAPACITY && to > from) {
            spanFrom[spans] = from;
            spanTo[spans] = to;
            spanColor[spans] = argb;
            spans++;
        }
    }

    /** Draws the collected spans as a ring, in physical pixels so the curve stays smooth at any GUI scale. */
    private void ring(UiDraw draw, Frame frame, int x, int y, int size, double thickness) {
        double pixels = frame.pixelScale();
        if (pixels <= 1.01) {
            HudPaint.ring(draw, x, y, size, thickness, spanFrom, spanTo, spanColor, spans);
            return;
        }
        draw.pushPose();
        draw.translate(x, y);
        draw.scale((float) (1.0 / pixels), (float) (1.0 / pixels));
        HudPaint.ring(draw, 0, 0, (int) Math.round(size * pixels), thickness * pixels, spanFrom, spanTo, spanColor, spans);
        draw.popPose();
    }

    private static void wipe(UiDraw draw, Frame frame, int x, int y, int size, double inset, double remaining, int argb) {
        double pixels = frame.pixelScale();
        if (pixels <= 1.01) {
            HudPaint.wipe(draw, x, y, size, inset, remaining, argb);
            return;
        }
        draw.pushPose();
        draw.translate(x, y);
        draw.scale((float) (1.0 / pixels), (float) (1.0 / pixels));
        HudPaint.wipe(draw, 0, 0, (int) Math.round(size * pixels), inset * pixels, remaining, argb);
        draw.popPose();
    }

    private static boolean countShown(HudStatusEntry status) {
        if (status.glyph() == HudGlyph.CHARGES) {
            return status.maxStacks() <= 0 && status.stacks() > 1;
        }
        return status.glyph() == HudGlyph.DURATION && status.stacks() > 1;
    }

    private static String number(int value) {
        return value >= 0 && value < NUMBERS.length ? NUMBERS[value] : Integer.toString(value);
    }

    private static String plus(int value) {
        return value >= 0 && value < PLUS.length ? PLUS[value] : "+" + value;
    }

    /** The skill's colour lifted to read on any background, or a neutral cyan for glyphs without a known skill. */
    static int accent(HudModel model, String skillId) {
        if (skillId.isEmpty() || model.skill(skillId) == null) {
            return DEFAULT_ACCENT;
        }
        return HudPaint.readable(HudPaint.mix(HudPaint.opaque(model.skillColor(skillId)), 0xFFFFFFFF, 0.15)) & 0xFFFFFF;
    }
}
