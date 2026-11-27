package art.arcane.adapt.client.server;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.engine.gui.MutationGui;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.adaptation.AdaptationLevelActions;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.AssetSubscription;
import art.arcane.adapt.client.link.ClientFrameHandler;
import art.arcane.adapt.client.link.ClientSessions;
import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.server.panel.PanelFrameHandler;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViewDiff;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.client.view.RecipeViews;
import art.arcane.adapt.client.view.ToolViews;
import art.arcane.adapt.client.view.SpendViews;
import art.arcane.adapt.client.view.XpGuideViews;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.screen.PackScreens;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.util.common.input.ReportedJumps;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server side of the native client UI. Owns the {@link ClientSessions} of the running core, keeps every negotiated
 * player's catalog and player view current, and runs their learn, unlearn and preference requests through the same
 * rules as the chest GUI. Platform transports feed inbound bytes to {@link #receive(UUID, byte[])}; quits arrive
 * through the core bus. Players without a negotiated client cost one map lookup per change.
 */
public final class ClientUiService implements ClientFrameHandler {
  private static final PanelFrameHandler NO_PANELS = new PanelFrameHandler() {
    @Override
    public void click(UUID viewer, ClientFrame.PanelClick click) {
    }

    @Override
    public void input(UUID viewer, ClientFrame.PanelInput input) {
    }

    @Override
    public void closed(UUID viewer, ClientFrame.PanelClosed closed) {
    }

    @Override
    public void quit(UUID viewer) {
    }

    @Override
    public void withdrawn(UUID viewer) {
    }
  };

  /** How long permissions, config and region bonuses may go unchecked while the player's own numbers stand still. */
  private static final long ACCESS_REFRESH_MS = 5_000L;

  private final AdaptCore core;
  private final ClientSessions sessions;
  private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
  private volatile PanelFrameHandler panels = NO_PANELS;
  private volatile CachedItems cachedItems;
  private final ClientAssets assets;
  private final AssetSubscription assetChanges;

  public ClientUiService(AdaptCore core) {
    this.core = Objects.requireNonNull(core, "core");
    PlatformHost host = core.getPlatform().host();
    sessions = new ClientSessions(new ServerInfo(host.platformName(), host.minecraftVersion(),
        servedCapabilities(host)), this);
    assets = new ClientAssets(core.assets(), core.assetInstaller());
    assetChanges = core.assets().subscribe((AssetCatalog catalog) -> assetsChanged());
  }

  /** Every capability this server answers; jump reports only where the server receives no player input packet. */
  static int servedCapabilities(PlatformHost host) {
    return host.supports(EngineFeature.PLAYER_INPUT) ? ClientProtocol.ALL & ~ClientProtocol.JUMPS : ClientProtocol.ALL;
  }

  /** The service of the running core, or null when Adapt is not running. */
  public static ClientUiService current() {
    AdaptCore running = AdaptCore.current();
    return running == null ? null : running.getClientUi();
  }

  /**
   * Notes that what the client shows about this player may have changed. Coalesced to one diff per player per tick
   * and skipped at once for players without a negotiated client. An open pack screen redraws the same way.
   */
  public static void changed(AdaptPlayer player) {
    ClientUiService service = current();
    if (service != null && player != null) {
      service.changed(player.getPlayer());
    }
    PackScreens pack = PackScreens.current();
    if (pack != null && player != null) {
      pack.changed(player.getPlayer());
    }
  }

  /**
   * The once-a-second check for changes nothing reports, such as earned xp. It rebuilds the view only when the
   * player's stored numbers moved, the catalog went stale, or the access check is due; an open pack screen redraws
   * when the stored numbers moved. Call on the player's thread.
   */
  public static void refresh(AdaptPlayer player) {
    ClientUiService service = current();
    if (service != null && player != null) {
      service.refresh(player, player.getPlayer());
    }
    PackScreens pack = PackScreens.current();
    if (pack != null && player != null) {
      pack.refresh(player.getPlayer());
    }
  }

  /** True when the player's client negotiated {@code capabilityBit}. */
  public static boolean negotiated(PlayerRef player, int capabilityBit) {
    ClientUiService service = current();
    return service != null && player != null && service.has(player.uuid(), capabilityBit);
  }

  /** Lower-case names of the capability bits in {@code mask}, in bit order. */
  public static List<String> capabilityNames(int mask) {
    List<String> names = new ArrayList<>();
    String[] known = {"screens", "panel", "hud", "toasts", "items", "insight", "jumps", "mutate", "kept", "tools", "mutate_spread", "xp_guide", "spending",
        "unmutate", "skill_toasts", "group_labels", "short_names", "recipes", "assets"};
    for (int bit = 0; bit < known.length; bit++) {
      if ((mask & (1 << bit)) != 0) {
        names.add(known[bit]);
      }
    }
    return List.copyOf(names);
  }

  /** The negotiated sessions, for server features that send their own frames. */
  /** The running service's sessions, or null while no service runs. */
  public static ClientSessions activeSessions() {
    ClientUiService service = current();
    return service == null ? null : service.sessions();
  }

  public ClientSessions sessions() {
    return sessions;
  }

  /** One inbound {@code adapt:ui} payload from a player's connection; any thread. */
  public void receive(UUID playerId, byte[] payload) {
    sessions.receive(playerId, payload);
  }

  public boolean has(UUID playerId, int capabilityBit) {
    return !viewers.isEmpty() && sessions.has(playerId, capabilityBit);
  }

  public Optional<ClientCapability> capability(UUID playerId) {
    return sessions.capability(playerId);
  }

  /** Sends a frame to a negotiated player; false when they have no client or the channel is closed. */
  public boolean send(UUID playerId, ServerFrame frame) {
    return sessions.send(playerId, frame);
  }

  /** Installs the handler that receives panel frames, withdrawals and quits; null removes it. */
  public void panels(PanelFrameHandler handler) {
    panels = handler == null ? NO_PANELS : handler;
  }

  public void shutdown() {
    assetChanges.cancel();
    for (UUID playerId : viewers.keySet()) {
      if (sessions.has(playerId, ClientProtocol.HUD)) {
        sessions.send(playerId, new ServerFrame.HudClearAll());
      }
      sessions.quit(playerId);
    }
    viewers.clear();
    panels = NO_PANELS;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    sessions.quit(playerId);
    panels.quit(playerId);
  }

  @Override
  public void negotiated(UUID playerId, ClientCapability capability) {
    Viewer viewer = new Viewer();
    viewers.put(playerId, viewer);
    ReportedJumps.reporting(playerId, capability.has(ClientProtocol.JUMPS));
    PlayerRef player = core.getPlatform().host().player(playerId);
    if (player != null) {
      schedule(player, viewer, 0);
    }
  }

  @Override
  public void withdrawn(UUID playerId) {
    viewers.remove(playerId);
    ReportedJumps.reporting(playerId, false);
    panels.withdrawn(playerId);
  }

  @Override
  public void handle(UUID playerId, ClientFrame frame) {
    switch (frame) {
      case ClientFrame.PanelClick click -> panels.click(playerId, click);
      case ClientFrame.PanelInput input -> panels.input(playerId, input);
      case ClientFrame.PanelClosed closed -> panels.closed(playerId, closed);
      case ClientFrame.JumpKey jump -> ReportedJumps.report(playerId, jump.down());
      default -> act(playerId, frame);
    }
  }

  private void act(UUID playerId, ClientFrame frame) {
    Viewer viewer = viewers.get(playerId);
    PlayerRef player = viewer == null ? null : core.getPlatform().host().player(playerId);
    if (player == null) {
      return;
    }
    Tasks.runEntity(player, () -> {
      if (viewers.get(playerId) != viewer || !player.isOnline()) {
        return;
      }
      try (LanguageAudience.Scope audience = LanguageAudience.open(playerId)) {
        dispatch(player, viewer, frame);
      }
    });
  }

  private void changed(PlayerRef player) {
    if (player == null || viewers.isEmpty()) {
      return;
    }
    Viewer viewer = viewers.get(player.uuid());
    if (viewer != null) {
      schedule(player, viewer, 1);
    }
  }

  private void refresh(AdaptPlayer adaptPlayer, PlayerRef player) {
    if (player == null || viewers.isEmpty()) {
      return;
    }
    Viewer viewer = viewers.get(player.uuid());
    SkillRegistry registry = registry();
    if (viewer == null || registry == null) {
      return;
    }
    if (viewer.view == null || M.ms() >= viewer.accessDueAt
        || viewer.fingerprint != PlayerViews.fingerprint(adaptPlayer.getData())
        || catalogStale(viewer, registry, player.uuid(), AdaptLanguage.snapshot(player.uuid()),
        adaptPlayer.getData().getMutations())) {
      schedule(player, viewer, 1);
    }
  }

  private static boolean catalogStale(Viewer viewer, SkillRegistry registry, UUID playerId,
                                      LocalizationSnapshot texts, List<SkillPair> mutations) {
    return viewer.catalogRevision != registry.getCatalogRevision()
        || !AdaptLanguage.locale(playerId).equals(viewer.catalogLocale) || viewer.catalogTexts != texts
        || !viewer.catalogMutations.equals(mutations);
  }

  private void schedule(PlayerRef player, Viewer viewer, int delayTicks) {
    if (!viewer.scheduled.compareAndSet(false, true)) {
      return;
    }
    Runnable release = () -> viewer.scheduled.set(false);
    boolean queued = Tasks.runEntity(player, () -> {
      viewer.scheduled.set(false);
      if (viewers.get(player.uuid()) == viewer && player.isOnline()) {
        sync(player, viewer);
      }
    }, delayTicks, release);
    if (!queued) {
      release.run();
    }
  }

  private void dispatch(PlayerRef player, Viewer viewer, ClientFrame frame) {
    switch (frame) {
      case ClientFrame.Learn learn -> learn(player, viewer, learn);
      case ClientFrame.Unlearn unlearn -> unlearn(player, viewer, unlearn);
      case ClientFrame.SetPreference preference -> preference(player, viewer, preference);
      case ClientFrame.ResetPreference reset -> reset(player, viewer, reset);
      case ClientFrame.Open open -> AdaptScreens.open(player, open.route());
      case ClientFrame.Mutate mutate -> mutate(player, mutate.skillId());
      case ClientFrame.MutationRequest request -> mutation(player, viewer, request);
      case ClientFrame.Unmutate unmutate -> unmutate(player, viewer, unmutate.skillId());
      case ClientFrame.AssetRequest request -> assetRequest(player.uuid(), viewer, request);
      default -> {
      }
    }
  }

  private void learn(PlayerRef player, Viewer viewer, ClientFrame.Learn frame) {
    Adaptation<?> adaptation = actionTarget(player, viewer, frame.adaptationId(), frame.catalogRevision());
    if (adaptation == null) {
      return;
    }
    if (frame.level() < 1 || frame.level() > adaptation.getMaxLevel()) {
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    reply(player, viewer, AdaptationLevelActions.learn(adaptation, player, frame.level()));
  }

  private void unlearn(PlayerRef player, Viewer viewer, ClientFrame.Unlearn frame) {
    Adaptation<?> adaptation = actionTarget(player, viewer, frame.adaptationId(), frame.catalogRevision());
    if (adaptation == null) {
      return;
    }
    if (frame.level() < 0 || frame.level() >= adaptation.getMaxLevel()) {
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    reply(player, viewer, AdaptationLevelActions.unlearn(adaptation, player, frame.level()));
  }

  private Adaptation<?> actionTarget(PlayerRef player, Viewer viewer, String adaptationId, long catalogRevision) {
    SkillRegistry registry = registry();
    if (registry == null) {
      return null;
    }
    if (catalogRevision != registry.getCatalogRevision()) {
      sync(player, viewer);
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_CATALOG_CHANGED));
      return null;
    }
    Adaptation<?> adaptation = ScreenActions.available(registry, player, adaptationId);
    if (adaptation == null) {
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return null;
    }
    return adaptation;
  }

  private void reply(PlayerRef player, Viewer viewer, AdaptationLevelActions.Outcome outcome) {
    sync(player, viewer);
    String message = outcome.message().isEmpty() ? AdaptLanguage.text(GuiMessages.CLIENT_NO_CHANGE) : outcome.message();
    feedback(player, outcome.changed() ? FeedbackKind.SUCCESS : FeedbackKind.FAILURE, message);
  }

  private void preference(PlayerRef player, Viewer viewer, ClientFrame.SetPreference frame) {
    boolean accepted = ScreenActions.setPreference(registry(), adaptPlayer(player), frame.owner(), frame.ownerId(),
        frame.preferenceId(), frame.value());
    preferenceReply(player, viewer, accepted);
  }

  private void reset(PlayerRef player, Viewer viewer, ClientFrame.ResetPreference frame) {
    boolean accepted = ScreenActions.resetPreference(registry(), adaptPlayer(player), frame.owner(), frame.ownerId());
    preferenceReply(player, viewer, accepted);
  }

  private void preferenceReply(PlayerRef player, Viewer viewer, boolean accepted) {
    sync(player, viewer);
    TextKey text = accepted ? GuiMessages.CLIENT_PREFERENCE_SAVED : GuiMessages.CLIENT_PREFERENCE_DENIED;
    feedback(player, accepted ? FeedbackKind.SUCCESS : FeedbackKind.FAILURE, AdaptLanguage.text(text));
  }

  private void sync(PlayerRef player, Viewer viewer) {
    if (!sessions.ready(player.uuid())) {
      return;
    }
    SkillRegistry registry = registry();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (registry == null || adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      return;
    }
    UUID playerId = player.uuid();
    LocalizationSnapshot texts = AdaptLanguage.snapshot(playerId);
    List<SkillPair> mutations = adaptPlayer.getData().getMutations();
    if (catalogStale(viewer, registry, playerId, texts, mutations)) {
      CatalogView catalog = CatalogViews.build(registry, playerId, mutations, sessions.has(playerId, ClientProtocol.KEPT));
      if (!sessions.send(playerId, new ServerFrame.Catalog(catalog))) {
        return;
      }
      viewer.catalogRevision = catalog.revision();
      viewer.catalogLocale = catalog.locale();
      viewer.catalogTexts = texts;
      viewer.catalogMutations = mutations;
      viewer.view = null;
      viewer.guidesDue = true;
      viewer.labelsDue = true;
      viewer.shortNamesDue = true;
    }
    viewer.fingerprint = PlayerViews.fingerprint(adaptPlayer.getData());
    viewer.accessDueAt = M.ms() + ACCESS_REFRESH_MS;
    PlayerView view = PlayerViews.build(registry, adaptPlayer);
    PlayerView previous = viewer.view;
    if (previous == null) {
      if (!sessions.send(playerId, new ServerFrame.Player(view))) {
        return;
      }
      viewer.view = view;
    } else {
      PlayerDelta delta = PlayerViewDiff.diff(previous, view);
      if (!delta.isEmpty() && sessions.send(playerId, new ServerFrame.PlayerUpdate(delta))) {
        viewer.view = view;
      }
    }
    items(playerId, viewer, registry, texts);
    mutateOffers(playerId, viewer, registry, adaptPlayer);
    mutationKept(player, adaptPlayer, viewer, registry, mutations);
    mutationTools(playerId, viewer, registry, texts);
    recipes(playerId, viewer, registry, texts);
    xpGuides(playerId, viewer, registry, mutations);
    groupLabels(playerId, viewer, registry);
    shortNames(playerId, viewer, registry, mutations);
    spending(playerId, viewer, registry, adaptPlayer);
    assetManifest(playerId, viewer, registry);
  }

  /** Sends the art manifest when the client negotiated ASSETS and the catalog or the registered skills changed. */
  private void assetManifest(UUID playerId, Viewer viewer, SkillRegistry registry) {
    if (!sessions.has(playerId, ClientProtocol.ASSETS)) {
      return;
    }
    ClientAssets.Manifest manifest = assets.manifest(registry);
    if (manifest == viewer.assets) {
      return;
    }
    if (sessions.send(playerId, manifest.frame())) {
      viewer.assetsSent.clear();
      viewer.assets = manifest;
    }
  }

  /** Sends each requested file of the client's manifest, each at most once per manifest; unknown ids are ignored. */
  private void assetRequest(UUID playerId, Viewer viewer, ClientFrame.AssetRequest request) {
    ClientAssets.Manifest manifest = viewer.assets;
    if (manifest == null || !sessions.has(playerId, ClientProtocol.ASSETS)) {
      return;
    }
    for (String resource : request.resources()) {
      ClientAssets.File file = manifest.files().get(resource);
      if (file != null && viewer.assetsSent.add(resource)
          && !sessions.send(playerId, new ServerFrame.Asset(resource, file.hash(), file.bytes()))) {
        return;
      }
    }
  }

  /** The catalog changed: every client that takes art gets the new manifest on its next sync. */
  private void assetsChanged() {
    for (Map.Entry<UUID, Viewer> entry : viewers.entrySet()) {
      PlayerRef player = sessions.has(entry.getKey(), ClientProtocol.ASSETS)
          ? core.getPlatform().host().player(entry.getKey()) : null;
      if (player != null) {
        schedule(player, entry.getValue(), 1);
      }
    }
  }

  /** Sends what the player spends knowledge and power on, and the inactive skills policy, when either changed. */
  private void spending(UUID playerId, Viewer viewer, SkillRegistry registry, AdaptPlayer adaptPlayer) {
    if (!sessions.has(playerId, ClientProtocol.SPENDING)) {
      return;
    }
    ServerFrame.Spending spending = new ServerFrame.Spending(AdaptConfig.get().getGui().isShowInactiveSkills(),
        SpendViews.build(registry, adaptPlayer));
    if (spending.equals(viewer.spending)) {
      return;
    }
    if (sessions.send(playerId, spending)) {
      viewer.spending = spending;
    }
  }

  /** Sends the mutation tools and their recipes when the client negotiated TOOLS and the catalog or language changed. */
  private void mutationTools(UUID playerId, Viewer viewer, SkillRegistry registry, LocalizationSnapshot texts) {
    if (!sessions.has(playerId, ClientProtocol.TOOLS)) {
      return;
    }
    long revision = registry.getCatalogRevision();
    String locale = AdaptLanguage.locale(playerId);
    if (revision == viewer.toolsRevision && locale.equals(viewer.toolsLocale) && texts == viewer.toolsTexts) {
      return;
    }
    if (sessions.send(playerId, new ServerFrame.MutationTools(ToolViews.build(registry, playerId)))) {
      viewer.toolsRevision = revision;
      viewer.toolsLocale = locale;
      viewer.toolsTexts = texts;
    }
  }

  /** Sends the recipes every adaptation unlocks when the client negotiated RECIPES and the catalog or language changed. */
  private void recipes(UUID playerId, Viewer viewer, SkillRegistry registry, LocalizationSnapshot texts) {
    if (!sessions.has(playerId, ClientProtocol.RECIPES)) {
      return;
    }
    long revision = registry.getCatalogRevision();
    String locale = AdaptLanguage.locale(playerId);
    if (revision == viewer.recipesRevision && locale.equals(viewer.recipesLocale) && texts == viewer.recipesTexts) {
      return;
    }
    if (sessions.send(playerId, new ServerFrame.AdaptationRecipes(RecipeViews.build(registry, playerId)))) {
      viewer.recipesRevision = revision;
      viewer.recipesLocale = locale;
      viewer.recipesTexts = texts;
    }
  }

  /** Sends how each skill earns XP with every new catalog, when the client negotiated XP_GUIDE. */
  private void xpGuides(UUID playerId, Viewer viewer, SkillRegistry registry, List<SkillPair> mutations) {
    if (!viewer.guidesDue || !sessions.has(playerId, ClientProtocol.XP_GUIDE)) {
      return;
    }
    String title = LanguageAudience.call(playerId, XpGuideViews::title);
    if (sessions.send(playerId, new ServerFrame.XpGuides(title, XpGuideViews.build(registry, playerId, mutations)))) {
      viewer.guidesDue = false;
    }
  }

  /**
   * Sends the names of every grouped skill's adaptation groups with every new catalog, when the client negotiated them
   * and the server has a grouped skill.
   */
  private void groupLabels(UUID playerId, Viewer viewer, SkillRegistry registry) {
    if (!viewer.labelsDue || !sessions.has(playerId, ClientProtocol.GROUP_LABELS)) {
      return;
    }
    List<GroupLabelView> skills = LanguageAudience.call(playerId, () -> ScreenActions.groupLabels(registry));
    if (skills.isEmpty() || sessions.send(playerId, new ServerFrame.GroupLabels(skills))) {
      viewer.labelsDue = false;
    }
  }

  /** Sends the names adaptations show on their own skill's pages with every new catalog, when the client negotiated them. */
  private void shortNames(UUID playerId, Viewer viewer, SkillRegistry registry, List<SkillPair> mutations) {
    if (!viewer.shortNamesDue || !sessions.has(playerId, ClientProtocol.SHORT_NAMES)) {
      return;
    }
    if (sessions.send(playerId, new ServerFrame.ShortNames(CatalogViews.shortNames(registry, playerId, mutations)))) {
      viewer.shortNamesDue = false;
    }
  }

  /**
   * Sends the originals each of the player's mutated skills keeps, and the originals each held upgrade lends a level
   * while their own code runs, when the client negotiated KEPT and they changed.
   */
  private void mutationKept(PlayerRef player, AdaptPlayer adaptPlayer, Viewer viewer, SkillRegistry registry,
                            List<SkillPair> mutations) {
    UUID playerId = player.uuid();
    if (!sessions.has(playerId, ClientProtocol.KEPT)) {
      return;
    }
    List<KeptView> kept = ScreenActions.kept(registry, player, adaptPlayer);
    String title = LanguageAudience.call(playerId, () -> AdaptLanguage.text(MutatedMessages.KEPT));
    if (kept.equals(viewer.kept) && (kept.isEmpty() || title.equals(viewer.keptTitle))) {
      return;
    }
    if (sessions.send(playerId, new ServerFrame.MutationKept(title, kept))) {
      viewer.kept = List.copyOf(kept);
      viewer.keptTitle = title;
    }
  }

  /** Sends the skills whose page offers Mutate when the client negotiated MUTATE and the list changed. */
  private void mutateOffers(UUID playerId, Viewer viewer, SkillRegistry registry, AdaptPlayer adaptPlayer) {
    if (!sessions.has(playerId, ClientProtocol.MUTATE)) {
      return;
    }
    List<String> offers = ScreenActions.mutateOffers(registry, adaptPlayer);
    if (offers.equals(viewer.mutateOffers)) {
      return;
    }
    if (sessions.send(playerId, new ServerFrame.MutateOffers(offers))) {
      viewer.mutateOffers = List.copyOf(offers);
    }
  }

  /** Opens the partner picker for a client's Mutate button, when that skill's page offers it. */
  private void mutate(PlayerRef player, String skillId) {
    SkillRegistry registry = registry();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    Skill<?> skill = registry == null ? null : registry.getSkill(skillId);
    if (!sessions.has(player.uuid(), ClientProtocol.MUTATE) || !ScreenActions.mutateOffered(registry, adaptPlayer, skill)) {
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    MutationGui.openPartners(player, skill);
  }

  /**
   * Answers the spread: the preview of the lead with the partner, or on confirm the merge with every rule checked
   * again, the spread's answer naming the new skill once it went through, and the result as feedback.
   */
  private void mutation(PlayerRef player, Viewer viewer, ClientFrame.MutationRequest request) {
    SkillRegistry registry = registry();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    Skill<?> lead = registry == null ? null : registry.getSkill(request.lead());
    if (!sessions.has(player.uuid(), ClientProtocol.MUTATE_SPREAD) || lead == null || lead instanceof MutatedSkill
        || adaptPlayer == null) {
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    Skill<?> partner = request.partner().isEmpty() ? null : registry.getSkill(request.partner());
    if (!request.confirm()) {
      sessions.send(player.uuid(), new ServerFrame.MutationSpread(ScreenActions.spread(registry, adaptPlayer, lead, partner)));
      return;
    }
    ScreenActions.MutationOutcome outcome = ScreenActions.mutate(registry, player, adaptPlayer, lead, partner);
    sync(player, viewer);
    sessions.send(player.uuid(), new ServerFrame.MutationSpread(outcome.spread()));
    feedback(player, outcome.mutated() ? FeedbackKind.SUCCESS : FeedbackKind.FAILURE, outcome.text());
  }

  /**
   * Undoes the mutation a client's Unmutate names, for a player in debug mode or with the admin permission, then sends
   * the parents back and the result as feedback.
   */
  private void unmutate(PlayerRef player, Viewer viewer, String skillId) {
    String text = sessions.has(player.uuid(), ClientProtocol.UNMUTATE)
        ? ScreenActions.unmutate(player, adaptPlayer(player), skillId) : null;
    if (text == null) {
      feedback(player, FeedbackKind.FAILURE, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    sync(player, viewer);
    feedback(player, FeedbackKind.SUCCESS, text);
  }

  /** Sends the recipe viewer items when the client negotiated ITEMS and the list changed since its last copy. */
  private void items(UUID playerId, Viewer viewer, SkillRegistry registry, LocalizationSnapshot texts) {
    if (!sessions.has(playerId, ClientProtocol.ITEMS)) {
      return;
    }
    CustomItemCatalog.Key key = CustomItemCatalog.key(registry, AdaptLanguage.locale(playerId), texts);
    if (key.equals(viewer.itemsKey)) {
      return;
    }
    for (ServerFrame.CustomItems frame : itemFrames(key, playerId, registry)) {
      if (!sessions.send(playerId, frame)) {
        return;
      }
    }
    viewer.itemsKey = key;
  }

  private List<ServerFrame.CustomItems> itemFrames(CustomItemCatalog.Key key, UUID playerId, SkillRegistry registry) {
    CachedItems cached = cachedItems;
    if (cached != null && cached.key().equals(key)) {
      return cached.frames();
    }
    List<ServerFrame.CustomItems> frames;
    try (LanguageAudience.Scope audience = LanguageAudience.open(playerId)) {
      frames = CustomItemCatalog.frames(CustomItemCatalog.build(registry));
    }
    cachedItems = new CachedItems(key, frames);
    return frames;
  }

  private void feedback(PlayerRef player, FeedbackKind kind, String text) {
    sessions.send(player.uuid(), new ServerFrame.Feedback(kind, text));
  }

  private SkillRegistry registry() {
    AdaptServer server = core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry();
  }

  private AdaptPlayer adaptPlayer(PlayerRef player) {
    AdaptServer server = core.getAdaptServer();
    return server == null ? null : server.getOnlineAdaptPlayer(player.uuid());
  }

  static Adaptation<?> adaptation(SkillRegistry registry, String adaptationId) {
    return registry.getAdaptation(Id.tryParse(adaptationId));
  }

  private record CachedItems(CustomItemCatalog.Key key, List<ServerFrame.CustomItems> frames) {
  }

  private static final class Viewer {
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile CustomItemCatalog.Key itemsKey;
    private volatile long catalogRevision = Long.MIN_VALUE;
    private volatile String catalogLocale = "";
    private volatile LocalizationSnapshot catalogTexts;
    private volatile List<SkillPair> catalogMutations = List.of();
    private volatile List<String> mutateOffers = List.of();
    private volatile List<KeptView> kept = List.of();
    private volatile String keptTitle = "";
    private volatile long toolsRevision = Long.MIN_VALUE;
    private volatile String toolsLocale = "";
    private volatile LocalizationSnapshot toolsTexts;
    private volatile long recipesRevision = Long.MIN_VALUE;
    private volatile String recipesLocale = "";
    private volatile LocalizationSnapshot recipesTexts;
    private volatile boolean guidesDue;
    private volatile boolean labelsDue;
    private volatile boolean shortNamesDue;
    private volatile ServerFrame.Spending spending;
    private volatile PlayerView view;
    private volatile long fingerprint;
    private volatile long accessDueAt;
    private volatile ClientAssets.Manifest assets;
    private final Set<String> assetsSent = ConcurrentHashMap.newKeySet();
  }
}
