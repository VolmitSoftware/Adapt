package art.arcane.adapt.client.server;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLevelActions;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
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
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViewDiff;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
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

  public ClientUiService(AdaptCore core) {
    this.core = Objects.requireNonNull(core, "core");
    PlatformHost host = core.getPlatform().host();
    sessions = new ClientSessions(new ServerInfo(host.platformName(), host.minecraftVersion(),
        servedCapabilities(host)), this);
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
   * and skipped at once for players without a negotiated client.
   */
  public static void changed(AdaptPlayer player) {
    ClientUiService service = current();
    if (service != null && player != null) {
      service.changed(player.getPlayer());
    }
  }

  /**
   * The once-a-second check for changes nothing reports, such as earned xp. It rebuilds the view only when the
   * player's stored numbers moved, the catalog went stale, or the access check is due. Call on the player's thread.
   */
  public static void refresh(AdaptPlayer player) {
    ClientUiService service = current();
    if (service != null && player != null) {
      service.refresh(player, player.getPlayer());
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
    String[] known = {"screens", "panel", "hud", "toasts", "items", "insight", "jumps"};
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
        || catalogStale(viewer, registry, player.uuid(), AdaptLanguage.snapshot(player.uuid()))) {
      schedule(player, viewer, 1);
    }
  }

  private static boolean catalogStale(Viewer viewer, SkillRegistry registry, UUID playerId,
                                      LocalizationSnapshot texts) {
    return viewer.catalogRevision != registry.getCatalogRevision()
        || !AdaptLanguage.locale(playerId).equals(viewer.catalogLocale) || viewer.catalogTexts != texts;
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
    Adaptation<?> adaptation = adaptation(registry, adaptationId);
    if (adaptation == null || !AdaptationLevelActions.available(adaptation, player)) {
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
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    boolean accepted = adaptPlayer != null && registry != null && switch (frame.owner()) {
      case SKILL -> setSkillPreference(registry.getSkill(frame.ownerId()), adaptPlayer, frame.preferenceId(),
          frame.value());
      case ADAPTATION -> setAdaptationPreference(adaptation(registry, frame.ownerId()), adaptPlayer,
          frame.preferenceId(), frame.value());
    };
    preferenceReply(player, viewer, accepted);
  }

  private void reset(PlayerRef player, Viewer viewer, ClientFrame.ResetPreference frame) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    boolean accepted = adaptPlayer != null && registry != null && switch (frame.owner()) {
      case SKILL -> {
        Skill<?> skill = registry.getSkill(frame.ownerId());
        yield skill != null && PlayerPreferences.resetSkill(skill, adaptPlayer);
      }
      case ADAPTATION -> {
        Adaptation<?> adaptation = adaptation(registry, frame.ownerId());
        yield adaptation != null && PlayerPreferences.reset(adaptation, adaptPlayer);
      }
    };
    preferenceReply(player, viewer, accepted);
  }

  private void preferenceReply(PlayerRef player, Viewer viewer, boolean accepted) {
    sync(player, viewer);
    TextKey text = accepted ? GuiMessages.CLIENT_PREFERENCE_SAVED : GuiMessages.CLIENT_PREFERENCE_DENIED;
    feedback(player, accepted ? FeedbackKind.SUCCESS : FeedbackKind.FAILURE, AdaptLanguage.text(text));
  }

  private static boolean setSkillPreference(Skill<?> skill, AdaptPlayer player, String preferenceId, String value) {
    if (skill == null || !CommonPreferences.SKILL_ENABLED.id().equals(preferenceId)) {
      return false;
    }
    CommonPreferences.Toggle toggle = CommonPreferences.SKILL_ENABLED.parse(value);
    return toggle != null && PlayerPreferences.setSkillEnabled(skill, player, toggle);
  }

  private static boolean setAdaptationPreference(Adaptation<?> adaptation, AdaptPlayer player, String preferenceId,
                                                 String value) {
    if (adaptation == null) {
      return false;
    }
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      if (preference.id().equals(preferenceId)) {
        return adaptation.isPlayerPreferenceVisible(player, preference)
            && setChoice(adaptation, player, preference, value);
      }
    }
    return false;
  }

  private static <E extends Enum<E>> boolean setChoice(Adaptation<?> adaptation, AdaptPlayer player,
                                                       PlayerPreference<E> preference, String value) {
    E choice = preference.parse(value);
    return choice != null && PlayerPreferences.set(adaptation, player, preference, choice);
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
    if (catalogStale(viewer, registry, playerId, texts)) {
      CatalogView catalog = CatalogViews.build(registry, playerId);
      if (!sessions.send(playerId, new ServerFrame.Catalog(catalog))) {
        return;
      }
      viewer.catalogRevision = catalog.revision();
      viewer.catalogLocale = catalog.locale();
      viewer.catalogTexts = texts;
      viewer.view = null;
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
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.getName().equals(adaptationId)) {
          return adaptation;
        }
      }
    }
    return null;
  }

  private record CachedItems(CustomItemCatalog.Key key, List<ServerFrame.CustomItems> frames) {
  }

  private static final class Viewer {
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile CustomItemCatalog.Key itemsKey;
    private volatile long catalogRevision = Long.MIN_VALUE;
    private volatile String catalogLocale = "";
    private volatile LocalizationSnapshot catalogTexts;
    private volatile PlayerView view;
    private volatile long fingerprint;
    private volatile long accessDueAt;
  }
}
