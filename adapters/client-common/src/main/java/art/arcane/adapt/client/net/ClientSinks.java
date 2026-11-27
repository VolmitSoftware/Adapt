package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.ClientProtocol;

import java.util.Objects;

/**
 * Where decoded frames go. A sink left at its {@code NONE} default also leaves its capability bit out of
 * {@code Hello}, so the server keeps its chest or action bar fallback for that part.
 */
public record ClientSinks(PanelSink panels, HudSink hud, RouteSink routes, ItemSink items, FeedbackSink feedback,
                          InsightSink insight, AssetSink assets) {
    public static final ClientSinks NONE = new ClientSinks(PanelSink.NONE, HudSink.NONE, RouteSink.NONE, ItemSink.NONE,
            FeedbackSink.NONE, InsightSink.NONE, AssetSink.NONE);

    public ClientSinks {
        Objects.requireNonNull(panels, "panels");
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(insight, "insight");
        Objects.requireNonNull(assets, "assets");
    }

    public ClientSinks withPanels(PanelSink sink) {
        return new ClientSinks(sink, hud, routes, items, feedback, insight, assets);
    }

    public ClientSinks withHud(HudSink sink) {
        return new ClientSinks(panels, sink, routes, items, feedback, insight, assets);
    }

    public ClientSinks withRoutes(RouteSink sink) {
        return new ClientSinks(panels, hud, sink, items, feedback, insight, assets);
    }

    public ClientSinks withItems(ItemSink sink) {
        return new ClientSinks(panels, hud, routes, sink, feedback, insight, assets);
    }

    public ClientSinks withFeedback(FeedbackSink sink) {
        return new ClientSinks(panels, hud, routes, items, sink, insight, assets);
    }

    public ClientSinks withInsight(InsightSink sink) {
        return new ClientSinks(panels, hud, routes, items, feedback, sink, assets);
    }

    public ClientSinks withAssets(AssetSink sink) {
        return new ClientSinks(panels, hud, routes, items, feedback, insight, sink);
    }

    /** The capability bits this client offers: screens with a route sink, toasts with a HUD sink. */
    public int capabilities() {
        int mask = 0;
        if (routes != RouteSink.NONE) {
            mask |= ClientProtocol.SCREENS | ClientProtocol.KEPT | ClientProtocol.TOOLS | ClientProtocol.XP_GUIDE
                    | ClientProtocol.SPENDING | ClientProtocol.UNMUTATE | ClientProtocol.GROUP_LABELS
                    | ClientProtocol.SHORT_NAMES | ClientProtocol.RECIPES;
        }
        if (panels != PanelSink.NONE) {
            mask |= ClientProtocol.PANEL;
        }
        if (routes != RouteSink.NONE && panels != PanelSink.NONE) {
            mask |= ClientProtocol.MUTATE | ClientProtocol.MUTATE_SPREAD;
        }
        if (hud != HudSink.NONE) {
            mask |= ClientProtocol.HUD | ClientProtocol.TOASTS | ClientProtocol.SKILL_TOASTS;
        }
        if (items != ItemSink.NONE) {
            mask |= ClientProtocol.ITEMS;
        }
        if (insight != InsightSink.NONE) {
            mask |= ClientProtocol.INSIGHT;
        }
        if (assets != AssetSink.NONE) {
            mask |= ClientProtocol.ASSETS;
        }
        return mask;
    }
}
