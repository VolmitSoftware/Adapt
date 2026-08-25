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

import art.arcane.adapt.api.adaptation.AbilityApiBridge;
import art.arcane.adapt.api.AdaptPermissionRegistrar;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.fx.FxDirector;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.potion.BrewingManager;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import art.arcane.adapt.api.protection.RegionPolicyService;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.Ticker;
import art.arcane.adapt.api.value.MaterialValue;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerDataPersistenceQueue;
import art.arcane.adapt.api.xp.XpNoveltyListener;
import art.arcane.adapt.api.xp.XpProvenanceListener;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.papi.AdaptPlaceholderInstaller;
import art.arcane.adapt.papi.AdaptPlaceholders;
import art.arcane.adapt.content.protector.ChestProtectProtector;
import art.arcane.adapt.content.protector.FactionsClaimProtector;
import art.arcane.adapt.content.protector.GriefDefenderProtector;
import art.arcane.adapt.content.protector.GriefPreventionProtector;
import art.arcane.adapt.content.protector.LocketteProProtector;
import art.arcane.adapt.content.protector.ResidenceProtector;
import art.arcane.adapt.content.protector.WorldGuardFlags;
import art.arcane.adapt.content.protector.WorldGuardProtector;
import art.arcane.adapt.content.protector.WorldGuardRegionPolicySource;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptLanguageDownload;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.misc.CustomModel;
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
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.io.JarScanner;
import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.SplashScreenSupport;
import com.jeff_media.customblockdata.CustomBlockData;
import de.crazydev22.platformutils.AudienceProvider;
import de.crazydev22.platformutils.Platform;
import de.crazydev22.platformutils.PlatformUtils;
import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.PluginManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

import static art.arcane.adapt.util.director.context.AdaptationListingHandler.initializeAdaptationListings;

public class Adapt extends VolmitPlugin implements ReloadAware {
  private static final long STARTUP_SLOW_PHASE_MS = 1500L;
  private static final int UPDATE_CONNECT_TIMEOUT_MS = 3_000;
  private static final int UPDATE_READ_TIMEOUT_MS = 3_000;
  private static final long SHUTDOWN_CLEANUP_TIMEOUT_MS = 5_000L;
  private static final int BSTATS_PLUGIN_ID = 24221;
  private static final boolean SLIMJAR_DEBUG = Boolean.getBoolean("adapt.debug-slimjar");
  private static final Logger FALLBACK_LOGGER = Logger.getLogger("Adapt");
  private static final Object GLOWING_ENTITIES_LOCK = new Object();
  private static final List<String> UPDATE_SOURCES = List.of(
      "https://raw.githubusercontent.com/VolmitSoftware/Adapt/main/build.gradle.kts",
      "https://raw.githubusercontent.com/VolmitSoftware/Adapt/main/build.gradle"
  );
  private static final Pattern REMOTE_VERSION_PATTERN = Pattern.compile("^version\\s*=?\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern VERSION_PREFIX_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
  public static Adapt instance;
  public static Platform platform;
  public static AudienceProvider audiences;
  private static VolmitSender sender;
  private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);
  public final EffectManager adaptEffectManager;
  private final KList<Runnable> postShutdown = new KList<>();
  private KMap<Class<? extends AdaptService>, AdaptService> services;
  @Getter
  private GlowingEntities glowingEntities;
  @Getter
  private ViewerGlowCoordinator viewerGlowCoordinator;
  @Getter
  private Ticker ticker;
  @Getter
  private FxDirector fxDirector;
  @Getter
  private AdaptServer adaptServer;
  @Getter
  private SQLManager sqlManager;
  @Getter
  private ProtectorRegistry protectorRegistry;
  @Getter
  private ConcurrentHashMap<String, UIWindow> guiLeftovers = new ConcurrentHashMap<>();
  @Getter
  private AdvancementManager manager;
  @Getter
  private RedisSync redisSync;
  @Getter
  private PlayerDataPersistenceQueue playerDataPersistenceQueue;
  @Getter
  private VaultEconomy vaultEconomy;
  private volatile PlaceholderRegistration papiRegistration;
  // AdaptMetrics owns all bstats types; never reference them from this class (slimjar link trap)
  private AdaptMetrics metrics;


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
    return (T) instance.services.get(c);
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
    JarScanner js = new JarScanner(instance.getFile(), s);
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

