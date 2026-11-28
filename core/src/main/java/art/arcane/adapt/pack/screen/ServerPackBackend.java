package art.arcane.adapt.pack.screen;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.adaptation.AdaptationLevelActions;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.server.AdaptScreens;
import art.arcane.adapt.client.server.ScreenActions;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.RecipeViews;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.client.view.ToolViews;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.client.view.XpGuideViews;
import art.arcane.adapt.engine.gui.MutationGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pack screens' view models and actions on the running core. Views come from the same builders the native client's
 * frames carry, cached per viewer until the catalog revision, language or mutations change; actions run through
 * {@link ScreenActions} and {@link AdaptationLevelActions}, the operations the native client's frames use.
 */
public final class ServerPackBackend implements PackViews, PackActions {
  private final Map<UUID, Catalog> catalogs = new ConcurrentHashMap<>();
  private volatile Recipes recipes;
  private volatile Tools tools;

  /** Forgets what was cached for a player who left. */
  public void forget(UUID playerId) {
    catalogs.remove(playerId);
  }

  @Override
  public CatalogIndex catalog(PlayerRef viewer) {
    SkillRegistry registry = registry();
    AdaptPlayer player = adaptPlayer(viewer);
    if (registry == null || player == null || !player.isRuntimeReady()) {
      return null;
    }
    UUID id = viewer.uuid();
    List<SkillPair> mutations = player.getData().getMutations();
    String locale = AdaptLanguage.locale(id);
    LocalizationSnapshot texts = AdaptLanguage.snapshot(id);
    Catalog cached = catalogs.get(id);
    if (cached != null && cached.revision() == registry.getCatalogRevision() && cached.locale().equals(locale)
        && cached.texts() == texts && cached.mutations().equals(mutations)) {
      return cached.index();
    }
    CatalogView catalog = CatalogViews.build(registry, id, mutations, true);
    Map<String, String> shortNames = new HashMap<>();
    for (ShortNameView shortName : CatalogViews.shortNames(registry, id, mutations)) {
      shortNames.put(shortName.adaptationId(), shortName.name());
    }
    CatalogIndex index = new CatalogIndex(catalog, Map.copyOf(shortNames));
    catalogs.put(id, new Catalog(registry.getCatalogRevision(), locale, texts, List.copyOf(mutations), index));
    return index;
  }

  @Override
  public PlayerView player(PlayerRef viewer) {
    SkillRegistry registry = registry();
    AdaptPlayer player = adaptPlayer(viewer);
    return registry == null || player == null || !player.isRuntimeReady() ? null : PlayerViews.build(registry, player);
  }

  @Override
  public long fingerprint(PlayerRef viewer) {
    AdaptPlayer player = adaptPlayer(viewer);
    return player == null ? 0L : PlayerViews.fingerprint(player.getData());
  }

  @Override
  public List<RecipeView> recipes(PlayerRef viewer, String adaptationId) {
    SkillRegistry registry = registry();
    if (registry == null) {
      return List.of();
    }
    String locale = AdaptLanguage.locale(viewer.uuid());
    Recipes cached = recipes;
    if (cached == null || cached.revision() != registry.getCatalogRevision() || !cached.locale().equals(locale)) {
      cached = new Recipes(registry.getCatalogRevision(), locale, RecipeViews.build(registry, viewer.uuid()));
      recipes = cached;
    }
    List<RecipeView> unlocked = new ArrayList<>();
    for (RecipeView recipe : cached.all()) {
      if (recipe.adaptationId().equals(adaptationId)) {
        unlocked.add(recipe);
      }
    }
    return unlocked;
  }

  @Override
  public XpGuideView guide(PlayerRef viewer, String skillId) {
    SkillRegistry registry = registry();
    Skill<?> skill = registry == null ? null : registry.getSkill(skillId);
    return skill == null ? null : XpGuideViews.guide(registry, skill);
  }

  @Override
  public List<KeptView> kept(PlayerRef viewer) {
    SkillRegistry registry = registry();
    AdaptPlayer player = adaptPlayer(viewer);
    return registry == null || player == null ? List.of() : ScreenActions.kept(registry, viewer, player);
  }

  @Override
  public List<String> groupLabels(PlayerRef viewer, String skillId) {
    SkillRegistry registry = registry();
    if (registry == null) {
      return List.of();
    }
    for (GroupLabelView labels : ScreenActions.groupLabels(registry)) {
      if (labels.skillId().equals(skillId)) {
        return labels.labels();
      }
    }
    return List.of();
  }

  @Override
  public List<String> groupOwners(PlayerRef viewer, String skillId) {
    SkillRegistry registry = registry();
    if (registry == null || !(registry.getSkill(skillId) instanceof MutatedSkill mutated)) {
      return List.of();
    }
    List<Skill<?>> parents = new ArrayList<>(2);
    for (Id parent : List.of(mutated.getPair().lead(), mutated.getPair().partner())) {
      Skill<?> skill = registry.getAnySkill(parent);
      if (skill != null) {
        parents.add(skill);
      }
    }
    List<String> owners = new ArrayList<>();
    for (List<String> row : mutated.rowGroups()) {
      String owner = skillId;
      for (Skill<?> parent : parents) {
        for (Adaptation<?> upgrade : SkillHost.of(parent).getUpgrades()) {
          if (!row.isEmpty() && upgrade.getId().toString().equals(row.get(0))) {
            owner = SkillHost.lineKey(parent);
          }
        }
      }
      owners.add(owner);
    }
    return owners;
  }

