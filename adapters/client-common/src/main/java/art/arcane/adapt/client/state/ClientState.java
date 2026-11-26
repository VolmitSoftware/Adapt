package art.arcane.adapt.client.state;

import art.arcane.adapt.client.protocol.ItemDescriptor;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViewPatch;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.XpGuideView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * What the client knows about the current server session: the negotiated version and capability bits, the catalog,
 * the player's view and the custom items. Read and written on the client thread; listeners run there too.
 */
public final class ClientState {
    private final List<Consumer<ServerInfo>> connectedListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> disconnectedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CatalogView>> catalogListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlayerView>> playerListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<List<String>>> mutateListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> keptListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> toolsListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> recipeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MutationSpreadView>> spreadListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> guideListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> spendingListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> groupLabelListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> shortNameListeners = new CopyOnWriteArrayList<>();
    private int version;
    private ServerInfo server;
    private CatalogView catalog;
    private PlayerView player;
    private List<ItemDescriptor> items = List.of();
    private List<String> mutateOffers = List.of();
    private List<KeptView> kept = List.of();
    private String keptTitle = "";
    private List<ToolView> tools = List.of();
    private Map<String, List<RecipeView>> recipes = Map.of();
    private MutationSpreadView spread;
    private Map<String, XpGuideView> xpGuides = Map.of();
    private String xpGuideTitle = "";
    private List<SpendView> spends = List.of();
    private Map<String, List<String>> groupLabels = Map.of();
    private Map<String, String> shortNames = Map.of();
    private boolean inactiveSkillsAllowed = true;

    /** True between the server's {@code Accept} and the end of the session. */
    public boolean negotiated() {
        return server != null;
    }

    /** The negotiated protocol version, 0 when not negotiated. */
    public int version() {
        return version;
    }

    /** The negotiated capability bits, 0 when not negotiated. */
    public int capabilityMask() {
        return server == null ? 0 : server.capabilityMask();
    }

    public boolean has(int capabilityBit) {
        return (capabilityMask() & capabilityBit) == capabilityBit && capabilityBit != 0;
    }

    /** The server as it answered {@code Hello}, with the negotiated mask; null when not negotiated. */
    public ServerInfo server() {
        return server;
    }

    /** The latest catalog, or null before the server sent one. */
    public CatalogView catalog() {
        return catalog;
    }

    /** The player's current view, or null before the server sent one. */
    public PlayerView player() {
        return player;
    }

    public List<ItemDescriptor> items() {
        return items;
    }

    /** The skills whose page offers Mutate; empty unless the server negotiated MUTATE. */
    public List<String> mutateOffers() {
        return mutateOffers;
    }

    public ClientSubscription onMutateOffers(Consumer<List<String>> listener) {
        return add(mutateListeners, listener);
    }

    public void acceptMutateOffers(List<String> skillIds) {
        mutateOffers = List.copyOf(skillIds);
        for (Consumer<List<String>> listener : mutateListeners) {
            listener.accept(mutateOffers);
        }
    }

    /**
     * The originals a mutated skill keeps without learning, or an upgrade lends a level, in order; empty unless the
     * server negotiated KEPT.
     */
    public List<KeptView> kept(String ownerId) {
        List<KeptView> found = new ArrayList<>();
        for (KeptView entry : kept) {
            if (entry.ownerId().equals(ownerId)) {
                found.add(entry);
            }
        }
        return found;
    }

    /** How the player holds {@code adaptationId} without learning it, or null when they do not. */
    public KeptView keptAdaptation(String adaptationId) {
        for (KeptView entry : kept) {
            if (entry.adaptationId().equals(adaptationId)) {
                return entry;
            }
        }
        return null;
    }

    /** The heading the server names kept originals with, in the player's language. */
    public String keptTitle() {
        return keptTitle;
    }

    public void acceptKept(String title, List<KeptView> entries) {
        keptTitle = title;
        kept = List.copyOf(entries);
        for (Runnable listener : keptListeners) {
            listener.run();
        }
    }

