package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.AdaptKey;
import art.arcane.adapt.client.net.FeedbackSink;
import art.arcane.adapt.client.panel.PanelPreview;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.state.ClientActions;
import art.arcane.adapt.client.state.ClientFrameSender;
import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.theme.PageThemes;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Development preview of the Adapt pages with no server: with {@code -Dadapt.client.preview=screens} the "Open skills"
 * key opens the skills page over {@link PreviewSamples}. Requests go to a stand-in server that answers a few ticks
 * later, so the waiting state shows, and applies learn, unlearn and preference changes with the real rules.
 */
public final class ScreenPreview implements ClientFrameSender {
    public static final String SCREENS = "screens";
    public static final int REPLY_TICKS = 6;

    private final ClientState state = new ClientState();
    private final ClientActions actions = new ClientActions(state, this);
    private final ScreenRouter router;
    private final List<Reply> replies = new ArrayList<>();
    private long tick;

    public ScreenPreview(UiHost host, UiMotion motion, FeedbackSink fallback) {
        state.negotiated(ClientProtocol.VERSION, new ServerInfo("preview", "preview", ClientProtocol.SCREENS | ClientProtocol.PANEL));
        state.acceptCatalog(PreviewSamples.catalog());
        state.acceptPlayer(PreviewSamples.player());
        router = new ScreenRouter(host, state, actions, UiTheme.DEFAULT, motion, PageThemes.defaults(), fallback);
    }

    /** Binds the preview to the "Open skills" key when {@code adapt.client.preview} is {@value #SCREENS}. */
    public static void installIfRequested(AdaptClient client) {
        if (!SCREENS.equals(System.getProperty(PanelPreview.PROPERTY))) {
            return;
        }
        ScreenText.install(client.host());
        ScreenPreview preview = new ScreenPreview(client.host(), UiMotion.system(), client.hud()::feedback);
        client.bindKey(AdaptKey.OPEN_SKILLS, preview::open);
        client.onTick(preview::tick);
    }

    public ScreenRouter router() {
        return router;
    }

    public ClientState state() {
        return state;
    }

    /** Asks the stand-in server for the skills page, as the key does on a real server. */
    public void open() {
        actions.open(new Route.Skills());
    }

    @Override
    public boolean send(ClientFrame frame) {
        replies.add(new Reply(tick + REPLY_TICKS, frame));
        return true;
    }

    /** One client tick: answers the requests that are due. */
    public void tick() {
        tick++;
        List<Reply> due = new ArrayList<>();
        replies.removeIf((Reply reply) -> {
            if (reply.at() <= tick) {
                due.add(reply);
                return true;
            }
            return false;
        });
        for (Reply reply : due) {
            answer(reply.frame());
        }
    }

    private void answer(ClientFrame frame) {
        switch (frame) {
            case ClientFrame.Open open -> router.open(open.route());
            case ClientFrame.Learn learn -> learn(learn.adaptationId(), learn.level());
            case ClientFrame.Unlearn unlearn -> unlearn(unlearn.adaptationId(), unlearn.level());
            case ClientFrame.SetPreference set -> setPreference(new PreferenceKey(set.owner(), set.ownerId(), set.preferenceId()), set.value());
            case ClientFrame.ResetPreference reset -> resetPreferences(reset.owner(), reset.ownerId());
            default -> {
            }
        }
    }