  public static void printInformation() {
    debug("XP Curve: " + AdaptConfig.get().getXpCurve());
    debug("XP/Level base: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
    debug("XP/Level multiplier: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier());
    info("Language: " + AdaptLanguage.activeLocale());
  }

  public static void autoUpdateCheck() {
    String localVersion = instance.getDescription().getVersion();
    if (localVersion.contains("development")) {
      verbose("Development build detected. Skipping update check.");
      return;
    }

    verbose("Checking for updates...");
    String remoteVersion = fetchRemoteVersion();
    if (remoteVersion == null) {
      warn("Failed to check for updates.");
      return;
    }

    int comparison = compareVersionPrefixes(localVersion, remoteVersion);
    if (comparison < 0) {
      info(MessageFormat.format("Please update your Adapt plugin to the latest version! (Current: {0} Latest: {1})", localVersion, remoteVersion));
    } else if (comparison > 0) {
      info("Running a build ahead of the published release. (Current: " + localVersion + " Published: " + remoteVersion + ")");
    } else {
      verbose("Adapt is running the latest published version.");
    }
  }

  private static String fetchRemoteVersion() {
    for (String source : UPDATE_SOURCES) {
      try {
        URLConnection connection = URI.create(source).toURL().openConnection();
        connection.setConnectTimeout(UPDATE_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(UPDATE_READ_TIMEOUT_MS);
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = in.readLine()) != null) {
            Matcher matcher = REMOTE_VERSION_PATTERN.matcher(line.trim());
            if (matcher.find()) {
              return matcher.group(1);
            }
          }
        }
      } catch (IOException | IllegalArgumentException error) {
        verbose("Update source unavailable (" + source + "): " + error.getMessage());
      }
    }

    return null;
  }

  private static int compareVersionPrefixes(String local, String remote) {
    int[] a = parseVersionPrefix(local);
    int[] b = parseVersionPrefix(remote);
    for (int i = 0; i < 3; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }

    return 0;
  }

  private static int[] parseVersionPrefix(String version) {
    int[] parts = new int[3];
    Matcher matcher = VERSION_PREFIX_PATTERN.matcher(version);
    if (matcher.find()) {
      parts[0] = Integer.parseInt(matcher.group(1));
      parts[1] = Integer.parseInt(matcher.group(2));
      parts[2] = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
    }

    return parts;
  }

  public static void actionbar(Player p, String msg) {
    AdaptHud.actionBar(p, msg);
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
    ComponentLog.logLegacy(instance, FALLBACK_LOGGER, "[Adapt] ", level, message, throwable);
  }