    public ClientSubscription onKept(Runnable listener) {
        return add(keptListeners, listener);
    }

    /** The mutation tools the adaptation hands out, in order; empty unless the server negotiated TOOLS. */
    public List<ToolView> tools(String adaptationId) {
        List<ToolView> found = new ArrayList<>();
        for (ToolView tool : tools) {
            if (tool.adaptationId().equals(adaptationId)) {
                found.add(tool);
            }
        }
        return found;
    }

    /** The mutation tool with this id, or null. */
    public ToolView tool(String toolId) {
        for (ToolView tool : tools) {
            if (tool.toolId().equals(toolId)) {
                return tool;
            }
        }
        return null;
    }

    public void acceptTools(List<ToolView> entries) {
        tools = List.copyOf(entries);
        for (Runnable listener : toolsListeners) {
            listener.run();
        }
    }

    /** The recipes the adaptation unlocks, in the order its page shows them; empty unless the server negotiated RECIPES. */
    public List<RecipeView> recipes(String adaptationId) {
        return recipes.getOrDefault(adaptationId, List.of());
    }

    public void acceptRecipes(List<RecipeView> entries) {
        Map<String, List<RecipeView>> indexed = new LinkedHashMap<>();
        for (RecipeView recipe : entries) {
            indexed.computeIfAbsent(recipe.adaptationId(), (String id) -> new ArrayList<>()).add(recipe);
        }
        Map<String, List<RecipeView>> frozen = new LinkedHashMap<>();
        indexed.forEach((String id, List<RecipeView> list) -> frozen.put(id, List.copyOf(list)));
        recipes = Map.copyOf(frozen);
        for (Runnable listener : recipeListeners) {
            listener.run();
        }
    }

    public ClientSubscription onRecipes(Runnable listener) {
        return add(recipeListeners, listener);
    }

    /** How {@code skillId} earns XP, or null unless the server negotiated XP_GUIDE and described it. */
    public XpGuideView xpGuide(String skillId) {
        return xpGuides.get(skillId);
    }

    /** The heading the server names XP guides with, in the player's language. */
    public String xpGuideTitle() {
        return xpGuideTitle;
    }

    public void acceptXpGuides(String title, List<XpGuideView> guides) {
        Map<String, XpGuideView> indexed = new LinkedHashMap<>();
        for (XpGuideView guide : guides) {
            indexed.put(guide.skillId(), guide);
        }
        xpGuideTitle = title;
        xpGuides = Map.copyOf(indexed);
        for (Runnable listener : guideListeners) {
            listener.run();
        }
    }

    public ClientSubscription onTools(Runnable listener) {
        return add(toolsListeners, listener);
    }

    /** The server's latest answer for the mutation spread, or null before one; only under MUTATE_SPREAD. */
    public MutationSpreadView spread() {
        return spread;
    }

    public void acceptSpread(MutationSpreadView view) {
        spread = Objects.requireNonNull(view, "view");
        for (Consumer<MutationSpreadView> listener : spreadListeners) {
            listener.accept(view);
        }
    }

    public ClientSubscription onSpread(Consumer<MutationSpreadView> listener) {
        return add(spreadListeners, listener);
    }

    public ClientSubscription onXpGuides(Runnable listener) {
        return add(guideListeners, listener);
    }

    /** What the player spends knowledge and power on; empty unless the server negotiated SPENDING. */
    public List<SpendView> spends() {
        return spends;
    }

    /** Whether the server lets inactive skills show; true unless a SPENDING server said otherwise. */
    public boolean inactiveSkillsAllowed() {
        return inactiveSkillsAllowed;
    }

    public void acceptSpending(boolean inactiveAllowed, List<SpendView> entries) {
        inactiveSkillsAllowed = inactiveAllowed;
        spends = List.copyOf(entries);
        for (Runnable listener : spendingListeners) {
            listener.run();
        }
    }

    public ClientSubscription onSpending(Runnable listener) {
        return add(spendingListeners, listener);
    }

