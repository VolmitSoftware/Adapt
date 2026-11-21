package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.ClientProtocol;

import java.util.Objects;

/**
 * Where decoded frames go. A sink left at its {@code NONE} default also leaves its capability bit out of
 * {@code Hello}, so the server keeps its chest or action bar fallback for that part.
 */
public record ClientSinks(PanelSink panels, HudSink hud, RouteSink routes, ItemSink items, FeedbackSink feedback,
                          InsightSink insight) {
    public static final ClientSinks NONE = new ClientSinks(PanelSink.NONE, HudSink.NONE, RouteSink.NONE, ItemSink.NONE,
            FeedbackSink.NONE, InsightSink.NONE);

    public ClientSinks {
        Objects.requireNonNull(panels, "panels");
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(insight, "insight");
    }

    public ClientSinks withPanels(PanelSink sink) {
        return new ClientSinks(sink, hud, routes, items, feedback, insight);
    }

    public ClientSinks withHud(HudSink sink) {
        return new ClientSinks(panels, sink, routes, items, feedback, insight);
    }

    public ClientSinks withRoutes(RouteSink sink) {
        return new ClientSinks(panels, hud, sink, items, feedback, insight);
    }

    public ClientSinks withItems(ItemSink sink) {
        return new ClientSinks(panels, hud, routes, sink, feedback, insight);
    }

    public ClientSinks withFeedback(FeedbackSink sink) {
        return new ClientSinks(panels, hud, routes, items, sink, insight);
    }

    public ClientSinks withInsight(InsightSink sink) {
        return new ClientSinks(panels, hud, routes, items, feedback, sink);
    }

    /** The capability bits this client offers: screens with a route sink, toasts with a HUD sink. */
    public int capabilities() {
        int mask = 0;
        if (routes != RouteSink.NONE) {
            mask |= ClientProtocol.SCREENS;
        }
        if (panels != PanelSink.NONE) {
            mask |= ClientProtocol.PANEL;
        }
        if (hud != HudSink.NONE) {
            mask |= ClientProtocol.HUD | ClientProtocol.TOASTS;
        }
        if (items != ItemSink.NONE) {
            mask |= ClientProtocol.ITEMS;
        }
        if (insight != InsightSink.NONE) {
            mask |= ClientProtocol.INSIGHT;
        }
        return mask;
    }
}
