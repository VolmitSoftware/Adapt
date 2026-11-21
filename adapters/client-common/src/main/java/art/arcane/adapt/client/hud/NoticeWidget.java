package art.arcane.adapt.client.hud;

import art.arcane.adapt.api.notification.HudSlot;
import art.arcane.adapt.client.ui.UiDraw;

import java.util.List;

/**
 * Text segments the server publishes (ambient status, notices, titles, menu titles), highest priority first, each
 * aligned by its slot. Titles are drawn larger, except on screens too small for that ({@link HudFit#compact()}),
 * where every line is plain and cut to the width the renderer found free.
 */
final class NoticeWidget implements HudWidget {
    static final int LINE_HEIGHT = 13;
    static final int EMPHASIZED_HEIGHT = 16;
    private static final float EMPHASIS = 1.25F;
    private static final long FADE_IN_MILLIS = 150L;
    private static final int PADDING = 10;

    @Override
    public void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out) {
        HudOptions.Notices options = (HudOptions.Notices) config.options();
        List<HudNotice> notices = frame.model().notices();
        int count = Math.min(frame.fit().noticeLines(options.maxLines()), notices.size());
        boolean compact = frame.fit().compact();
        int width = 0;
        int height = 0;
        for (int index = 0; index < count; index++) {
            HudNotice notice = notices.get(index);
            if (compact) {
                width = Math.max(width, Math.min(ToastWidget.PILL_MAX_WIDTH, draw.textWidth(notice.text()) + PADDING));
                height += LINE_HEIGHT;
            } else {
                width = Math.max(width, lineWidth(draw, notice));
                height += notice.emphasized() ? EMPHASIZED_HEIGHT : LINE_HEIGHT;
            }
        }
        out.set(width, height);
    }

    @Override
    public void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size) {
        HudOptions.Notices options = (HudOptions.Notices) config.options();
        List<HudNotice> notices = frame.model().notices();
        int count = Math.min(frame.fit().noticeLines(options.maxLines()), notices.size());
        boolean bottomUp = frame.anchor().bottom();
        boolean compact = frame.fit().compact();
        int y = bottomUp ? size.height : 0;
        for (int index = 0; index < count; index++) {
            HudNotice notice = notices.get(index);
            int height = notice.emphasized() && !compact ? EMPHASIZED_HEIGHT : LINE_HEIGHT;
            if (bottomUp) {
                y -= height;
            }
            long fadeIn = frame.reducedMotion() ? 1L : FADE_IN_MILLIS;
            double presence = HudMotion.presence(frame.now(), notice.shownAt(), notice.expiresAt(), fadeIn, HudModel.NOTICE_FADE_MILLIS);
            if (compact) {
                drawCompactLine(draw, notice, size.width, y, frame.opacity() * presence);
            } else {
                drawLine(draw, notice, size.width, y, height, frame.opacity() * presence);
            }
            if (!bottomUp) {
                y += height;
            }
        }
    }

    private static void drawLine(UiDraw draw, HudNotice notice, int widgetWidth, int y, int height, double opacity) {
        if (opacity <= 0.01) {
            return;
        }
        int width = lineWidth(draw, notice);
        HudSlot slot = notice.slot();
        int x = slot == HudSlot.LEFT ? 0 : slot == HudSlot.RIGHT ? widgetWidth - width : (widgetWidth - width) / 2;
        HudPaint.panel(draw, x, y, width, height - 1, opacity);
        if (notice.emphasized()) {
            draw.pushPose();
            draw.translate(x + 5.0F, y + 2.0F);
            draw.scale(EMPHASIS, EMPHASIS);
            HudPaint.text(draw, notice.text(), 0, 0, HudPaint.TEXT, opacity);
            draw.popPose();
        } else {
            HudPaint.text(draw, notice.text(), x + 5, y + 2, HudPaint.TEXT, opacity);
        }
    }

    private static void drawCompactLine(UiDraw draw, HudNotice notice, int widgetWidth, int y, double opacity) {
        if (opacity <= 0.01) {
            return;
        }
        if (notice.compactText == null || notice.compactSource != notice.text() || notice.compactWidth != widgetWidth) {
            notice.compactText = HudPaint.fit(draw, notice.text(), widgetWidth - PADDING);
            notice.compactSource = notice.text();
            notice.compactWidth = widgetWidth;
        }
        int width = Math.min(widgetWidth, draw.textWidth(notice.compactText) + PADDING);
        HudSlot slot = notice.slot();
        int x = slot == HudSlot.LEFT ? 0 : slot == HudSlot.RIGHT ? widgetWidth - width : (widgetWidth - width) / 2;
        HudPaint.panel(draw, x, y, width, LINE_HEIGHT - 1, opacity);
        HudPaint.text(draw, notice.compactText, x + 5, y + 2, HudPaint.TEXT, opacity);
    }

    private static int lineWidth(UiDraw draw, HudNotice notice) {
        int text = draw.textWidth(notice.text());
        return (notice.emphasized() ? (int) Math.ceil(text * EMPHASIS) : text) + 10;
    }
}
