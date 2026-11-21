package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.ui.UiDraw;

import art.arcane.adapt.client.ui.kit.UiIcons;
import java.util.List;

/**
 * Toast slips of parchment for level-ups, knowledge, unlocks, discoveries and challenges, sliding in from the edge they
 * are attached to, with ink lettering and the icon at its own 16 unit size. A challenge slip reads like vanilla's: a
 * plum "Challenge Complete!" header over the challenge's name. On screens too small for slips ({@link HudFit#compact()})
 * each toast is a one-line pill: icon and title, cut to fit.
 * Pills, and cards in the centre column, slide vertically by at most {@link #GAP}, so they never reach the neighbouring
 * slot.
 */
final class ToastWidget implements HudWidget {
    static final int CARD_WIDTH = 160;
    static final int CARD_HEIGHT = 32;
    static final int GAP = 4;
    static final int PILL_HEIGHT = 18;
    static final int PILL_TEXT_LEFT = 20;
    static final int PILL_MAX_WIDTH = 200;
    private static final int PILL_TEXT_RIGHT = 5;
    private static final int TEXT_LEFT = 30;
    private static final int ICON = 16;
    private static final int CHALLENGE_PAPER = 0xE6EAD6E0;
    private static final int CHALLENGE_HEADER = 0xFF763078;

