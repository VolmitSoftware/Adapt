/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.bukkit.feature.BukkitFeatures;
import art.arcane.adapt.bukkit.feature.menu.MenuCursor;
import art.arcane.adapt.bukkit.platform.AddonProviders;
import art.arcane.adapt.bukkit.platform.BukkitLog;
import art.arcane.adapt.bukkit.platform.BukkitNatives;
import art.arcane.adapt.bukkit.platform.BukkitPlatform;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitSupportedVersions;
import art.arcane.adapt.bukkit.platform.data.BlockDataListener;
import art.arcane.adapt.bukkit.port.BukkitPorts;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.content.protector.BukkitProtector;
import art.arcane.adapt.content.protector.ChestProtectProtector;
import art.arcane.adapt.content.protector.FactionsClaimProtector;
import art.arcane.adapt.content.protector.GriefDefenderProtector;
import art.arcane.adapt.content.protector.GriefPreventionProtector;
import art.arcane.adapt.content.protector.LocketteProProtector;
import art.arcane.adapt.content.protector.ResidenceProtector;
import art.arcane.adapt.content.protector.WorldGuardFlags;
import art.arcane.adapt.content.protector.WorldGuardProtector;
import art.arcane.adapt.content.protector.WorldGuardRegionPolicySource;
import art.arcane.adapt.engine.AdaptPermissionRegistrar;
import art.arcane.adapt.engine.ComponentRegistrar;
import art.arcane.adapt.engine.advancement.AdvancementManager;
import art.arcane.adapt.engine.data.WorldData;
import art.arcane.adapt.engine.data.WorldDataLoader;
import art.arcane.adapt.engine.fx.FxDirector;
import art.arcane.adapt.engine.fx.ViewerDisplayDirector;
import art.arcane.adapt.engine.fx.ViewerGlowCoordinator;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.api.projectile.ProjectileReplacements;
import art.arcane.adapt.engine.protection.RegionPolicyService;
import art.arcane.adapt.engine.tick.Ticker;
import art.arcane.adapt.engine.version.Version;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerDataPersistenceQueue;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.papi.AdaptPlaceholderInstaller;
import art.arcane.adapt.papi.AdaptPlaceholders;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.plugin.VolmitPlugin;
import art.arcane.adapt.util.common.plugin.VolmitSender;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.project.redis.RedisSync;
import art.arcane.adapt.util.secret.SecretSplash;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.integration.VaultEconomy;
import art.arcane.volmlib.nativelib.entity.EntityGlow;
import art.arcane.volmlib.nativelib.entity.EntityGlowAccess;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.io.JarScanner;
import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.plugin.SplashScreenSupport;
import art.arcane.volmlib.util.update.BukkitUpdateService;
import de.crazydev22.platformutils.AudienceProvider;
import de.crazydev22.platformutils.Platform;
import de.crazydev22.platformutils.PlatformUtils;
import de.slikey.effectlib.EffectManager;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static art.arcane.adapt.util.director.context.AdaptationListingHandler.initializeAdaptationListings;

public class Adapt extends VolmitPlugin implements ReloadAware {
  private static final long STARTUP_SLOW_PHASE_MS = 1500L;
  private static final long SHUTDOWN_CLEANUP_TIMEOUT_MS = 5_000L;
  private static final int BSTATS_PLUGIN_ID = 24221;
  private static final boolean SLIMJAR_DEBUG = Boolean.getBoolean("adapt.debug-slimjar");
  private static final Logger FALLBACK_LOGGER = Logger.getLogger("Adapt");
  private static final String LOG_DISCRIMINATOR = ComponentLog.discriminator("Adapt", "&4");
  private static final Object GLOWING_ENTITIES_LOCK = new Object();
  public static Adapt instance;
  public static Platform platform;
  public static AudienceProvider audiences;
  private static VolmitSender sender;

  static {
    AdaptEnvironment.install(new BukkitLog(), Adapt::dataDirectory);
    AdaptEnvironment.bindConfig(new BukkitAdaptConfig());
  }

