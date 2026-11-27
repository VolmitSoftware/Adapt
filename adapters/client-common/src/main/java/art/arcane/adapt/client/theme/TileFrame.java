package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiRect;

/**
 * One tile's hover frame: the skill's own frame assembling over {@link #ASSEMBLE_MILLIS} while the tile is pointed at
 * or focused, idling once built and leaving its own way over {@link #LEAVE_MILLIS} after. A mutated skill's tile wears
 * the lead's frame on its left half and the partner's on its right, with one hook of each crossing the middle. Still
 * motion shows the built frame at once and holds it.
 */
public final class TileFrame {
    public static final long ASSEMBLE_MILLIS = 1100L;
    public static final long LEAVE_MILLIS = 600L;
    /** How far a frame reaches past its tile: above and below, and at the sides. */
    public static final int REACH_Y = 4;
    public static final int REACH_X = 5;
    /** The most a frame starting from nothing advances in one step, so a tile not drawn for a while starts at its start. */
    private static final long FIRST_STEP_MILLIS = 50L;
    private static final KitFrameCanvas CANVAS = new KitFrameCanvas();

    private double lift;
    private boolean leaving;
    private long last = Long.MIN_VALUE;

    /** How far the frame has come in, 0 to 1. */
    public double lift() {
        return lift;
    }

    /** Whether the frame is drawing, assembling, idling or leaving. */
    public boolean active() {
        return lift > 0.0;
    }

    /**
     * Advances the frame to {@code now} (milliseconds) and draws it round {@code box}. {@code seconds} is the idle clock;
     * {@code still} holds every frame at rest, built when {@code wanted}.
     */
    public void paint(UiDraw draw, PageTheme theme, UiRect box, boolean wanted, long now, double seconds, boolean still) {
        long gap = last == Long.MIN_VALUE ? 0L : Math.max(0L, now - last);
        long dt = Math.min(lift > 0.0 ? LEAVE_MILLIS : FIRST_STEP_MILLIS, gap);
        last = now;
        leaving = !wanted;
        if (still) {
            lift = wanted ? 1.0 : 0.0;
        } else if (wanted) {
            lift = Math.min(1.0, lift + dt / (double) ASSEMBLE_MILLIS);
        } else {
            lift = Math.max(0.0, lift - dt / (double) LEAVE_MILLIS);
        }
        if (lift <= 0.0 || theme == null) {
            return;
        }
        draw(draw, theme, box.x(), box.y(), box.width(), box.height(), lift, seconds, leaving);
    }

    /** The demo's {@code hoverFrame}: the frame of {@code theme} round a tile at {@code lift}, idle clock {@code seconds}. */
    public static void draw(UiDraw draw, PageTheme theme, int x, int y, int width, int height, double lift, double seconds, boolean out) {
        if (lift <= 0.0) {
            return;
        }
        double e = out ? lift : lift >= 1.0 ? 1.0 : 1.0 - Math.pow(1.0 - lift, 1.6);
        int mid = x + (width >> 1);
        ThemeKit lead = theme.lead();
        ThemeKit partner = theme.partner();
        if (partner == null) {
            CANVAS.begin(draw, lead, x, y, width, height, e, seconds, out, Integer.MIN_VALUE, Integer.MAX_VALUE);
            SkillFrames.draw(lead, CANVAS);
            return;
        }
        CANVAS.begin(draw, lead, x, y, width, height, e, seconds, out, Integer.MIN_VALUE, mid - 2);
        SkillFrames.draw(lead, CANVAS);
        CANVAS.begin(draw, partner, x, y, width, height, e, seconds, out, mid + 2, Integer.MAX_VALUE);
        SkillFrames.draw(partner, CANVAS);
        if (e > 0.5 && lead.dress(DressSlot.HOOK).length > 0 && partner.dress(DressSlot.HOOK).length > 0) {
            int alpha = KitFrameCanvas.alphaByte(Math.min(1.0, (e - 0.5) * 4)) << 24 | 0xFFFFFF;
            DressSprite leadHook = lead.dress(DressSlot.HOOK)[0];
            DressSprite partnerHook = partner.dress(DressSlot.HOOK)[0];
            ThemeSprite left = leadHook.frame(0, false);
            ThemeSprite right = partnerHook.frame(0, true);
            draw.blit(lead.texture(), mid - 8, y - Math.min(4, leadHook.height() - 1), left.width(), left.height(), left.u(), left.v(),
                    left.width(), left.height(), alpha);
            draw.blit(partner.texture(), mid + 8 - partnerHook.width(), y + height - 2 - Math.max(0, partnerHook.height() - 6),
                    right.width(), right.height(), right.u(), right.v(), right.width(), right.height(), alpha);
        }
    }
}