    /**
     * The names of {@code skillId}'s adaptation groups, in the order of its catalog row groups; empty unless the server
     * negotiated GROUP_LABELS and the skill is grouped.
     */
    public List<String> groupLabels(String skillId) {
        return groupLabels.getOrDefault(skillId, List.of());
    }

    public void acceptGroupLabels(List<GroupLabelView> skills) {
        Map<String, List<String>> indexed = new LinkedHashMap<>();
        for (GroupLabelView skill : skills) {
            indexed.put(skill.skillId(), skill.labels());
        }
        groupLabels = Map.copyOf(indexed);
        for (Runnable listener : groupLabelListeners) {
            listener.run();
        }
    }

    public ClientSubscription onGroupLabels(Runnable listener) {
        return add(groupLabelListeners, listener);
    }

    /**
     * The names adaptations show on their own skill's pages by adaptation id, where they differ from the catalog's;
     * empty unless the server negotiated SHORT_NAMES.
     */
    public Map<String, String> shortNames() {
        return shortNames;
    }

    public void acceptShortNames(List<ShortNameView> names) {
        Map<String, String> indexed = new LinkedHashMap<>();
        for (ShortNameView name : names) {
            indexed.put(name.adaptationId(), name.name());
        }
        shortNames = Map.copyOf(indexed);
        for (Runnable listener : shortNameListeners) {
            listener.run();
        }
    }

    public ClientSubscription onShortNames(Runnable listener) {
        return add(shortNameListeners, listener);
    }

    public ClientSubscription onConnected(Consumer<ServerInfo> listener) {
        return add(connectedListeners, listener);
    }

    public ClientSubscription onDisconnected(Runnable listener) {
        return add(disconnectedListeners, listener);
    }

    public ClientSubscription onCatalog(Consumer<CatalogView> listener) {
        return add(catalogListeners, listener);
    }

    public ClientSubscription onPlayer(Consumer<PlayerView> listener) {
        return add(playerListeners, listener);
    }

    /** The server accepted {@code Hello}; a renegotiation keeps the catalog and player view. */
    public void negotiated(int negotiatedVersion, ServerInfo info) {
        version = negotiatedVersion;
        server = Objects.requireNonNull(info, "info");
        for (Consumer<ServerInfo> listener : connectedListeners) {
            listener.accept(info);
        }
    }

    public void acceptCatalog(CatalogView view) {
        catalog = Objects.requireNonNull(view, "view");
        for (Consumer<CatalogView> listener : catalogListeners) {
            listener.accept(view);
        }
    }

    public void acceptPlayer(PlayerView view) {
        player = Objects.requireNonNull(view, "view");
        for (Consumer<PlayerView> listener : playerListeners) {
            listener.accept(view);
        }
    }

    /** Applies a delta to the current view; false (and nothing changes) before the first full view. */
    public boolean applyDelta(PlayerDelta delta) {
        PlayerView current = player;
        if (current == null) {
            return false;
        }
        acceptPlayer(PlayerViewPatch.apply(current, delta));
        return true;
    }

    public void acceptItems(List<ItemDescriptor> descriptors) {
        items = List.copyOf(descriptors);
    }

    /** Forgets the session; disconnected listeners run only if a session was negotiated. */
    public void reset() {
        boolean wasNegotiated = server != null;
        version = 0;
        server = null;
        catalog = null;
        player = null;
        items = List.of();
        mutateOffers = List.of();
        kept = List.of();
        keptTitle = "";
        tools = List.of();
        recipes = Map.of();
        spread = null;
        xpGuides = Map.of();
        xpGuideTitle = "";
        spends = List.of();
        groupLabels = Map.of();
        shortNames = Map.of();
        inactiveSkillsAllowed = true;
        if (wasNegotiated) {
            for (Runnable listener : disconnectedListeners) {
                listener.run();
            }
        }
    }

    private static <T> ClientSubscription add(List<T> listeners, T listener) {
        T added = Objects.requireNonNull(listener, "listener");
        listeners.add(added);
        return () -> listeners.remove(added);
    }
}
