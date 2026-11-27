package art.arcane.adapt;

import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.ComponentRegistrar;
import art.arcane.adapt.engine.adaptation.BonusBlockBreaks;
import art.arcane.adapt.engine.item.ItemDurability;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.addon.AddonLifecycle;
import art.arcane.adapt.api.AdaptClock;
import art.arcane.adapt.engine.bridge.CoreBridge;
import art.arcane.adapt.engine.attribute.AdaptAttributeService;
import art.arcane.adapt.engine.advancement.AdvancementManager;
import art.arcane.adapt.engine.fx.FxDirector;
import art.arcane.adapt.engine.batch.BuildBatches;
import art.arcane.adapt.engine.item.AdaptItemGuard;
import art.arcane.adapt.engine.mutation.tool.MutationToolGuard;
import art.arcane.adapt.engine.minion.MinionBurden;
import art.arcane.adapt.engine.potion.BrewingManager;
import art.arcane.adapt.engine.tick.Ticker;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerDataPersistenceQueue;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.engine.value.MaterialValue;
import art.arcane.adapt.engine.xp.XpProvenanceListener;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.AssetInstaller;
import art.arcane.adapt.engine.asset.AssetSkills;
import art.arcane.adapt.engine.asset.BuiltinAssets;
import art.arcane.adapt.engine.asset.InstalledBundle;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.HudLink;
import art.arcane.adapt.client.server.panel.ClientAwareMenuHost;
import art.arcane.adapt.engine.gui.ConfigInput;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.LanguageEditorGui;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.util.common.input.MidairJumps;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.engine.adaptation.AdaptationRequirements;
import art.arcane.adapt.engine.protection.ProtectionDispatch;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.engine.ability.internal.AbilityHooksRuntime;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiSessions;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.config.ConfigHotload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The running Adapt instance shared by every platform. A platform builds its {@link HostPlatform}, constructs one
 * core with it, installs its built-in addon, calls {@link #start()} on enable and {@link #stop()} on disable. Core code
 * reaches the running instance through {@link #get()}.
 *
 * <p>Start runs in two halves so a platform can put its own phases between them: {@link #startServices()}
 * (language, models, HUD, SQL, ticker, FX director, persistence queue) and {@link #startRuntime()} (player server,
 * skills, attributes, advancements, core bus listeners). {@link #start()} runs both. Stop mirrors it.
 */
public final class AdaptCore {
  private static final long MINION_SHUTDOWN_TIMEOUT_MS = 5_000L;
  private static volatile AdaptCore current;
  private volatile boolean stopping;

  private final HostPlatform platform;
  private final AdaptEventBus bus;
  private final ComponentRegistrar registrar;
  private volatile Ticker ticker;
  private volatile FxDirector fxDirector;
  private volatile AdaptServer adaptServer;
  private volatile AdvancementManager advancementManager;
  private volatile SQLManager sqlManager;
  private volatile PlayerDataPersistenceQueue playerDataPersistenceQueue;
  private volatile ClientUiService clientUi;
  private volatile AddonLifecycle addons;
  private volatile CoreBridge bridge;
  private volatile boolean languagePending;
  private final AdaptClock systemClock = new AdaptClock() {
    @Override
    public long millis() {
      return System.currentTimeMillis();
    }

    @Override
    public long tick() {
      return platform.host().currentTick();
    }
  };
  private final List<Object> coreListeners = new ArrayList<>();
  private final ConfigHotload hotload = new ConfigHotload();
  private final ConfigInput configInput = new ConfigInput();
  private final AdaptAssetCatalog assets = new AdaptAssetCatalog(
      AssetSkills.of(this::skillRegistry), AdaptLog::error);

  public AdaptCore(HostPlatform platform, AdaptEventBus bus, ComponentRegistrar registrar) {
    Objects.requireNonNull(platform, "platform");
    this.platform = new HostPlatform(platform.host(), platform.scheduler(), platform.fx(), platform.viewers(),
        platform.menus(), platform.permissions(),
        new ProtectionDispatch(platform.protection(), AdaptationRequirements::find), platform.recipes(),
        platform.advancements());
    this.bus = Objects.requireNonNull(bus, "bus");
    this.registrar = Objects.requireNonNull(registrar, "registrar");
  }

  public void start() {
    startServices();
    startRuntime();
  }

  public void stop() {
    stopRuntime();
    stopServices();
  }

  public synchronized void startServices() {
    current = this;
    if (assets.bundles().stream().noneMatch((InstalledBundle bundle) -> Id.ADAPT.equals(bundle.namespace()))) {
      assets.install(BuiltinAssets.bundle(AdaptCore.class.getClassLoader()), Id.ADAPT);
    }
    languagePending = true;
    AdaptHud.start();
    if (!CustomModel.reloadFromDisk()) {
      AdaptLog.warn("Failed to load models config during startup migration.");
    }
    if (!AdaptConfig.get().isCustomModels()) {
      CustomModel.clear();
    }
    AdaptLog.debug("XP Curve: " + AdaptConfig.get().getXpCurve());
    AdaptLog.debug("XP/Level base: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
    AdaptLog.debug("XP/Level multiplier: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier());
    sqlManager = new SQLManager();
    if (AdaptConfig.get().getSql().isEnabled() && !sqlManager.establishConnection()) {
      throw new IllegalStateException("SQL persistence is enabled, but its transactional storage gate failed");
    }
    startTicking();
    playerDataPersistenceQueue = new PlayerDataPersistenceQueue(new PlayerDataPersistenceQueue.Host() {
      @Override
      public SQLManager sqlManager() {
        return sqlManager;
      }

      @Override
      public void fenceLost(UUID playerId, UUID ownerToken, long epoch) {
        AdaptServer server = adaptServer;
        if (server != null) {
          server.onPersistenceFenceLost(playerId, ownerToken, epoch);
        }
      }
    });
  }

  public synchronized void startRuntime() {
    current = this;
    stopping = false;
    startTicking();
    PlayerStateRegistry.start();
    addons = new AddonLifecycle(this);
    bridge = new CoreBridge(this, addons);
    CoreBridge.start(bridge);
    adaptServer = new AdaptServer();
    adaptServer.startRuntime();
    if (languagePending) {
      languagePending = false;
      startLanguage();
    }
    AdaptAttributeService attributes = AdaptAttributeService.get();
    AdaptAttributeService.startRuntime();
    attributes.reconcileOnline();
    advancementManager = new AdvancementManager();
    advancementManager.enable();
    clientUi = new ClientUiService(this);
    registerCoreListener(clientUi);
    ClientHud.install(HudLink.of(clientUi.sessions()));
    if (platform.menus() instanceof ClientAwareMenuHost panelHost) {
      clientUi.panels(panelHost.panels());
    }
    registerCoreListener(new XpProvenanceListener());
    registerCoreListener(new AdaptationHost.PlayerDamageListener());
    registerCoreListener(new BonusBlockBreaks());
    if (!ItemDurability.perStack()) {
      registerCoreListener(new ItemDurability());
    }
    if (!platform.host().supports(EngineFeature.PLAYER_INPUT)) {
      registerCoreListener(MidairJumps.start());
    }
    registerCoreListener(new BrewingManager());
    configInput.start();
    registerCoreListener(configInput);
    if (AdaptPorts.get(LanguageSwitcherPort.class, LanguageSwitcherPort.NONE).usesCoreEditor()) {
      registerCoreListener(new LanguageEditorGui.Input());
    }
    hotload.start();
    registerCoreListener(new AdaptItemGuard());
    registerCoreListener(new MutationToolGuard());
    registerCoreListener(BuildBatches.startRuntime());
    MinionBurden burden = MinionBurden.get();
    MinionBurden.startRuntime();
    burden.reconcileOnline();
    addons.started();
  }

  /**
   * Loads the configured language and checks the language source once the addons are applied: the built-in language
   * files hold the built-in content's messages too, so they are written and merged against the complete catalog.
   */
  private static void startLanguage() {
    AdaptLanguage.initialize();
    AdaptLanguage.start();
    AdaptLanguage.sourceFailure().ifPresent(failure -> AdaptLog.error(
        "Language source is unavailable; code-owned English and installed language files remain active.", failure));
    AdaptLanguage.startUpstreamCheck();
    AdaptLanguage.requestConfiguredLocale();
    AdaptLog.info("Language: " + AdaptLanguage.activeLocale());
  }

  public synchronized void stopRuntime() {
    stopping = true;
    AddonLifecycle lifecycle = addons;
    if (lifecycle != null) {
      phase("addon stopping event", lifecycle::stopping);
    }
    phase("minion registration gate", MinionBurden::beginShutdown);
    phase("build batches", () -> BuildBatches.get().shutdown());
    phase("config hotload", hotload::stop);
    phase("config input", configInput::stop);
    phase("open menus", GuiSessions::clear);
    ClientUiService activeClientUi = clientUi;
    clientUi = null;
    if (activeClientUi != null) {
      phase("client HUD", ClientHud::uninstall);
      phase("client UI", activeClientUi::shutdown);
    }
    Ticker activeTicker = ticker;
    if (activeTicker != null) {
      phase("ticker", activeTicker::shutdown);
    }
    phase("attribute change gate", AdaptAttributeService::beginShutdown);
    phase("mid-air jumps", MidairJumps::stop);
    for (Object listener : List.copyOf(coreListeners)) {
      phase("core listener", () -> registrar.unregister(listener));
    }
    coreListeners.clear();
    AdaptServer server = adaptServer;
    if (server != null) {
      phase("Adapt server", server::unregister);
    }
    phase("components", registrar::unregisterAll);
    phase("ability cost tickets", () -> AbilityHooksRuntime.get().cost().drain(AbilityRefundReason.SERVER_SHUTDOWN));
    phase("player state registry", PlayerStateRegistry::reset);
    AdvancementManager advancements = advancementManager;
    if (advancements != null) {
      phase("advancement manager", advancements::disable);
    }
    phase("attribute service", () -> {
      if (!AdaptAttributeService.shutdown(5_000L)) {
        AdaptLog.warn("Adapt attribute cleanup was incomplete at shutdown.");
      }
    });
    phase("material values", MaterialValue::save);
    phase("custom models", CustomModel::clear);
    phase("minion burden", () -> {
      if (!MinionBurden.shutdown(MINION_SHUTDOWN_TIMEOUT_MS)) {
        AdaptLog.warn("Minion-burden cleanup was incomplete at shutdown.");
      }
    });
    CoreBridge stopped = bridge;
    bridge = null;
    addons = null;
    if (lifecycle != null) {
      phase("addons", lifecycle::stopped);
    }
    if (stopped != null) {
      phase("engine bridge", () -> CoreBridge.stop(stopped));
    }
  }

  public synchronized void stopServices() {
    phase("HUD", AdaptHud::stop);
    phase("language", AdaptLanguage::shutdown);
    PlayerDataPersistenceQueue queue = playerDataPersistenceQueue;
    playerDataPersistenceQueue = null;
    if (queue != null) {
      phase("player data persistence", () -> queue.flushAndShutdown(30_000L));
    }
    SQLManager sql = sqlManager;
    sqlManager = null;
    if (sql != null) {
      phase("SQL", sql::closeConnection);
    }
    if (current == this) {
      current = null;
    }
  }

  private void startTicking() {
    if (ticker != null) {
      return;
    }
    ticker = new Ticker();
    fxDirector = new FxDirector();
    fxDirector.activateRuntime();
  }

  private void registerCoreListener(Object listener) {
    registrar.register(listener);
    coreListeners.add(listener);
  }

  private static void phase(String name, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | LinkageError failure) {
      AdaptLog.warn("Shutdown phase failed (" + name + "): " + failure.getMessage());
      AdaptLog.error(failure);
    }
  }

  /** The running core, or null before start and after stop. */
  public static AdaptCore current() {
    return current;
  }

  /** The running core; throws when Adapt is not running. */
  public static AdaptCore get() {
    AdaptCore running = current;
    if (running == null) {
      throw new IllegalStateException("Adapt is not running");
    }
    return running;
  }

  public static HostPlatform platform() {
    return get().platform;
  }

  /** True from the start of {@link #stopRuntime()} until the runtime starts again. */
  public boolean isStopping() {
    return stopping;
  }

  public HostPlatform getPlatform() {
    return platform;
  }

  /** Wall time and the platform's tick counter, as {@code AdaptApi.clock()} reports them. */
  public AdaptClock clock() {
    return systemClock;
  }

  public AdaptEventBus getBus() {
    return bus;
  }

  public ComponentRegistrar getRegistrar() {
    return registrar;
  }

  public Ticker getTicker() {
    return ticker;
  }

  public FxDirector getFxDirector() {
    return fxDirector;
  }

  public AdaptServer getAdaptServer() {
    return adaptServer;
  }

  /** The native client UI service while the runtime runs, else null. */
  /** Every provider's client art, built-in first; survives a runtime restart. */
  public AssetCatalog assets() {
    return assets;
  }

  /** Where engine code installs and removes asset bundles, such as the addon lifecycle. */
  public AssetInstaller assetInstaller() {
    return assets;
  }

  private SkillRegistry skillRegistry() {
    AdaptServer server = adaptServer;
    return server == null ? null : server.getSkillRegistry();
  }

  /** The addon lifecycle of the running runtime, else null. */
  public AddonLifecycle addons() {
    return addons;
  }

  public ClientUiService getClientUi() {
    return clientUi;
  }

  public AdvancementManager getAdvancementManager() {
    return advancementManager;
  }

  public ConfigHotload getHotload() {
    return hotload;
  }

  public SQLManager getSqlManager() {
    return sqlManager;
  }

  public PlayerDataPersistenceQueue getPlayerDataPersistenceQueue() {
    return playerDataPersistenceQueue;
  }

}