  private static Path dataDirectory() {
    Adapt current = instance;
    File folder = current == null ? null : current.getDataFolder();
    return folder == null ? null : folder.toPath();
  }

  private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);
  public final EffectManager adaptEffectManager;
  private final KList<Runnable> postShutdown = new KList<>();
  private KMap<Class<? extends AdaptService>, AdaptService> services;
  @Getter
  private EntityGlow glowingEntities;
  @Getter
  private ViewerGlowCoordinator viewerGlowCoordinator;
  @Getter
  private volatile AdaptCore core;
  private final List<BukkitProtector> protectors = new ArrayList<>();
  @Getter
  private RedisSync redisSync;
  @Getter
  private VaultEconomy vaultEconomy;
  private volatile PlaceholderRegistration papiRegistration;
  // AdaptMetrics owns all bstats types; never reference them from this class (slimjar link trap)
  private AdaptMetrics metrics;
  private BukkitDebugDump debugDump;
  private volatile BukkitUpdateService updates;


  public Adapt() {
    instance = this;
    long libraryLoadStart = System.currentTimeMillis();
    getLogger().info("Loading libraries...");
    new SpigotApplicationBuilder(this)
        .debug(SLIMJAR_DEBUG)
        .build();
    long libraryLoadElapsed = System.currentTimeMillis() - libraryLoadStart;
    getLogger().info("Libraries loaded (" + libraryLoadElapsed + "ms).");
    adaptEffectManager = new EffectManager(this);
  }

  @SuppressWarnings("unchecked")
  public static <T> T service(Class<T> c) {
    Adapt current = instance;
    return current == null || current.services == null ? null : (T) current.services.get(c);
  }

  public static Object glowingEntitiesLock() {
    return GLOWING_ENTITIES_LOCK;
  }

  private static void runStartupPhaseVoid(String phase, Runnable action) {
    runStartupPhase(phase, () -> {
      action.run();
      return null;
    });
  }

  private static <T> T runStartupPhase(String phase, Supplier<T> action) {
    if (phase == null || phase.isBlank()) {
      return action.get();
    }

    verbose(() -> "Startup phase: " + phase);
    long start = System.currentTimeMillis();
    try {
      return action.get();
    } finally {
      long elapsed = System.currentTimeMillis() - start;
      if (elapsed >= STARTUP_SLOW_PHASE_MS) {
        warn("Startup phase '" + phase + "' took " + elapsed + "ms.");
      } else {
        verbose("Startup phase '" + phase + "' took " + elapsed + "ms.");
      }
    }
  }

  public static VolmitSender getSender() {
    if (sender == null) {
      sender = new VolmitSender(Bukkit.getConsoleSender());
      sender.setTag(instance.getTag());
    }
    return sender;
  }

  public static List<Object> initialize(String s) {
    return initialize(s, null);
  }

  public static KList<Object> initialize(String s, Class<? extends Annotation> slicedClass) {
    JarScanner js = new JarScanner(instance.getJarFile(), s);
    KList<Object> v = new KList<>();
    J.attempt(js::scan);
    for (Class<?> i : js.getClasses()) {
      if (slicedClass == null || i.isAnnotationPresent(slicedClass)) {
        try {
          Adapt.verbose("Found class: " + i.getName());
          v.add(i.getDeclaredConstructor().newInstance());
        } catch (Throwable e) {
          Adapt.verbose("Failed to load class: " + i.getName());
          StringWriter writer = new StringWriter();
          e.printStackTrace(new PrintWriter(writer));
          for (String line : writer.toString().split("\n")) {
            verbose(line);
          }
        }
      }
    }

    return v;
  }

  public Ticker getTicker() {
    AdaptCore running = core;
    return running == null ? null : running.getTicker();
  }

  public FxDirector getFxDirector() {
    AdaptCore running = core;
    return running == null ? null : running.getFxDirector();
  }

  public AdaptServer getAdaptServer() {
    AdaptCore running = core;
    return running == null ? null : running.getAdaptServer();
  }

  public SQLManager getSqlManager() {
    AdaptCore running = core;
    return running == null ? null : running.getSqlManager();
  }

  public AdvancementManager getManager() {
    AdaptCore running = core;
    return running == null ? null : running.getAdvancementManager();
  }

  public PlayerDataPersistenceQueue getPlayerDataPersistenceQueue() {
    AdaptCore running = core;
    return running == null ? null : running.getPlayerDataPersistenceQueue();
  }

  public static void printInformation() {
    debug("XP Curve: " + AdaptConfig.get().getXpCurve());
    debug("XP/Level base: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
    debug("XP/Level multiplier: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier());
    info("Language: " + AdaptLanguage.activeLocale());
  }

  public static void actionbar(Player p, String msg) {
    AdaptHud.actionBar(BukkitRefs.player(p), msg);
  }

  public static void debug(String string) {
    debug(() -> string);
  }

  public static void debug(Supplier<String> messageSupplier) {
    if (AdaptConfig.get().isDebug()) {
      log(Level.INFO, C.DARK_PURPLE + messageSupplier.get(), null);
    }
  }

  public static void warn(String string) {
    log(Level.WARNING, C.YELLOW + string, null);
  }

  public static void warn(String string, Throwable throwable) {
    log(Level.WARNING, C.YELLOW + string, throwable);
  }

  public static void error(String string) {
    log(Level.SEVERE, C.RED + string, null);
  }

  public static void error(String string, Throwable throwable) {
    log(Level.SEVERE, C.RED + string, throwable);
  }

  public static void error(Throwable throwable) {
    String message = throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
        ? "Unhandled Adapt failure"
        : throwable.getMessage();
    error(message, throwable);
  }

  public static void verbose(String string) {
    verbose(() -> string);
  }

  public static void verbose(Supplier<String> messageSupplier) {
    if (AdaptConfig.get().isVerbose()) {
      log(Level.INFO, C.LIGHT_PURPLE + messageSupplier.get(), null);
    }
  }

  public static void success(String string) {
    log(Level.INFO, C.GREEN + string, null);
  }

  public static void info(String string) {
    log(Level.INFO, C.WHITE + string, null);
  }

  public static void messagePlayer(Player p, String string) {
    String msg = C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + string;
    ComponentMessenger.sendSection(p, msg);
  }

  public static void msg(String string) {
    log(Level.INFO, string, null);
  }

  private static void log(Level level, String message, Throwable throwable) {
    ComponentLog.logLegacy(instance, FALLBACK_LOGGER, LOG_DISCRIMINATOR, level, message, throwable);
  }

  @Override
  public void onLoad() {
    HostKeys.key();
    if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
      WorldGuardFlags.register();
    }
  }

  @Override
  public void start() {
    alreadyDrained.set(false);
    platform = PlatformUtils.createPlatform(this);
    audiences = platform.getAudienceProvider();
    services = new KMap<>();
    runStartupPhaseVoid("discover-services", () -> initialize("art.arcane.adapt.service")
        .forEach((i) -> services.put((Class<? extends AdaptService>) i.getClass(), (AdaptService) i)));
    runStartupPhaseVoid("platform", () -> {
      installPlatformFeatures();
      BukkitPlatform.start(this);
    });
    BukkitPorts.install(this);
    if (Engine.addons().find(Id.ADAPT).isEmpty()) {
      Engine.addons().installBuiltin(HostKeys.key(), new BuiltinAddon());
    }
    core = new AdaptCore(BukkitPlatform.platform(), BukkitPlatform.bus(), new ComponentRegistrar(BukkitPlatform.bus()));
    runStartupPhaseVoid("core-services", core::startServices);
    updates = BukkitUpdateService.register(this, new BukkitUpdateService.Options(
        "VolmitSoftware", "Adapt", "adapt.update", () -> AdaptConfig.get().isAutoUpdateCheck()));
    debugDump = BukkitDebugDump.create(this, new BukkitDebugDump.Options(() -> true, this::captureDebugState,
        new BukkitDebugDump.Presentation("/adapt debug dump", "/adapt debug",
            DirectorMiniMenu.Theme.adaptRed(),
            (key, arguments) -> ComponentText.literal(AdaptLanguage.directorResolver().resolve(key, arguments)))));
    vaultEconomy = new VaultEconomy(this);
    registerPapiExpansion();
    redisSync = new RedisSync();
    initializeGlowingEntities();
    runStartupPhase("start-sim", () -> {
      startSim();
      return null;
    });
    registerListener(new BlockDataListener(this));
    registerListener(new AddonProviders());
    registerListener(new MenuCursor());
    registerListener(Version.get());
    setupMetrics();
    startupPrint(); // Splash screen
    PluginManager pluginManager = getServer().getPluginManager();
    if (pluginManager.isPluginEnabled("WorldGuard")) {
      registerProtector(new WorldGuardProtector());
      RegionPolicyService.install(new WorldGuardRegionPolicySource());
    }
    if (pluginManager.isPluginEnabled("Factions")) {
      registerProtector(new FactionsClaimProtector());
    }
    if (pluginManager.isPluginEnabled("ChestProtect")) {
      registerProtector(new ChestProtectProtector());
    }
    if (pluginManager.isPluginEnabled("Residence")) {
      registerProtector(new ResidenceProtector());
    }
    if (pluginManager.isPluginEnabled("GriefDefender")) {
      registerProtector(new GriefDefenderProtector());
    }
    if (pluginManager.isPluginEnabled("GriefPrevention")) {
      registerProtector(new GriefPreventionProtector());
    }
    if (pluginManager.isPluginEnabled("LockettePro")) {
      registerProtector(new LocketteProProtector());
    }
    if (isHiddenOreIntegrationAvailable(pluginManager)) {
      HiddenOreLink.activate(this);
    } else if (pluginManager.getPlugin("HiddenOre") != null) {
      warn("HiddenOre is installed but disabled. Adapt will continue without HiddenOre integration; review HiddenOre's startup error and configuration.");
    }
    initializeAdaptationListings();
    services.values().forEach(AdaptService::onEnable);
    services.values().forEach(this::registerListener);
    ConfigFileSupport.flushCreatedConfigSummary();
  }

  private void installPlatformFeatures() {
    try {
      BukkitFeatures.install(this, this::getGlowingEntities, GLOWING_ENTITIES_LOCK);
    } catch (RuntimeException | LinkageError failure) {
      warn("Platform features are unavailable: " + summarizeThrowable(failure));
    }
  }

  private static final Logger GLOWING_ENTITIES_LOGGER = Logger.getLogger("GlowingEntities");

  static boolean isHiddenOreIntegrationAvailable(PluginManager pluginManager) {
    return pluginManager.isPluginEnabled("HiddenOre");
  }

  private void initializeGlowingEntities() {
    GLOWING_ENTITIES_LOGGER.setFilter(record -> record.getLevel().intValue() >= Level.WARNING.intValue());
    try {
      glowingEntities = BukkitNatives.find(EntityGlowAccess.class).map(access -> access.create(this)).orElse(null);
    } catch (Throwable t) {
      glowingEntities = null;
      warn("GlowingEntities is unavailable: " + summarizeThrowable(t) + ". Glow-based effects will be disabled.");
    }
    viewerGlowCoordinator = new ViewerGlowCoordinator(glowingEntities);
  }

  private String summarizeThrowable(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    StringBuilder summary = new StringBuilder(throwable.getClass().getSimpleName());
    appendMessage(summary, throwable.getMessage());
    if (root != throwable) {
      summary.append(" | cause=").append(root.getClass().getSimpleName());
      appendMessage(summary, root.getMessage());
    }
    return summary.toString();
  }

  private void appendMessage(StringBuilder builder, String message) {
    if (message != null && !message.isBlank()) {
      builder.append(": ").append(message);
    }
  }

  public void startSim() {
    ViewerDisplayDirector.startRuntime();
    ViewerDisplayDirector.purgeOrphans();
    registerListener(new WorldDataLoader());
    WorldDataLoader.load(Bukkit.getWorlds());
    long startServer = System.currentTimeMillis();
    core.startRuntime();
    AdaptServer adaptServer = core.getAdaptServer();
    int registeredPermissions = AdaptPermissionRegistrar.registerXpMultiplierNodes(Bukkit.getPluginManager(), AdaptConfig.get().getPermissionXpMultipliers());
    verbose("start-sim detail: registered " + registeredPermissions + " XP multiplier permission nodes");
    long serverMs = System.currentTimeMillis() - startServer;
    if (serverMs >= STARTUP_SLOW_PHASE_MS) {
      warn("start-sim detail: core runtime init took " + serverMs + "ms.");
    } else {
      verbose("start-sim detail: core runtime init in " + serverMs + "ms");
    }

  }

  private DebugDumpContributor.Report captureDebugState() {
    String state = "Language: " + AdaptLanguage.activeLocale()
        + "\nLanguage source: " + AdaptLanguage.sourceState()
        + "\nServices: " + (services == null ? 0 : services.size());
    return () -> state;
  }

  public void reconfigureUpdates() {
    BukkitUpdateService current = updates;
    if (current != null) {
      current.reconfigure();
    }
  }

  public BukkitDebugDump debugDump() {
    return debugDump;
  }

  public void postShutdown(Runnable r) {
    postShutdown.add(r);
  }


  public void stopSim() {
    runShutdownPhase("world block scan scheduler", WorldBlockScanScheduler::reset);
    for (Runnable task : List.copyOf(postShutdown)) {
      runShutdownPhase("post-shutdown task", task::run);
    }
    postShutdown.clear();
    AdaptCore running = core;
    if (running != null) {
      runShutdownPhase("core runtime", running::stopRuntime);
    }
    runShutdownPhase("projectile replacements", ProjectileReplacements::clear);
    runShutdownPhase("viewer displays", () -> {
      if (!ViewerDisplayDirector.clearAllAndAwait(SHUTDOWN_CLEANUP_TIMEOUT_MS)) {
        warn("Private display cleanup was incomplete at shutdown.");
      }
    });
    runShutdownPhase("world data", WorldData::stop);
  }

  @Override
  public void stop() {
    runShutdownPhase("PlaceholderAPI", this::unregisterPapiExpansion);
    if (!alreadyDrained.compareAndSet(false, true)) {
      return;
    }
    if (debugDump != null) {
      debugDump.close();
      debugDump = null;
    }
    if (updates != null) {
      updates.close();
      updates = null;
    }
    if (services != null) {
      for (AdaptService service : List.copyOf(services.values())) {
        runShutdownPhase("service " + service.getClass().getSimpleName(), service::onDisable);
      }
    }
    if (metrics != null) {
      runShutdownPhase("metrics", metrics::shutdown);
      metrics = null;
    }
    runShutdownPhase("simulation", this::stopSim);
    AdaptCore running = core;
    if (running != null) {
      runShutdownPhase("core services", running::stopServices);
    }
    if (redisSync != null) {
      RedisSync activeRedisSync = redisSync;
      redisSync = null;
      runShutdownPhase("Redis", activeRedisSync::close);
    }
    if (viewerGlowCoordinator != null) {
      ViewerGlowCoordinator activeCoordinator = viewerGlowCoordinator;
      viewerGlowCoordinator = null;
      runShutdownPhase("viewer glow coordinator", () -> {
        if (!activeCoordinator.clearAndAwait(2_000L)) {
          warn("Private viewer glow cleanup did not complete before shutdown.");
        }
      });
    }
    if (glowingEntities != null) {
      EntityGlow activeGlowingEntities = glowingEntities;
      glowingEntities = null;
      runShutdownPhase("glowing entities", activeGlowingEntities::disable);
    }
    vaultEconomy = null;
    runShutdownPhase("region policy", RegionPolicyService::clear);
    runShutdownPhase("protectors", this::unregisterProtectors);
    if (services != null) {
      services.clear();
    }
    runShutdownPhase("platform", BukkitPlatform::stop);
    runShutdownPhase("ports", BukkitPorts::uninstall);
    core = null;
  }

  private void registerProtector(BukkitProtector protector) {
    AdaptApi.protection().register(protector);
    protectors.add(protector);
    verbose("Protector: \"" + protector.name() + "\" registered.");
  }

  private void unregisterProtectors() {
    for (BukkitProtector protector : List.copyOf(protectors)) {
      AdaptApi.protection().unregister(protector);
      protector.unregister();
    }
    protectors.clear();
  }

  private void runShutdownPhase(String phase, ShutdownAction action) {
    try {
      action.run();
    } catch (Throwable error) {
      warn("Shutdown phase failed (" + phase + "): " + error.getMessage());
      Adapt.error(error);
    }
  }

  private void registerPapiExpansion() {
    PlaceholderRegistration registration = new PlaceholderRegistration(getLogger());
    papiRegistration = registration;
    if (!PlaceholderRegistration.isPlaceholderApiEnabled()) {
      return;
    }

    AdaptPlaceholderInstaller.install(registration, AdaptPlaceholders.get(), getLogger());
  }

  private void unregisterPapiExpansion() {
    PlaceholderRegistration registration = papiRegistration;
    papiRegistration = null;

    if (registration != null) {
      registration.unregister();
    }

    AdaptPlaceholders.get().clear();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    if (viewerGlowCoordinator != null) {
      viewerGlowCoordinator.discardViewer(event.getPlayer().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPluginDisable(PluginDisableEvent event) {
    if (event.getPlugin() != this) {
      return;
    }
    stop();
  }

  @Override
  public CompletionStage<Void> commitReload(ReloadAware.PreUnloadReason reason) {
    Adapt.info("BileTools pre-unload hook fired (" + reason + "). Draining Adapt (persistence flush + services).");
    stop();
    return CompletableFuture.completedFuture(null);
  }

  private void startupPrint() {
    if (!AdaptConfig.get().isSplashScreen()) {
      return;
    }
    String supportedMcVersion = BukkitSupportedVersions.RANGE;
    Random r = new Random();
    int game = r.nextInt(100);
    if (game < 90) {
      Adapt.info("\n" + C.DARK_GRAY + " █████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "██████" + C.DARK_RED + "╗  " + C.DARK_GRAY + "█████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "██████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "████████" + C.DARK_RED + "╗\n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗╚══" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══╝" + C.DARK_RED + "         Adapt, " + C.RED + "Abilities Refined" + C.RED + "[" + SplashScreenSupport.releaseTrain(instance.getDescription().getVersion()) + " RELEASE]\n" +
          C.DARK_GRAY + "███████" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "███████" + C.DARK_RED + "║" + C.DARK_GRAY + "██████" + C.DARK_RED + "╔╝   " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Version: " + C.DARK_RED + instance.getDescription().getVersion() + "     \n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "╔═══╝    " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            By: " + C.WHITE + "Volmit Software (Arcane Arts)" + C.WHITE + " | " + C.DARK_RED + "VolmitSoftware.com\n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██████" + C.DARK_RED + "╔╝" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║        " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Server: " + C.DARK_RED + SplashScreenSupport.serverVersionWithoutMcSuffix(Bukkit.getVersion()) + C.WHITE + " | MC Support: " + C.DARK_RED + supportedMcVersion + "\n" +
          C.DARK_RED + "╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝        ╚═╝   " + C.WHITE + "            Java: " + C.DARK_RED + SplashScreenSupport.javaMajorVersion() + C.WHITE + " | Date: " + C.DARK_RED + SplashScreenSupport.startupDate() + "\n");
    } else {
      info(SecretSplash.getSecretSplash().getRandom());
    }
  }

  public File getJarFile() {
    return getFile();
  }

  @Override
  public String getTag(String subTag) {
    return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.DARK_RED + "Adapt" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
  }

  private void setupMetrics() {
    if (BSTATS_PLUGIN_ID <= 0 || !AdaptConfig.get().isMetrics()) {
      return;
    }

    metrics = AdaptMetrics.start(this, BSTATS_PLUGIN_ID);
  }

  @FunctionalInterface
  private interface ShutdownAction {
    void run() throws Exception;
  }
}
