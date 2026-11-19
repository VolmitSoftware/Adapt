package art.arcane.adapt;

import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.item.ItemDurability;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.api.fx.FxDirector;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemGuard;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.potion.BrewingManager;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.Ticker;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerDataPersistenceQueue;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.value.MaterialValue;
import art.arcane.adapt.api.xp.XpProvenanceListener;
import art.arcane.adapt.content.adaptation.architect.ArchitectElevator;
import art.arcane.adapt.content.gui.ConfigInput;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptLanguageSwitcher;
import art.arcane.adapt.localization.LanguageEditorGui;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.util.common.input.MidairJumps;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.util.common.inventorygui.GuiSessions;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.config.ConfigHotload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The running Adapt instance shared by every platform. A platform builds its {@link AdaptPlatform}, constructs one
 * core with it and the skill types it ships, calls {@link #start()} on enable and {@link #stop()} on disable. Core code
 * reaches the running instance through {@link #get()}.
 *
 * <p>Start runs in two halves so a platform can put its own phases between them: {@link #startServices()}
 * (language, models, HUD, SQL, ticker, FX director, persistence queue) and {@link #startRuntime()} (player server,
 * skills, attributes, advancements, core bus listeners). {@link #start()} runs both. Stop mirrors it.
 */
public final class AdaptCore {
  private static final long GOLEM_SHUTDOWN_TIMEOUT_MS = 5_000L;
  private static volatile AdaptCore current;
  private volatile boolean stopping;

  private final AdaptPlatform platform;
  private final AdaptEventBus bus;
  private final List<Class<? extends Skill<?>>> skillTypes;
  private final ComponentRegistrar registrar;
  private volatile Ticker ticker;
  private volatile FxDirector fxDirector;
  private volatile AdaptServer adaptServer;
  private volatile AdvancementManager advancementManager;
  private volatile SQLManager sqlManager;
  private volatile PlayerDataPersistenceQueue playerDataPersistenceQueue;
  private final List<Object> coreListeners = new ArrayList<>();
  private final ConfigHotload hotload = new ConfigHotload();
  private final ConfigInput configInput = new ConfigInput();

  public AdaptCore(AdaptPlatform platform, AdaptEventBus bus, ComponentRegistrar registrar,
                   List<Class<? extends Skill<?>>> skillTypes) {
    this.platform = Objects.requireNonNull(platform, "platform");
    this.bus = Objects.requireNonNull(bus, "bus");
    this.registrar = Objects.requireNonNull(registrar, "registrar");
    this.skillTypes = List.copyOf(Objects.requireNonNull(skillTypes, "skillTypes"));
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
    AdaptLanguage.initialize();
    AdaptLanguage.start();
    AdaptLanguage.sourceFailure().ifPresent(failure -> AdaptLog.error(
        "Language source is unavailable; code-owned English and installed language files remain active.", failure));
    AdaptLanguage.startUpstreamCheck();
    AdaptLanguage.requestConfiguredLocale();
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
    AdaptLog.info("Language: " + AdaptLanguage.activeLocale());
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
    adaptServer = new AdaptServer();
    adaptServer.startRuntime();
    AdaptAttributeService attributes = AdaptAttributeService.get();
    AdaptAttributeService.startRuntime();
    attributes.reconcileOnline();
    advancementManager = new AdvancementManager();
    advancementManager.enable();
    registerCoreListener(new ArchitectElevator.BlockGuard());
    registerCoreListener(new XpProvenanceListener());
    registerCoreListener(new SimpleAdaptation.PlayerDamageListener());
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
    if (AdaptPorts.get(LanguageSwitcherPort.class, LanguageSwitcherPort.NONE) instanceof AdaptLanguageSwitcher) {
      registerCoreListener(new LanguageEditorGui.Input());
    }
    hotload.start();
    registerCoreListener(new GolemItemGuard(GolemItems.recipeBook()));
    registerCoreListener(GolemItems.craftCounter());
    MinionBurden burden = MinionBurden.get();
    MinionBurden.startRuntime();
    burden.reconcileOnline();
    GolemancyRuntime.startRuntime();
  }

  public synchronized void stopRuntime() {
    stopping = true;
    phase("minion registration gate", MinionBurden::beginShutdown);
    phase("golemancy runtime", () -> {
      if (!GolemancyRuntime.shutdown(GOLEM_SHUTDOWN_TIMEOUT_MS)) {
        AdaptLog.warn("Bound golem saving was incomplete at shutdown.");
      }
    });
    phase("config hotload", hotload::stop);
    phase("config input", configInput::stop);
    phase("open menus", GuiSessions::clear);
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
      if (!MinionBurden.shutdown(GOLEM_SHUTDOWN_TIMEOUT_MS)) {
        AdaptLog.warn("Minion-burden cleanup was incomplete at shutdown.");
      }
    });
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

  public static AdaptPlatform platform() {
    return get().platform;
  }

  /** True from the start of {@link #stopRuntime()} until the runtime starts again. */
  public boolean isStopping() {
    return stopping;
  }

  public AdaptPlatform getPlatform() {
    return platform;
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

  /** Every skill type this platform ships, in registration order. */
  public List<Class<? extends Skill<?>>> getSkillTypes() {
    return skillTypes;
  }
}
