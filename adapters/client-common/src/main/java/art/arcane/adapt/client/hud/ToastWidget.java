package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.ThemeLibrary;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.List;

/**
 * Toast cards in the book's dress ({@link ToastLook}): a parchment slip with the skill's corner studs, its icon on a
 * medallion, ink title over muted body and a thin line along the foot that runs down with the card's time. Cards slide
 * in from the edge they are attached to and fade, and slide back out.
 * <p>
 * A card is as wide as its text asks, from {@link #MIN_WIDTH} up to its widest ({@link #widest}): half the screen, at most
 * {@link #MAX_WIDTH} units, and always clear of the screen edges. Text that is still longer wraps: the title to at most
 * {@link #TITLE_LINES} lines, the body to at most {@link #BODY_LINES} (fewer on short screens, see {@link #bodyLines}),
 * and the card grows to fit. Text that needs more lines than that widens the card past its widest, as far as the screen
 * allows ({@link #stretch}); only text past the last line even then is cut, at a word, with an ellipsis. Cards stack
 * by their real heights, away from the screen edge the widget is attached to: down from the top and middle rows, up from
 * the bottom row.
 * <p>
 * On screens too small for cards ({@link HudFit#compact()}) each toast is a one-line pill: icon and title, cut to fit.
 */
final class ToastWidget implements HudWidget {
    static final int MIN_WIDTH = 140;
    static final int MAX_WIDTH = 340;
    static final double SCREEN_SHARE = 0.5;
    static final int CARD_HEIGHT = 32;
    static final int TITLE_LINES = 2;
    static final int BODY_LINES = 6;
    static final int PAD = 6;
    static final int TEXT_LEFT = PAD + UiTome.TOKEN + 6;
    static final int TEXT_RIGHT = 8;
    static final int TEXT_TOP = 7;
    static final int TEXT_BOTTOM = 6;
    static final int BODY_GAP = 3;
    /** The gap under a discovery's title, room for its ruled divider. */
    static final int DIVIDER_GAP = 5;
    static final int PILL_HEIGHT = 18;
    static final int PILL_TEXT_LEFT = 20;
    static final int PILL_MAX_WIDTH = 200;
    /** Units a card travels as it slides in or out, sideways from a side column and up or down in the middle one. */
    static final int SLIDE = 18;
    static final int SLIDE_MIDDLE = 8;
    private static final int SCREEN_MARGIN = 8;
    private static final int WIDEN_STEP = 10;
    private static final int PILL_TEXT_RIGHT = 5;
    private static final int ICON = 16;
    private static final int STUD = 5;
    private static final long SHEEN_MILLIS = 700L;
    private static final int SHEEN = 0x46E2B648;
    private static final int CHALLENGE_SHEEN = 0x38B070C8;
    private static final int TIME_ALPHA = 0xB0;
    private static final double MIN_ICON_OPACITY = 0.35;
    private static final int DIVIDER_FADE = 6;

    private final ThemeLibrary themes = new ThemeLibrary();