  @Override
  public void onLoad() {
    manager = new AdvancementManager();
    if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
      WorldGuardFlags.register();
    }
  }

  @Override
  public void start() {
    alreadyDrained.set(false);
    platform = PlatformUtils.createPlatform(this);
    audiences = platform.getAudienceProvider();
    AdaptHud.start(this);
    services = new KMap<>();
    runStartupPhaseVoid("discover-services", () -> initialize("art.arcane.adapt.service")
        .forEach((i) -> services.put((Class<? extends AdaptService>) i.getClass(), (AdaptService) i)));

    runStartupPhaseVoid("language-load", AdaptLanguage::initialize);
    AdaptLanguageDownload.start();
    AdaptLanguageDownload.requestConfiguredLocale();
    vaultEconomy = new VaultEconomy(this);
    if (!runStartupPhase("models-load", CustomModel::reloadFromDisk)) {
      Adapt.warn("Failed to load models config during startup migration.");
    }
    if (!AdaptConfig.get().isCustomModels()) {
      CustomModel.clear();
    }
    registerPapiExpansion();
    printInformation();
    sqlManager = new SQLManager();
    if (AdaptConfig.get().getSql().isEnabled()) {
      runStartupPhase("sql-connect", () -> {
        if (!sqlManager.establishConnection()) {
          throw new IllegalStateException(
              "SQL persistence is enabled, but its transactional storage gate failed");
        }
        return null;
      });
    }
    redisSync = new RedisSync();
    playerDataPersistenceQueue = new PlayerDataPersistenceQueue();
    initializeGlowingEntities();
    runStartupPhase("start-sim", () -> {
      startSim();
      return null;
    });
    CustomBlockData.registerListener(this);
    registerListener(new BrewingManager());
    registerListener(new XpProvenanceListener());
    registerListener(new XpNoveltyListener());
    registerListener(Version.get());
    setupMetrics();
    startupPrint(); // Splash screen
    if (AdaptConfig.get().isAutoUpdateCheck()) {
      J.a(Adapt::autoUpdateCheck);
    }
    AbilityApiBridge.install(this);
    protectorRegistry = new ProtectorRegistry();
    PluginManager pluginManager = getServer().getPluginManager();
    if (pluginManager.isPluginEnabled("WorldGuard")) {
      protectorRegistry.registerProtector(new WorldGuardProtector());
      RegionPolicyService.install(new WorldGuardRegionPolicySource());
    }
    if (pluginManager.isPluginEnabled("Factions")) {
      protectorRegistry.registerProtector(new FactionsClaimProtector());
    }
    if (pluginManager.isPluginEnabled("ChestProtect")) {
      protectorRegistry.registerProtector(new ChestProtectProtector());
    }
    if (pluginManager.isPluginEnabled("Residence")) {
      protectorRegistry.registerProtector(new ResidenceProtector());
    }
    if (pluginManager.isPluginEnabled("GriefDefender")) {
      protectorRegistry.registerProtector(new GriefDefenderProtector());
    }
    if (pluginManager.isPluginEnabled("GriefPrevention")) {
      protectorRegistry.registerProtector(new GriefPreventionProtector());
    }
    if (pluginManager.isPluginEnabled("LockettePro")) {
      protectorRegistry.registerProtector(new LocketteProProtector());
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

  private static final Logger GLOWING_ENTITIES_LOGGER = Logger.getLogger("GlowingEntities");

  static boolean isHiddenOreIntegrationAvailable(PluginManager pluginManager) {
    return pluginManager.isPluginEnabled("HiddenOre");
  }

  private void initializeGlowingEntities() {
    GLOWING_ENTITIES_LOGGER.setFilter(record -> record.getLevel().intValue() >= Level.WARNING.intValue());
    try {
      glowingEntities = new GlowingEntities(this);
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
    PlayerStateRegistry.start();
    long startTicker = System.currentTimeMillis();
    ticker = new Ticker();
    fxDirector = new FxDirector();
    fxDirector.activateRuntime();
    ViewerDisplayDirector.startRuntime();
    ViewerDisplayDirector.purgeOrphans();
    verbose("start-sim detail: ticker init in " + (System.currentTimeMillis() - startTicker) + "ms");

    long startServer = System.currentTimeMillis();
    adaptServer = new AdaptServer();
    adaptServer.startRuntime();
    int registeredPermissions = AdaptPermissionRegistrar.registerAll(Bukkit.getPluginManager(), adaptServer.getSkillRegistry().getAllSkills());
    registeredPermissions += AdaptPermissionRegistrar.registerXpMultiplierNodes(Bukkit.getPluginManager(), AdaptConfig.get().getPermissionXpMultipliers());
    verbose("start-sim detail: registered " + registeredPermissions + " use permission nodes");
    long serverMs = System.currentTimeMillis() - startServer;
    if (serverMs >= STARTUP_SLOW_PHASE_MS) {
      warn("start-sim detail: AdaptServer init took " + serverMs + "ms.");
    } else {
      verbose("start-sim detail: AdaptServer init in " + serverMs + "ms");
    }

    MinionBurden burden = MinionBurden.get();
    MinionBurden.startRuntime();
    burden.reconcileOnline();

    AdaptAttributeService attributeService = AdaptAttributeService.get();
    AdaptAttributeService.startRuntime();
    attributeService.reconcileOnline();

    long startAdv = System.currentTimeMillis();
    manager.enable();
    verbose("start-sim detail: advancement manager enable in " + (System.currentTimeMillis() - startAdv) + "ms");
  }

  public void postShutdown(Runnable r) {
    postShutdown.add(r);
  }

  public void stopSim() {
    runShutdownPhase("world block scan scheduler", WorldBlockScanScheduler::reset);
    if (ticker != null) {
      runShutdownPhase("ticker", ticker::shutdown);
    }
    runShutdownPhase("attribute change gate", AdaptAttributeService::beginShutdown);
    runShutdownPhase("minion registration gate", MinionBurden::beginShutdown);
    for (Runnable task : List.copyOf(postShutdown)) {
      runShutdownPhase("post-shutdown task", task::run);
    }
    postShutdown.clear();
    if (adaptServer != null) {
      runShutdownPhase("Adapt server", () -> adaptServer.unregister());
    }
    runShutdownPhase("projectile replacements", ProjectileReplacementRegistry::clear);
    runShutdownPhase("player state registry", PlayerStateRegistry::reset);
    if (manager != null) {
      runShutdownPhase("advancement manager", manager::disable);
    }
    runShutdownPhase("viewer displays", () -> {
      if (!ViewerDisplayDirector.clearAllAndAwait(SHUTDOWN_CLEANUP_TIMEOUT_MS)) {
        warn("Private display cleanup was incomplete at shutdown.");
      }
    });
    runShutdownPhase("minion burden", () -> {
      if (!MinionBurden.shutdown(SHUTDOWN_CLEANUP_TIMEOUT_MS)) {
        warn("Minion-burden cleanup was incomplete at shutdown.");
      }
    });
    runShutdownPhase("attribute service", () -> {
      if (!AdaptAttributeService.shutdown(SHUTDOWN_CLEANUP_TIMEOUT_MS)) {
        warn("Adapt attribute cleanup was incomplete at shutdown.");
      }
    });
    runShutdownPhase("material values", MaterialValue::save);
    runShutdownPhase("world data", WorldData::stop);
    runShutdownPhase("custom models", CustomModel::clear);
  }

  @Override
  public void stop() {
    runShutdownPhase("PlaceholderAPI", this::unregisterPapiExpansion);
    if (!alreadyDrained.compareAndSet(false, true)) {
      return;
    }
    runShutdownPhase("language downloader", AdaptLanguageDownload::shutdown);
    runShutdownPhase("Ability API", AbilityApiBridge::uninstall);
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
    runShutdownPhase("HUD", AdaptHud::stop);
    if (playerDataPersistenceQueue != null) {
      PlayerDataPersistenceQueue queue = playerDataPersistenceQueue;
      playerDataPersistenceQueue = null;
      runShutdownPhase("player data persistence", () -> queue.flushAndShutdown(30_000L));
    }
    if (redisSync != null) {
      RedisSync activeRedisSync = redisSync;
      redisSync = null;
      runShutdownPhase("Redis", activeRedisSync::close);
    }
    if (sqlManager != null) {
      SQLManager activeSqlManager = sqlManager;
      sqlManager = null;
      runShutdownPhase("SQL", activeSqlManager::closeConnection);
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
      GlowingEntities activeGlowingEntities = glowingEntities;
      glowingEntities = null;
      runShutdownPhase("glowing entities", activeGlowingEntities::disable);
    }
    vaultEconomy = null;
    runShutdownPhase("region policy", RegionPolicyService::clear);
    if (protectorRegistry != null) {
      ProtectorRegistry activeRegistry = protectorRegistry;
      protectorRegistry = null;
      runShutdownPhase("protectors", activeRegistry::unregisterAll);
    }
    if (services != null) {
      services.clear();
    }
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
  public void onPreUnload(ReloadAware.PreUnloadReason reason) {
    Adapt.info("BileTools pre-unload hook fired (" + reason + "). Draining Adapt (persistence flush + services).");
    stop();
  }

  private void startupPrint() {
    if (!AdaptConfig.get().isSplashScreen()) {
      return;
    }
    String supportedMcVersion = "26.1-26.2";
    Random r = new Random();
    int game = r.nextInt(100);
    if (game < 90) {
      Adapt.info("\n" + C.DARK_GRAY + " █████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "██████" + C.DARK_RED + "╗  " + C.DARK_GRAY + "█████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "██████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "████████" + C.DARK_RED + "╗\n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗╚══" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══╝" + C.DARK_RED + "         Adapt, " + C.RED + "Abilities Refined" + C.RED + "[" + SplashScreenSupport.releaseTrain(instance.getDescription().getVersion()) + " RELEASE]\n" +
          C.DARK_GRAY + "███████" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "███████" + C.DARK_RED + "║" + C.DARK_GRAY + "██████" + C.DARK_RED + "╔╝   " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Version: " + C.DARK_RED + instance.getDescription().getVersion() + "     \n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "╔═══╝    " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            By: " + C.WHITE + "Volmit Software (Arcane Arts)" + C.WHITE + " | " + C.DARK_RED + "VolmitSoftware.com\n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██████" + C.DARK_RED + "╔╝" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║        " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Server: " + C.DARK_RED + SplashScreenSupport.serverVersionWithoutMcSuffix() + C.WHITE + " | MC Support: " + C.DARK_RED + supportedMcVersion + "\n" +
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
