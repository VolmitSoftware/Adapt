package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.view.PlayerHeader;

/** Power used/max, master level and progress, wisdom and total knowledge; each can be turned off. */
final class ResourcesWidget implements HudWidget {
    static final int ROW_HEIGHT = 14;
    static final int MIN_WIDTH = 92;
    private static final int PADDING = 2;
    private static final int GAP = 4;
    private static final int POWER = 0;
    private static final int MASTER = 1;
    private static final int WISDOM = 2;
    private static final int KNOWLEDGE = 3;
    private static final String[] LABEL_KEYS = {
            "adapt.hud.resource.power", "adapt.hud.resource.master", "adapt.hud.resource.wisdom", "adapt.hud.resource.knowledge"
    };
    private static final int[] COLORS = {0x7FD8FF, 0xC59BFF, 0xFFD45C, 0x8FE3A6};

    private final String[] values = {"", "", "", ""};
    private final double[] fractions = {-1.0, -1.0, -1.0, -1.0};
    private int cachedRevision = Integer.MIN_VALUE;
    private PlayerHeader cachedHeader;
    private long cachedKnowledge = Long.MIN_VALUE;

    @Override
    public void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out) {
        HudOptions.Resources options = (HudOptions.Resources) config.options();
        if (!refresh(frame.model()) || options.count() == 0) {
            out.set(0, 0);
            return;
        }
        HudText text = frame.text();
        if (config.placement().orientation() == HudOrientation.HORIZONTAL) {
            int width = PADDING * 2;
            int shown = 0;
            for (int entry = 0; entry < 4; entry++) {
                if (enabled(options, entry)) {
                    width += chipWidth(draw, text, entry) + (shown > 0 ? GAP : 0);
                    shown++;
                }
            }
            out.set(width, PADDING * 2 + ROW_HEIGHT);
            return;
        }
        int labels = 0;
        int numbers = 0;
        for (int entry = 0; entry < 4; entry++) {
            if (enabled(options, entry)) {
                labels = Math.max(labels, draw.textWidth(text.get(LABEL_KEYS[entry])));
                numbers = Math.max(numbers, draw.textWidth(values[entry]));
            }
        }
        out.set(Math.max(MIN_WIDTH, PADDING * 2 + 4 + labels + 12 + numbers + 4), PADDING * 2 + ROW_HEIGHT * options.count());
    }

    @Override
    public void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size) {
        HudOptions.Resources options = (HudOptions.Resources) config.options();
        double opacity = frame.opacity();
        HudPaint.panel(draw, 0, 0, size.width, size.height, opacity);
        HudText text = frame.text();
        boolean horizontal = config.placement().orientation() == HudOrientation.HORIZONTAL;
        int x = PADDING;
        int y = PADDING;
        for (int entry = 0; entry < 4; entry++) {
            if (!enabled(options, entry)) {
                continue;
            }
            String label = text.get(LABEL_KEYS[entry]);
            int width = horizontal ? chipWidth(draw, text, entry) : size.width - PADDING * 2;
            HudPaint.text(draw, label, x + 4, y + 2, HudPaint.TEXT_DIM, opacity);
            int valueWidth = draw.textWidth(values[entry]);
            HudPaint.text(draw, values[entry], x + width - 4 - valueWidth, y + 2, HudPaint.mix(HudPaint.opaque(COLORS[entry]), 0xFFFFFFFF, 0.3), opacity);
            if (fractions[entry] >= 0.0) {
                HudPaint.bar(draw, x + 4, y + 11, width - 8, 1, fractions[entry], COLORS[entry], opacity);
            }
            if (horizontal) {
                x += width + GAP;
            } else {
                y += ROW_HEIGHT;
            }
        }
    }

    private int chipWidth(UiDraw draw, HudText text, int entry) {
        return 4 + draw.textWidth(text.get(LABEL_KEYS[entry])) + 6 + draw.textWidth(values[entry]) + 4;
    }

    private static boolean enabled(HudOptions.Resources options, int entry) {
        return switch (entry) {
            case POWER -> options.power();
            case MASTER -> options.master();
            case WISDOM -> options.wisdom();
            default -> options.knowledge();
        };
    }

    private boolean refresh(HudModel model) {
        PlayerHeader header = model.header();
        if (header == null) {
            return false;
        }
        if (cachedRevision == model.revision() && cachedHeader == header && cachedKnowledge == model.knowledgeTotal()) {
            return true;
        }
        cachedRevision = model.revision();
        cachedHeader = header;
        cachedKnowledge = model.knowledgeTotal();
        values[POWER] = header.powerUsed() + "/" + header.powerMax();
        fractions[POWER] = header.powerMax() > 0 ? (double) header.powerUsed() / header.powerMax() : 0.0;
        values[MASTER] = Integer.toString(header.masterLevel());
        fractions[MASTER] = Math.max(0.0, Math.min(1.0, header.masterProgress()));
        values[WISDOM] = Long.toString(header.wisdom());
        values[KNOWLEDGE] = Long.toString(model.knowledgeTotal());
        return true;
    }
}