    @Override
    public void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out) {
        HudToastQueue queue = frame.model().toasts();
        List<HudToast> shown = queue.shown();
        HudToast waiting = queue.waiting();
        if (frame.fit().compact()) {
            int width = 0;
            for (int index = 0; index < shown.size(); index++) {
                HudToast toast = shown.get(index);
                toast.height = PILL_HEIGHT;
                width = Math.max(width, PILL_TEXT_LEFT + draw.textWidth(toast.pillText()) + PILL_TEXT_RIGHT);
            }
            if (waiting != null) {
                waiting.height = PILL_HEIGHT;
            }
            out.set(Math.min(PILL_MAX_WIDTH, width), extent(shown));
            return;
        }
        boolean full = frame.fit().level() == HudFit.Level.FULL;
        int widest = widest(frame, full);
        int stretch = stretch(frame, full);
        int bodyLines = bodyLines(frame, config.placement(), draw.lineHeight());
        int width = 0;
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            layout(draw, frame.text(), toast, widest, stretch, bodyLines);
            toast.height = toast.cardHeight;
            width = Math.max(width, toast.cardWidth);
        }
        if (waiting != null) {
            layout(draw, frame.text(), waiting, widest, stretch, bodyLines);
            waiting.height = waiting.cardHeight;
        }
        out.set(width, extent(shown));
    }

    @Override
    public void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size) {
        HudToastQueue queue = frame.model().toasts();
        List<HudToast> shown = queue.shown();
        boolean up = frame.anchor().bottom();
        int column = frame.anchor().column();
        int bottom = Integer.MIN_VALUE;
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            int top = (int) Math.floor(toast.offset);
            if (index > 0) {
                top = Math.max(top, bottom + HudToastQueue.GAP);
            }
            bottom = top + toast.height;
            int y = up ? size.height - bottom : top;
            ToastLook look = look(draw, toast);
            if (frame.fit().compact()) {
                drawPill(draw, frame, queue, toast, look, y, size.width);
            } else {
                int x = column == 0 ? 0 : column == 2 ? size.width - toast.cardWidth : (size.width - toast.cardWidth) / 2;
                drawCard(draw, frame, queue, toast, look, x, y);
            }
        }
    }

    /**
     * The widest a card may be, in the widget's units: {@link #SCREEN_SHARE} of the screen up to {@link #MAX_WIDTH}, never
     * under {@link #MIN_WIDTH} unless the screen itself is narrower, and always {@link #SCREEN_MARGIN} clear of both edges.
     * On screens too short for the full HUD the scoreboard's width is kept clear on either side too.
     */
    static int widest(Frame frame, boolean full) {
        double screen = frame.screenWidth() / Math.max(1e-6, frame.unitScale());
        double share = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, screen * SCREEN_SHARE));
        return Math.max(1, (int) Math.min(share, stretch(frame, full)));
    }

    /**
     * The most a card may widen for text that would otherwise be cut: the screen less {@link #SCREEN_MARGIN} at each
     * edge, and on screens too short for the full HUD less the scoreboard's width on either side too (never below
     * {@link #MIN_WIDTH} for that).
     */
    static int stretch(Frame frame, boolean full) {
        double unit = Math.max(1e-6, frame.unitScale());
        double room = frame.screenWidth() / unit - SCREEN_MARGIN * 2.0;
        double clear = full ? room : Math.max(Math.min(MIN_WIDTH, room), room - HudVanilla.SCOREBOARD_WIDTH * 2.0 / unit);
        return Math.max(1, (int) clear);
    }

    /**
     * The body lines a card may take: {@link #BODY_LINES}, fewer when a card with a one-line title would otherwise
     * reach from the widget's edge into the protected view zone (short screens).
     */
    static int bodyLines(Frame frame, HudPlacement placement, int lineHeight) {
        HudBox zone = frame.zone();
        double unit = Math.max(1e-6, frame.unitScale());
        int row = placement.anchor().row();
        if (row == 1 || zone.height <= 0.0) {
            return BODY_LINES;
        }
        double room = row == 0 ? (zone.y - placement.offsetY()) / unit
                : (frame.screenHeight() + placement.offsetY() - zone.y - zone.height) / unit;
        int lines = (int) Math.floor((room - TEXT_TOP - lineHeight - BODY_GAP - TEXT_BOTTOM) / lineHeight);
        return Math.max(1, Math.min(BODY_LINES, lines));
    }

    /**
     * Sizes {@code toast}'s card to its text, at most {@code widest} wide or up to {@code stretch} when its text would
     * otherwise be cut, wrapping title and body (at most {@code bodyLines}) with the game's own font. Kept until an input
     * changes.
     */
    static void layout(UiDraw draw, HudText text, HudToast toast, int widest, int stretch, int bodyLines) {
        if (toast.laidOutWidest == widest && toast.laidOutStretch == stretch && toast.laidOutLines == bodyLines) {
            return;
        }
        boolean challenge = toast.kind == ToastKind.CHALLENGE;
        String title = challenge ? text.get("adapt.hud.toast.challenge") : toast.title;
        String body = challenge ? toast.title : toast.body;
        int lineHeight = draw.lineHeight();
        int smallest = Math.min(MIN_WIDTH, widest);
        int limit = widest;
        while (limit < stretch && !fits(draw, title, body, limit - TEXT_LEFT - TEXT_RIGHT, bodyLines)) {
            limit = Math.min(stretch, limit + WIDEN_STEP);
        }
        int room = Math.max(1, limit - TEXT_LEFT - TEXT_RIGHT);
        toast.shownTitle = HudWrap.clip(draw, title, room, TITLE_LINES);
        toast.shownBody = HudWrap.clip(draw, body, room, bodyLines);
        toast.titleLines = HudWrap.lines(draw, toast.shownTitle, room);
        toast.bodyLines = HudWrap.lines(draw, toast.shownBody, room);
        toast.titleWrap = toast.titleLines > 1 ? UiControl.balancedWidth(draw, toast.shownTitle, room) : room;
        toast.bodyWrap = toast.bodyLines > 1 ? UiControl.balancedWidth(draw, toast.shownBody, room) : room;
        int titleUsed = toast.titleLines > 1 ? toast.titleWrap : draw.textWidth(toast.shownTitle);
        int bodyUsed = toast.bodyLines > 1 ? toast.bodyWrap : toast.bodyLines == 1 ? draw.textWidth(toast.shownBody) : 0;
        int textHeight = toast.titleLines * lineHeight + (toast.bodyLines > 0 ? gap(toast) + toast.bodyLines * lineHeight : 0);
        toast.cardWidth = Math.max(smallest, Math.min(limit, TEXT_LEFT + Math.max(titleUsed, bodyUsed) + TEXT_RIGHT));
        toast.cardHeight = Math.max(CARD_HEIGHT, TEXT_TOP + textHeight + TEXT_BOTTOM);
        toast.lines = toast.titleLines + toast.bodyLines;
        toast.laidOutWidest = widest;
        toast.laidOutStretch = stretch;
        toast.laidOutLines = bodyLines;
    }

    private static int gap(HudToast toast) {
        return toast.kind == ToastKind.DISCOVERY ? DIVIDER_GAP : BODY_GAP;
    }

    private static boolean fits(UiDraw draw, String title, String body, int room, int bodyLines) {
        return room > 0 && HudWrap.lines(draw, title, room) <= TITLE_LINES && HudWrap.lines(draw, body, room) <= bodyLines;
    }

    /** The toast's look, worked out again only when its skill's theme changes (a resource pack reload). */
    private ToastLook look(UiDraw draw, HudToast toast) {
        PageTheme theme = toast.skillId.isEmpty() ? null : themes.theme(draw.sprites(), toast.skillId);
        if (toast.look == null || toast.lookTheme != theme) {
            toast.look = ToastLook.of(toast.kind, theme);
            toast.lookTheme = theme;
        }
        return toast.look;
    }

    private static int extent(List<HudToast> shown) {
        int extent = 0;
        int bottom = Integer.MIN_VALUE;
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            int top = (int) Math.floor(toast.offset);
            if (index > 0) {
                top = Math.max(top, bottom + HudToastQueue.GAP);
            }
            bottom = top + toast.height;
            extent = Math.max(extent, bottom);
        }
        return extent;
    }

    /** How far {@code toast} has come in, 0 to 1, eased. */
    private static double entered(Frame frame, HudToast toast) {
        return HudMotion.easeOutCubic(HudMotion.progress(frame.now() - toast.shownAt(), HudToastQueue.ENTER_MILLIS));
    }

    /** How far {@code toast} has gone out, 0 to 1, eased. */
    private static double left(Frame frame, HudToastQueue queue, HudToast toast) {
        long age = frame.now() - toast.shownAt();
        return HudMotion.easeInCubic(HudMotion.progress(age - queue.duration(toast), HudToastQueue.EXIT_MILLIS));
    }

    private static void drawCard(UiDraw draw, Frame frame, HudToastQueue queue, HudToast toast, ToastLook look, int x, int y) {
        double enter = entered(frame, toast);
        double exit = left(frame, queue, toast);
        double opacity = frame.opacity() * Math.min(enter, 1.0 - exit);
        if (opacity <= 0.01) {
            return;
        }
        int width = toast.cardWidth;
        int height = toast.cardHeight;
        if (!frame.reducedMotion()) {
            double travel = Math.min(1.0, (1.0 - enter) + exit);
            int column = frame.anchor().column();
            if (column == 1) {
                int distance = (int) Math.round(travel * SLIDE_MIDDLE);
                y += frame.anchor().bottom() ? distance : -distance;
            } else {
                int distance = (int) Math.round(travel * SLIDE);
                x += column == 0 ? -distance : distance;
            }
        }
        long age = frame.now() - toast.shownAt();
        draw.fill(x + 1, y + 1, x + width - 1, y + height - 1, HudPaint.fade(look.paper, opacity));
        if (look.sheen && !frame.reducedMotion() && age < SHEEN_MILLIS) {
            double sheen = 1.0 - HudMotion.easeOutCubic(HudMotion.progress(age, SHEEN_MILLIS));
            int color = toast.kind == ToastKind.CHALLENGE ? CHALLENGE_SHEEN : SHEEN;
            draw.fill(x + 1, y + 1, x + width - 1, y + height - 1, HudPaint.fade(color, sheen * opacity));
        }
        edge(draw, x, y, width, height, HudPaint.fade(look.edge, opacity));
        if (look.gilt) {
            edge(draw, x + 2, y + 2, width - 4, height - 4, HudPaint.fade(UiRamp.GOLD[2], opacity * 0.8));
            UiTome.filigree(draw, UiRect.of(x + 2, y + 2, x + width - 2, y + height - 2), (float) opacity);
        } else {
            stud(draw, x, y, look.leftStud, opacity);
            stud(draw, x, y + height - STUD, look.leftStud, opacity);
            stud(draw, x + width - STUD, y, look.rightStud, opacity);
            stud(draw, x + width - STUD, y + height - STUD, look.rightStud, opacity);
        }
        if (opacity >= MIN_ICON_OPACITY) {
            UiTome.token(draw, x + PAD, y + PAD, look.well, look.token);
            HudPaint.icon(draw, UiIcons.toast(toast.kind, toast.icon), x + PAD + UiTome.TOKEN_INSET,
                    y + PAD + UiTome.TOKEN_INSET, ICON, opacity);
        }
        int lineHeight = draw.lineHeight();
        int textX = x + TEXT_LEFT;
        if (toast.bodyLines == 0) {
            int titleY = y + (height - toast.titleLines * lineHeight) / 2 + 1;
            HudPaint.inkWrapped(draw, toast.shownTitle, textX, titleY, toast.titleWrap, look.title, opacity);
        } else {
            int bodyY = y + TEXT_TOP + toast.titleLines * lineHeight + gap(toast);
            HudPaint.inkWrapped(draw, toast.shownTitle, textX, y + TEXT_TOP, toast.titleWrap, look.title, opacity);
            if (look.divider) {
                divider(draw, textX, x + width - TEXT_RIGHT, bodyY - DIVIDER_GAP / 2 - 1, look.leftAccent, opacity);
            }
            HudPaint.inkWrapped(draw, toast.shownBody, textX, bodyY, toast.bodyWrap, look.body, opacity);
        }
        timeLine(draw, frame, queue, toast, look, x, y + height - 3, width, opacity);
    }

    /**
     * The time left as a one unit line along the card's foot, shrinking toward the left; on a mutated skill's card the
     * left half is in the lead's accent and the right in the partner's.
     */
    private static void timeLine(UiDraw draw, Frame frame, HudToastQueue queue, HudToast toast, ToastLook look, int x, int y,
                                 int width, double opacity) {
        long showing = queue.duration(toast) - HudToastQueue.ENTER_MILLIS;
        double left = 1.0 - HudMotion.progress(frame.now() - toast.shownAt() - HudToastQueue.ENTER_MILLIS, showing);
        int from = x + 4;
        int to = from + (int) Math.round((width - 8) * left);
        if (to <= from) {
            return;
        }
        int middle = x + width / 2;
        double alpha = opacity * TIME_ALPHA / 255.0;
        draw.fill(from, y, Math.min(to, middle), y + 1, HudPaint.fade(look.leftAccent, alpha));
        if (to > middle) {
            draw.fill(middle, y, to, y + 1, HudPaint.fade(look.rightAccent, alpha));
        }
    }

    /** A soft one unit edge, its corner pixels left out like the book's paper. */
    private static void edge(UiDraw draw, int x, int y, int width, int height, int color) {
        draw.fill(x + 1, y, x + width - 1, y + 1, color);
        draw.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        draw.fill(x, y + 1, x + 1, y + height - 1, color);
        draw.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** A corner stud like the book's page corners: outlined, lit at its top left, with a dot of accent. */
    private static void stud(UiDraw draw, int x, int y, int[] colors, double opacity) {
        draw.fill(x, y, x + STUD, y + STUD, HudPaint.fade(colors[0], opacity));
        draw.fill(x + 1, y + 1, x + STUD - 1, y + STUD - 1, HudPaint.fade(colors[1], opacity));
        draw.fill(x + 1, y + 1, x + STUD - 1, y + 2, HudPaint.fade(colors[2], opacity));
        draw.fill(x + 1, y + 2, x + 2, y + STUD - 1, HudPaint.fade(colors[2], opacity));
        draw.fill(x + 2, y + 2, x + 3, y + 3, HudPaint.fade(colors[3], opacity));
    }

    /** A one unit rule that fades at both ends and breaks round a gold mark in the accent, as the book divides a page. */
    private static void divider(UiDraw draw, int left, int right, int y, int accent, double opacity) {
        int middle = (left + right) / 2;
        int fade = Math.max(0, Math.min(DIVIDER_FADE, (middle - 4 - left) / 2));
        int soft = HudPaint.fade(HudPaint.INK_TRACK, opacity * 0.5);
        int rule = HudPaint.fade(HudPaint.INK_TRACK, opacity);
        draw.fill(left, y, left + fade, y + 1, soft);
        draw.fill(left + fade, y, middle - 3, y + 1, rule);
        draw.fill(middle + 4, y, right - fade, y + 1, rule);
        draw.fill(right - fade, y, right, y + 1, soft);
        draw.fill(middle - 1, y, middle + 2, y + 1, HudPaint.fade(accent, opacity));
        draw.fill(middle, y, middle + 1, y + 1, HudPaint.fade(UiRamp.GOLD[3], opacity));
    }

    private static void drawPill(UiDraw draw, Frame frame, HudToastQueue queue, HudToast toast, ToastLook look, int y, int width) {
        double enter = entered(frame, toast);
        double exit = left(frame, queue, toast);
        double opacity = frame.opacity() * Math.min(enter, 1.0 - exit);
        if (opacity <= 0.01) {
            return;
        }
        if (!frame.reducedMotion()) {
            y -= (int) Math.round(Math.min(1.0, (1.0 - enter) + exit) * HudToastQueue.GAP);
        }
        draw.fill(1, y + 1, width - 1, y + PILL_HEIGHT - 1, HudPaint.fade(look.paper, opacity));
        edge(draw, 0, y, width, PILL_HEIGHT, HudPaint.fade(look.edge, opacity));
        HudPaint.icon(draw, UiIcons.toast(toast.kind, toast.icon), 2, y + 1, ICON, opacity);
        if (toast.compactText == null || toast.compactWidth != width) {
            int room = width - PILL_TEXT_LEFT - PILL_TEXT_RIGHT;
            String pill = toast.pillText();
            toast.compactText = draw.textWidth(pill) <= room ? pill : HudPaint.fit(draw, toast.title(), room);
            toast.compactWidth = width;
        }
        HudPaint.ink(draw, toast.compactText, PILL_TEXT_LEFT, y + (PILL_HEIGHT - draw.lineHeight()) / 2 + 1, look.title, opacity);
    }
}