  @Override
  public List<ToolView> tools(PlayerRef viewer) {
    SkillRegistry registry = registry();
    if (registry == null) {
      return List.of();
    }
    String locale = AdaptLanguage.locale(viewer.uuid());
    Tools cached = tools;
    if (cached == null || cached.revision() != registry.getCatalogRevision() || !cached.locale().equals(locale)) {
      cached = new Tools(registry.getCatalogRevision(), locale, ToolViews.build(registry, viewer.uuid()));
      tools = cached;
    }
    return cached.all();
  }

  @Override
  public boolean inactiveSkills(PlayerRef viewer) {
    return AdaptConfig.get().getGui().isShowInactiveSkills();
  }

  @Override
  public boolean mutateOffered(PlayerRef viewer, String skillId) {
    SkillRegistry registry = registry();
    return registry != null && ScreenActions.mutateOffered(registry, adaptPlayer(viewer), registry.getSkill(skillId));
  }

  @Override
  public boolean unmutateOffered(PlayerRef viewer, String skillId) {
    SkillRegistry registry = registry();
    return registry != null && MutationGui.offersUnmutate(registry.getSkill(skillId), viewer);
  }

  @Override
  public List<String> mutationLeads(PlayerRef viewer) {
    SkillRegistry registry = registry();
    AdaptPlayer player = adaptPlayer(viewer);
    if (registry == null || player == null) {
      return List.of();
    }
    List<String> leads = new ArrayList<>();
    for (Skill<?> lead : MutationOffers.leads(player.getData(), registry.getSkills(), AdaptConfig.get().getMutations())) {
      leads.add(SkillHost.lineKey(lead));
    }
    return leads;
  }

  @Override
  public MutationSpreadView spread(PlayerRef viewer, String lead, String partner) {
    SkillRegistry registry = registry();
    if (registry == null) {
      return null;
    }
    Skill<?> partnerSkill = partner.isEmpty() ? null : registry.getSkill(partner);
    return ScreenActions.spread(registry, adaptPlayer(viewer), registry.getSkill(lead), partnerSkill);
  }

  @Override
  public Outcome learn(PlayerRef viewer, String adaptationId, int level) {
    Adaptation<?> adaptation = ScreenActions.available(registry(), viewer, adaptationId);
    if (adaptation == null || level < 1 || level > adaptation.getMaxLevel()) {
      return new Outcome(false, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
    }
    AdaptationLevelActions.Outcome outcome = AdaptationLevelActions.learn(adaptation, viewer, level);
    return new Outcome(outcome.changed(), outcome.message());
  }

  @Override
  public Outcome unlearn(PlayerRef viewer, String adaptationId, int level) {
    Adaptation<?> adaptation = ScreenActions.available(registry(), viewer, adaptationId);
    if (adaptation == null || level < 0 || level >= adaptation.getMaxLevel()) {
      return new Outcome(false, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
    }
    AdaptationLevelActions.Outcome outcome = AdaptationLevelActions.unlearn(adaptation, viewer, level);
    return new Outcome(outcome.changed(), outcome.message());
  }

  @Override
  public boolean setPreference(PlayerRef viewer, PreferenceOwner owner, String ownerId, String preferenceId, String value) {
    return ScreenActions.setPreference(registry(), adaptPlayer(viewer), owner, ownerId, preferenceId, value);
  }

  @Override
  public boolean resetPreference(PlayerRef viewer, PreferenceOwner owner, String ownerId) {
    return ScreenActions.resetPreference(registry(), adaptPlayer(viewer), owner, ownerId);
  }

  @Override
  public Mutation mutate(PlayerRef viewer, String lead, String partner) {
    SkillRegistry registry = registry();
    AdaptPlayer player = adaptPlayer(viewer);
    Skill<?> leadSkill = registry == null ? null : registry.getSkill(lead);
    if (player == null || ScreenActions.spread(registry, player, leadSkill, null) == null) {
      return null;
    }
    ScreenActions.MutationOutcome outcome = ScreenActions.mutate(registry, viewer, player, leadSkill, registry.getSkill(partner));
    return new Mutation(outcome.mutated(), outcome.text(), outcome.spread());
  }

  @Override
  public String unmutate(PlayerRef viewer, String skillId) {
    return ScreenActions.unmutate(viewer, adaptPlayer(viewer), skillId);
  }

  @Override
  public void open(PlayerRef viewer, Route route) {
    AdaptScreens.open(viewer, route);
  }

  private static AdaptServer server() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
  }

  private SkillRegistry registry() {
    AdaptServer server = server();
    return server == null ? null : server.getSkillRegistry();
  }

  private AdaptPlayer adaptPlayer(PlayerRef viewer) {
    AdaptServer server = server();
    return server == null || viewer == null ? null : server.getOnlineAdaptPlayer(viewer.uuid());
  }

  private record Catalog(long revision, String locale, LocalizationSnapshot texts, List<SkillPair> mutations,
                         CatalogIndex index) {
  }

  private record Recipes(long revision, String locale, List<RecipeView> all) {
  }

  private record Tools(long revision, String locale, List<ToolView> all) {
  }
}