    @Override
    public void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out) {
        HudOptions.Toasts options = (HudOptions.Toasts) config.options();
        if (frame.model().toasts().shown().isEmpty()) {
            out.set(0, 0);
            return;
        }
        if (frame.fit().compact()) {
            int width = 0;
            for (HudToast toast : frame.model().toasts().shown()) {
                width = Math.max(width, PILL_TEXT_LEFT + draw.textWidth(toast.pillText()) + PILL_TEXT_RIGHT);
            }
            out.set(Math.min(PILL_MAX_WIDTH, width), frame.fit().toasts(options.maxVisible()) * (PILL_HEIGHT + GAP) - GAP);
            return;
        }
        out.set(CARD_WIDTH, frame.fit().toasts(options.maxVisible()) * (CARD_HEIGHT + GAP) - GAP);
    }

    @Override
    public void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size) {
        HudOptions.Toasts options = (HudOptions.Toasts) config.options();
        HudToastQueue queue = frame.model().toasts();
        List<HudToast> shown = queue.shown();
        if (frame.fit().compact()) {
            for (int index = 0; index < shown.size(); index++) {
                HudToast toast = shown.get(index);
                int y = (int) Math.floor(toast.slot() * (PILL_HEIGHT + GAP));
                drawPill(draw, frame, queue, toast, options.stackUp() ? size.height - PILL_HEIGHT - y : y, size.width);
            }
            return;
        }
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            int y = (int) Math.floor(toast.slot() * (CARD_HEIGHT + GAP));
            if (options.stackUp()) {
                y = size.height - CARD_HEIGHT - y;
            }
            drawCard(draw, frame, queue, toast, y);
        }
    }

    private static void drawCard(UiDraw draw, Frame frame, HudToastQueue queue, HudToast toast, int y) {
        long now = frame.now();
        long age = now - toast.shownAt();
        double enter = HudMotion.easeOutCubic(HudMotion.progress(age, HudToastQueue.ENTER_MILLIS));
        double exit = HudMotion.easeInCubic(HudMotion.progress(age - queue.duration(toast), HudToastQueue.EXIT_MILLIS));
        double opacity = frame.opacity() * Math.min(enter, 1.0 - exit);
        if (opacity <= 0.01) {
            return;
        }
        int x = 0;
        if (!frame.reducedMotion()) {
            int column = frame.anchor().column();
            double travel = Math.min(1.0, (1.0 - enter) + exit);
            int distance = (int) Math.round(travel * (CARD_WIDTH + 8));
            x = column == 0 ? -distance : column == 2 ? distance : 0;
            if (column == 1) {
                y -= (int) Math.round(travel * GAP);
            }
        }
        boolean challenge = toast.kind() == ToastKind.CHALLENGE;
        HudPaint.paper(draw, x, y, CARD_WIDTH, CARD_HEIGHT, challenge ? CHALLENGE_PAPER : HudPaint.PAPER, opacity);
        if ((toast.kind() == ToastKind.LEVEL_UP || challenge) && !frame.reducedMotion() && age < 900L) {
            double glow = 1.0 - HudMotion.progress(age, 900L);
            draw.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, HudPaint.fade(glow(challenge), glow * opacity));
        }
        HudPaint.icon(draw, UiIcons.toast(toast.kind(), toast.icon()), x + 8, y + (CARD_HEIGHT - ICON) / 2, ICON, opacity);
        if (toast.fittedTitle == null) {
            int textWidth = CARD_WIDTH - TEXT_LEFT - 6;
            if (challenge) {
                toast.fittedTitle = HudPaint.fit(draw, frame.text().get("adapt.hud.toast.challenge"), textWidth);
                toast.fittedBody = HudPaint.fit(draw, toast.title(), textWidth);
            } else {
                toast.fittedTitle = HudPaint.fit(draw, toast.title(), textWidth);
                toast.fittedBody = HudPaint.fit(draw, toast.body(), textWidth);
            }
        }
        boolean hasBody = !toast.fittedBody.isEmpty();
        int titleColor = challenge ? CHALLENGE_HEADER : HudPaint.INK;
        HudPaint.ink(draw, toast.fittedTitle, x + TEXT_LEFT, y + (hasBody ? 7 : 12), titleColor, opacity);
        if (hasBody) {
            HudPaint.ink(draw, toast.fittedBody, x + TEXT_LEFT, y + 18, challenge ? HudPaint.INK : HudPaint.INK_MUTED, opacity);
        }
    }

    private static void drawPill(UiDraw draw, Frame frame, HudToastQueue queue, HudToast toast, int y, int width) {
        long age = frame.now() - toast.shownAt();
        double enter = HudMotion.easeOutCubic(HudMotion.progress(age, HudToastQueue.ENTER_MILLIS));
        double exit = HudMotion.easeInCubic(HudMotion.progress(age - queue.duration(toast), HudToastQueue.EXIT_MILLIS));
        double opacity = frame.opacity() * Math.min(enter, 1.0 - exit);
        if (opacity <= 0.01) {
            return;
        }
        if (!frame.reducedMotion()) {
            y -= (int) Math.round(Math.min(1.0, (1.0 - enter) + exit) * GAP);
        }
        boolean challenge = toast.kind() == ToastKind.CHALLENGE;
        HudPaint.paper(draw, 0, y, width, PILL_HEIGHT, challenge ? CHALLENGE_PAPER : HudPaint.PAPER, opacity);
        if ((toast.kind() == ToastKind.LEVEL_UP || challenge) && !frame.reducedMotion() && age < 900L) {
            double glow = 1.0 - HudMotion.progress(age, 900L);
            draw.fill(1, y + 1, width - 1, y + PILL_HEIGHT - 1, HudPaint.fade(glow(challenge), glow * opacity));
        }
        HudPaint.icon(draw, UiIcons.toast(toast.kind(), toast.icon()), 2, y + 1, ICON, opacity);
        if (toast.compactText == null || toast.compactWidth != width) {
            int room = width - PILL_TEXT_LEFT - PILL_TEXT_RIGHT;
            String pill = toast.pillText();
            toast.compactText = draw.textWidth(pill) <= room ? pill : HudPaint.fit(draw, toast.title(), room);
            toast.compactWidth = width;
        }
        HudPaint.ink(draw, toast.compactText, PILL_TEXT_LEFT, y + (PILL_HEIGHT - draw.lineHeight()) / 2 + 1,
                challenge ? CHALLENGE_HEADER : HudPaint.INK, opacity);
    }

    private static int glow(boolean challenge) {
        return challenge ? 0x40B070C8 : 0x50E2B648;
    }
}
