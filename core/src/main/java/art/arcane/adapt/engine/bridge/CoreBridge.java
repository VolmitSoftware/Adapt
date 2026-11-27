package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.content.ContentVeto;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.AdaptClock;
import art.arcane.adapt.api.AdaptSettings;
import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.internal.AbilityRuntime;
import art.arcane.adapt.api.internal.AdaptationRuntime;
import art.arcane.adapt.api.internal.BuildRuntime;
import art.arcane.adapt.api.internal.CombatRuntime;
import art.arcane.adapt.api.internal.IntegrationRuntime;
import art.arcane.adapt.api.internal.ComponentHooks;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.EventSubscriptions;
import art.arcane.adapt.api.internal.HostKey;
import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.api.internal.FxRuntime;
import art.arcane.adapt.api.internal.HudRuntime;
import art.arcane.adapt.api.internal.InstalledAddon;
import art.arcane.adapt.api.internal.ItemRuntime;
import art.arcane.adapt.api.internal.MutationRuntime;
import art.arcane.adapt.api.internal.ProgressRuntime;
import art.arcane.adapt.api.internal.SkillRuntime;
import art.arcane.adapt.api.internal.TextRuntime;
import art.arcane.adapt.api.internal.ViewerRuntime;
import art.arcane.adapt.api.internal.WorldRuntime;
import art.arcane.adapt.api.player.PlayerDirectory;
import art.arcane.adapt.engine.addon.AddonLifecycle;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.tick.Ticker;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** The running core as the API sees it. One per core runtime start. */
public final class CoreBridge implements EngineBridge {
  private static CoreBridge started;
  private final AdaptCore core;
  private final AddonLifecycle addons;
  private final BridgeServices services;
  private final FxRuntime fx = new FxBridge();
  private final ViewerRuntime viewers = new ViewerBridge();
  private final WorldRuntime world = new WorldBridge();
  private final CombatRuntime combat = new CombatBridge();
  private final IntegrationRuntime integrations = new IntegrationBridge();
  private final BuildRuntime builds = new BuildBridge();
  private final AdaptClock clock = new AdaptClock() {
    @Override
    public long millis() {
      return owner().clock().millis();
    }

    @Override
    public long tick() {
      return owner().clock().tick();
    }

    @Override
    public double loadPercent() {
      Ticker ticker = owner().getTicker();
      return ticker == null ? 0D : ticker.getWindowLoadPercent();
    }
  };
  private final ProgressRuntime progress = new ProgressBridge();
  private final AbilityRuntime abilities = new AbilityBridge();

  public CoreBridge(AdaptCore core, AddonLifecycle addons) {
    this.core = Objects.requireNonNull(core, "core");
    this.addons = Objects.requireNonNull(addons, "addons");
    this.services = new BridgeServices(core);
  }

  /** Installs {@code bridge} as the running core, first removing one whose core never stopped. */
  public static synchronized void start(CoreBridge bridge) {
    CoreBridge abandoned = started;
    if (abandoned != null) {
      AdaptEnvironment.log().warn("A previous Adapt core never stopped; replacing it.");
      Engine.stop(HostKeys.key(), abandoned);
    }
    Engine.start(HostKeys.key(), bridge);
    started = bridge;
  }

  public static synchronized void stop(CoreBridge bridge) {
    Engine.stop(HostKeys.key(), bridge);
    if (started == bridge) {
      started = null;
    }
  }

  private AdaptCore owner() {
    AdaptCore running = AdaptCore.current();
    return running == null ? core : running;
  }

  @Override
  public SkillRuntime attach(SimpleSkill<?> skill, ComponentHooks hooks) {
    return ComponentBuilder.attach(skill, hooks);
  }

  @Override
  public AdaptationRuntime attach(SimpleAdaptation<?> adaptation, ComponentHooks hooks) {
    return ComponentBuilder.attach(adaptation, hooks);
  }

  @Override
  public ContentCatalog content() {
    return addons.catalog();
  }

  @Override
  public PlayerDirectory players() {
    return PlayerApi.directory();
  }

  @Override
  public AdaptPlatform platform() {
    return core.getPlatform().consumer();
  }

  @Override
  public AdaptSettings settings() {
    return services.settings();
  }

  @Override
  public PlatformLog log() {
    return AdaptEnvironment.log();
  }

  /** Reads the current core on each call, so a test that installs a core with a stubbed clock drives it. */
  @Override
  public AdaptClock clock() {
    return clock;
  }

  @Override
  public TextRuntime text() {
    return services.text();
  }

  @Override
  public HudRuntime hud() {
    return services.hud();
  }

  @Override
  public FxRuntime fx() {
    return fx;
  }

  @Override
  public ItemRuntime items() {
    return services.items();
  }

  @Override
  public MutationRuntime mutations() {
    return services.mutations();
  }

  @Override
  public ViewerRuntime viewers() {
    return viewers;
  }

  @Override
  public WorldRuntime world() {
    return world;
  }

  @Override
  public CombatRuntime combat() {
    return combat;
  }

  @Override
  public IntegrationRuntime integrations() {
    return integrations;
  }

  @Override
  public BuildRuntime builds() {
    return builds;
  }

  @Override
  public void addonInstalled(HostKey key, InstalledAddon addon) {
    Engine.requireHost(key);
    addons.install(addon);
  }

  @Override
  public void addonRefreshed(HostKey key, InstalledAddon addon) {
    Engine.requireHost(key);
    addons.refresh(addon);
  }

  @Override
  public void addonUninstalled(HostKey key, InstalledAddon addon) {
    Engine.requireHost(key);
    addons.uninstall(addon);
  }

  @Override
  public ContentVeto veto(HostKey key, InstalledAddon addon, Id content) {
    Engine.requireHost(key);
    return addons.disable(content, addon.namespace());
  }

  @Override
  public void listenerSubscribed(HostKey key, Object listener) {
    Engine.requireHost(key);
    addons.subscribe(listener);
  }

  @Override
  public void listenerUnsubscribed(HostKey key, Object listener) {
    Engine.requireHost(key);
    addons.unsubscribe(listener);
  }

  @Override
  public boolean isStopping() {
    return owner().isStopping();
  }

  @Override
  public void post(AdaptEvent event) {
    EventSubscriptions.requireAddonEvent(event);
    owner().getBus().post(event);
  }

  @Override
  public ProgressRuntime progress() {
    return progress;
  }

  @Override
  public AbilityRuntime abilities() {
    return abilities;
  }
}
