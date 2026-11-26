package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.net.FeedbackSink;
import art.arcane.adapt.client.net.RouteSink;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.state.ClientActions;
import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiScreen;
import art.arcane.adapt.client.theme.PageThemes;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.SkillView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Opens the Adapt pages: the skills list, a skill and an adaptation. The pages form a path (skills, then a skill, then
 * one of its adaptations); going back opens the parent page and closes from the skills list. The leaf each page
 * was turned to, and where its scrolling list rested, is remembered for the session. Server routes and feedback arrive here on the client thread; feedback with no
 * Adapt page open goes to {@code fallback} (the HUD in game). A session end closes the open page.
 */
public final class ScreenRouter implements RouteSink, FeedbackSink {
    private final UiHost host;
    private final ClientState state;
    private final ClientActions actions;
    private final UiTheme theme;
    private final UiMotion motion;
    private final PageThemes themes;
    private final FeedbackSink fallback;
    private final Map<Route, Integer> leaves = new HashMap<>();
    private final Map<Route, Integer> scrolls = new HashMap<>();
    private CatalogIndex index;

    public ScreenRouter(UiHost host, ClientState state, ClientActions actions, UiTheme theme, UiMotion motion, PageThemes themes,
                        FeedbackSink fallback) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.motion = Objects.requireNonNull(motion, "motion");
        this.themes = Objects.requireNonNull(themes, "themes");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        state.onDisconnected(this::reset);
    }

    public ClientState state() {
        return state;
    }

    public ClientActions actions() {
        return actions;
    }

    public UiTheme theme() {
        return theme;
    }

    public UiMotion motion() {
        return motion;
    }

    public PageThemes themes() {
        return themes;
    }

    /** Opens the page for {@code route}; the page already open stays as it is. */
    @Override
    public void open(Route route) {
        Objects.requireNonNull(route, "route");
        AdaptScreen current = current();
        if (current != null && current.route().equals(route)) {
            return;
        }
        host.open(create(route));
    }

    /** Opens the parent of the open page, or closes the skills list. */
    public void back() {
        AdaptScreen current = current();
        if (current == null) {
            return;
        }
        List<Route> path = path(current.route());
        if (path.size() <= 1) {
            current.close();
        } else {
            open(path.get(path.size() - 2));
        }
    }

    /** The open page shows the answer; with no Adapt page open the fallback shows it. */
    @Override
    public void feedback(FeedbackKind kind, String text) {
        AdaptScreen current = current();
        if (current != null) {
            current.feedback(kind, text);
        } else {
            fallback.feedback(kind, text);
        }
    }

    /** Closes the open page and forgets the leaves turned to, for a session that ended. */
    public void reset() {
        AdaptScreen current = current();
        if (current != null) {
            current.close();
        }
        leaves.clear();
    }

    /** The open Adapt page, or null when another screen (or none) is showing. */
    public AdaptScreen current() {
        UiScreen screen = host.current();
        return screen instanceof AdaptScreen page ? page : null;
    }

    /** The pages from the skills list down to {@code route}. */
    public List<Route> path(Route route) {
        Route skills = new Route.Skills();
        return switch (route) {
            case Route.Skills ignored -> List.of(skills);
            case Route.Skill skill -> List.of(skills, skill);
            case Route.Adaptation adaptation -> {
                CatalogIndex catalog = index();
                AdaptationView view = catalog == null ? null : catalog.adaptation(adaptation.adaptationId());
                KeptView held = state.keptAdaptation(adaptation.adaptationId());
                if (held != null && catalog != null
                        && (catalog.skill(held.ownerId()) != null || CatalogIndex.level(state.player(), adaptation.adaptationId()) == 0)) {
                    yield heldPath(catalog, held, adaptation);
                }
                yield view == null ? List.of(skills, adaptation) : List.of(skills, new Route.Skill(view.skillId()), adaptation);
            }
            case Route.Mutate mutate -> List.of(skills, new Route.Skill(mutate.skillId()), mutate);
        };
    }

    /** The pages down to an original held without learning: its mutated skill's, then the lending upgrade's if any. */
    private static List<Route> heldPath(CatalogIndex catalog, KeptView held, Route.Adaptation route) {
        Route skills = new Route.Skills();
        if (catalog.skill(held.ownerId()) != null) {
            return List.of(skills, new Route.Skill(held.ownerId()), route);
        }
        AdaptationView upgrade = catalog.adaptation(held.ownerId());
        return upgrade == null ? List.of(skills, route)
                : List.of(skills, new Route.Skill(upgrade.skillId()), new Route.Adaptation(held.ownerId()), route);
    }

    /** The breadcrumb text for {@code route}: the page name. */
    public String label(Route route) {
        CatalogIndex index = index();
        return switch (route) {
            case Route.Skills ignored -> ScreenText.SKILLS.text();
            case Route.Skill skill -> {
                SkillView view = index == null ? null : index.skill(skill.skillId());
                yield view == null ? skill.skillId() : view.name();
            }
            case Route.Adaptation adaptation -> {
                AdaptationView view = index == null ? null : index.shown(adaptation.adaptationId());
                yield view == null ? adaptation.adaptationId() : view.name();
            }
            case Route.Mutate ignored -> ScreenText.MUTATE.text();
        };
    }

    /** The leaf {@code route}'s page was last turned to, 0 at first. */
    int leaf(Route route) {
        return leaves.getOrDefault(route, 0);
    }

    void remember(Route route, int leaf) {
        leaves.put(route, leaf);
    }

    /** Where {@code route}'s scrolling list last rested, 0 at first. */
    int scroll(Route route) {
        return scrolls.getOrDefault(route, 0);
    }

    void rememberScroll(Route route, int rest) {
        scrolls.put(route, rest);
    }

    /** An index of the current catalog, or null before the server sent one. */
    CatalogIndex index() {
        CatalogView catalog = state.catalog();
        if (catalog == null) {
            return null;
        }
        Map<String, String> shortNames = state.shortNames();
        CatalogIndex cached = index;
        if (cached == null || cached.catalog() != catalog || cached.shortNames() != shortNames) {
            cached = new CatalogIndex(catalog, shortNames);
            index = cached;
        }
        return cached;
    }

    private AdaptScreen create(Route route) {
        return switch (route) {
            case Route.Skills ignored -> new SkillsScreen(this);
            case Route.Skill skill -> new SkillScreen(this, skill);
            case Route.Adaptation adaptation -> new AdaptationScreen(this, adaptation);
            case Route.Mutate mutate -> new MutateScreen(this, mutate);
        };
    }
}