    private void learn(String adaptationId, int level) {
        CatalogIndex catalog = router.index();
        PlayerView player = state.player();
        AdaptationView adaptation = catalog.adaptation(adaptationId);
        if (adaptation == null) {
            router.feedback(FeedbackKind.FAILURE, "That adaptation is not available.");
            return;
        }
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, level);
        if (!check.allowed()) {
            router.feedback(FeedbackKind.FAILURE, LearnText.learnBlock(catalog, check));
            return;
        }
        List<PreferenceValue> preferences = new ArrayList<>(player.preferences());
        if (check.from() == 0) {
            for (PreferenceView preference : adaptation.preferences()) {
                preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, adaptationId, preference.id()),
                        preference.defaultValue()));
            }
        }
        state.acceptPlayer(change(player, adaptation, check.to(), -check.knowledgeCost(), check.powerCost(), preferences));
        router.feedback(FeedbackKind.SUCCESS, "Learned " + adaptation.name() + " "
                + ScreenText.roman(check.to()));
    }

    private void unlearn(String adaptationId, int level) {
        CatalogIndex catalog = router.index();
        PlayerView player = state.player();
        AdaptationView adaptation = catalog.adaptation(adaptationId);
        if (adaptation == null) {
            router.feedback(FeedbackKind.FAILURE, "That adaptation is not available.");
            return;
        }
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, level);
        if (!check.allowed()) {
            String reason = LearnText.unlearnBlock(check);
            router.feedback(FeedbackKind.FAILURE, reason.isEmpty() ? "Nothing to unlearn." : reason);
            return;
        }
        List<PreferenceValue> preferences = new ArrayList<>(player.preferences());
        if (check.to() == 0) {
            preferences.removeIf((PreferenceValue value) -> value.key().owner() == PreferenceOwner.ADAPTATION
                    && value.key().ownerId().equals(adaptationId));
        }
        int power = -LearnRules.powerCost(adaptation, check.to(), check.from());
        state.acceptPlayer(change(player, adaptation, check.to(), check.refund(), power, preferences));
        router.feedback(FeedbackKind.SUCCESS, "Unlearned " + adaptation.name() + " "
                + ScreenText.roman(check.from()));
    }

    private void setPreference(PreferenceKey key, String value) {
        PlayerView player = state.player();
        List<PreferenceValue> preferences = new ArrayList<>(player.preferences());
        preferences.removeIf((PreferenceValue existing) -> existing.key().equals(key));
        preferences.add(new PreferenceValue(key, value));
        preferences.sort(Comparator.comparing(PreferenceValue::key));
        state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), preferences, player.access()));
        router.feedback(FeedbackKind.SUCCESS, "Preference saved.");
    }

    private void resetPreferences(PreferenceOwner owner, String ownerId) {
        CatalogIndex catalog = router.index();
        PlayerView player = state.player();
        List<PreferenceView> defaults = new ArrayList<>();
        if (owner == PreferenceOwner.SKILL) {
            SkillView skill = catalog.skill(ownerId);
            if (skill != null) {
                defaults.addAll(skill.preferences());
            }
        } else {
            AdaptationView adaptation = catalog.adaptation(ownerId);
            if (adaptation != null) {
                defaults.addAll(adaptation.preferences());
            }
        }
        List<PreferenceValue> preferences = new ArrayList<>(player.preferences());
        for (PreferenceView preference : defaults) {
            PreferenceKey key = new PreferenceKey(owner, ownerId, preference.id());
            for (int index = 0; index < preferences.size(); index++) {
                if (preferences.get(index).key().equals(key)) {
                    preferences.set(index, new PreferenceValue(key, preference.defaultValue()));
                }
            }
        }
        state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), preferences, player.access()));
        router.feedback(FeedbackKind.SUCCESS, "Preferences reset.");
    }

    private static PlayerView change(PlayerView player, AdaptationView adaptation, int level, long knowledge, int power,
                                     List<PreferenceValue> preferences) {
        List<SkillLineView> lines = new ArrayList<>();
        for (SkillLineView line : player.skills()) {
            lines.add(line.skillId().equals(adaptation.skillId())
                    ? new SkillLineView(line.skillId(), line.xp(), line.level(), line.progress(), line.xpToNext(), line.knowledge() + knowledge)
                    : line);
        }
        Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
        if (level <= 0) {
            levels.remove(adaptation.id());
        } else {
            levels.put(adaptation.id(), level);
        }
        PlayerHeader header = player.header();
        PlayerHeader changed = new PlayerHeader(header.masterLevel(), header.masterProgress(), header.powerUsed() + power, header.powerMax(),
                header.wisdom(), header.effectsEnabled());
        return new PlayerView(changed, lines, levels, preferences, player.access());
    }

    private record Reply(long at, ClientFrame frame) {
    }
}
