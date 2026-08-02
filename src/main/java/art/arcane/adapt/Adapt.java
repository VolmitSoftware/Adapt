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
import art.arcane.adapt.content.protector.WorldGuardProtector;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.plugin.VolmitPlugin;
import art.arcane.adapt.util.common.plugin.VolmitSender;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.config.ConfigMigrationManager;
import art.arcane.adapt.util.project.redis.RedisSync;
import art.arcane.adapt.util.secret.SecretSplash;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.integration.VaultEconomy;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.io.JarScanner;
import com.jeff_media.customblockdata.CustomBlockData;
import de.crazydev22.platformutils.AudienceProvider;
import de.crazydev22.platformutils.Platform;
import de.crazydev22.platformutils.PlatformUtils;
import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.PluginManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static art.arcane.adapt.util.director.context.AdaptationListingHandler.initializeAdaptationListings;

public class Adapt extends VolmitPlugin implements ReloadAware {
  private static final long STARTUP_SLOW_PHASE_MS = 1500L;
  private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);
  private static final boolean SLIMJAR_DEBUG = Boolean.getBoolean("adapt.debug-slimjar");
  private static final Object GLOWING_ENTITIES_LOCK = new Object();
  public static Adapt instance;
  public static Platform platform;
  public static AudienceProvider audiences;
  private static VolmitSender sender;
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
  private Map<String, UIWindow> guiLeftovers = new HashMap<>();
  @Getter
  private AdvancementManager manager;
  @Getter
  private RedisSync redisSync;
  @Getter
  private PlayerDataPersistenceQueue playerDataPersistenceQueue;
  @Getter
  private VaultEconomy vaultEconomy;
  private volatile PlaceholderRegistration papiRegistration;
  private Metrics metrics;


  public Adapt() {
    instance = this;
    long libraryLoadStart = System.currentTimeMillis();
    getLogger().info("Loading Libraries...");
    new SpigotApplicationBuilder(this)
        .debug(SLIMJAR_DEBUG)
        .build();
    long libraryLoadElapsed = System.currentTimeMillis() - libraryLoadStart;
    getLogger().info("Libraries Loaded! (" + libraryLoadElapsed + "ms)");
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

    info("Startup phase: " + phase);
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

  public static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }

  private static String getServerVersion() {
    String version = Bukkit.getVersion();
    int mcMarkerIndex = version.indexOf(" (MC:");
    if (mcMarkerIndex != -1) {
      version = version.substring(0, mcMarkerIndex);
    }
    return version;
  }

  private static String getStartupDate() {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private static String getReleaseTrain(String version) {
    String value = version;
    int suffixIndex = value.indexOf('-');
    if (suffixIndex >= 0) {
      value = value.substring(0, suffixIndex);
    }
    String[] split = value.split("\\.");
    if (split.length >= 2) {
      return split[0] + "." + split[1];
    }
    return value;
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
      info("Development build detected. Skipping update check.");
      return;
    }

    info("Checking for updates...");
    String remoteVersion = fetchRemoteVersion();
    if (remoteVersion == null) {
      error("Failed to check for updates.");
      return;
    }

    int comparison = compareVersionPrefixes(localVersion, remoteVersion);
    if (comparison < 0) {
      info(MessageFormat.format("Please update your Adapt plugin to the latest version! (Current: {0} Latest: {1})", localVersion, remoteVersion));
    } else if (comparison > 0) {
      info("Running a build ahead of the published release. (Current: " + localVersion + " Published: " + remoteVersion + ")");
    } else {
      info("You are running the latest version of Adapt!");
    }
  }

  private static String fetchRemoteVersion() {
    String[] sources = {
        "https://raw.githubusercontent.com/VolmitSoftware/Adapt/main/build.gradle.kts",
        "https://raw.githubusercontent.com/VolmitSoftware/Adapt/main/build.gradle"
    };
    Pattern versionPattern = Pattern.compile("^version\\s*=?\\s*['\"]([^'\"]+)['\"]");

    for (String source : sources) {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(source).openStream()))) {
        String line;
        while ((line = in.readLine()) != null) {
          Matcher matcher = versionPattern.matcher(line.trim());
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
      } catch (Throwable ignored) {
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
    Matcher matcher = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(version);
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
    if (AdaptConfig.get().isDebug()) {
      msg(C.DARK_PURPLE + string);
    }
  }

  public static void warn(String string) {
    msg(C.YELLOW + string);
  }

  public static void error(String string) {
    msg(C.RED + string);
  }

  public static void verbose(String string) {
    if (AdaptConfig.get().isVerbose()) {
      msg(C.LIGHT_PURPLE + string);
    }
  }

  public static void success(String string) {
    msg(C.GREEN + string);
  }

  public static void info(String string) {
    msg(C.WHITE + string);
  }

  public static void messagePlayer(Player p, String string) {
    String msg = C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + string;
    p.sendMessage(msg);
  }

  public static void msg(String string) {
    try {
      if (instance == null) {
        System.out.println("[Adapt]: " + string);
        return;
      }

      String msg = C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + string;
      Bukkit.getConsoleSender().sendMessage(msg);
    } catch (Throwable e) {
      System.out.println("[Adapt]: " + string);
    }
  }

  @Override
  public void onLoad() {
    manager = new AdvancementManager();
    if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
      WorldGuardProtector.registerFlag();
    }
  }

  @Override
  public void start() {
    runStartupPhaseVoid("backup-legacy-configs", ConfigMigrationManager::backupLegacyJsonConfigsOnce);
    runStartupPhaseVoid("remove-retired-adaptation-configs", () -> {
      int deletedConfigs = ConfigMigrationManager.deleteRetiredAdaptationConfigs();
      if (deletedConfigs > 0) {
        Adapt.info("Deleted " + deletedConfigs + " retired adaptation config files.");
      }
    });
    platform = PlatformUtils.createPlatform(this);
    audiences = platform.getAudienceProvider();
    AdaptHud.start(this);
    services = new KMap<>();
    runStartupPhaseVoid("discover-services", () -> initialize("art.arcane.adapt.service")
        .forEach((i) -> services.put((Class<? extends AdaptService>) i.getClass(), (AdaptService) i)));

    runStartupPhaseVoid("language-load", AdaptLanguage::initialize);
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
    if (AdaptConfig.get().isUseSql()) {
      runStartupPhase("sql-connect", () -> {
        sqlManager.establishConnection();
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
    runStartupPhase("config-canonicalization", () -> {
      migrateAllSkillAndAdaptationConfigs();
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
      autoUpdateCheck();
    }
    AbilityApiBridge.install(this);
    protectorRegistry = new ProtectorRegistry();
    if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
      protectorRegistry.registerProtector(new WorldGuardProtector());
    }
    if (getServer().getPluginManager().getPlugin("Factions") != null) {
      protectorRegistry.registerProtector(new FactionsClaimProtector());
    }
    if (getServer().getPluginManager().getPlugin("ChestProtect") != null) {
      protectorRegistry.registerProtector(new ChestProtectProtector());
    }
    if (getServer().getPluginManager().getPlugin("Residence") != null) {
      protectorRegistry.registerProtector(new ResidenceProtector());
    }
    if (getServer().getPluginManager().getPlugin("GriefDefender") != null) {
      protectorRegistry.registerProtector(new GriefDefenderProtector());
    }
    if (getServer().getPluginManager().getPlugin("GriefPrevention") != null) {
      protectorRegistry.registerProtector(new GriefPreventionProtector());
    }
    if (getServer().getPluginManager().getPlugin("LockettePro") != null) {
      protectorRegistry.registerProtector(new LocketteProProtector());
    }
    PluginManager pluginManager = getServer().getPluginManager();
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

  private void migrateAllSkillAndAdaptationConfigs() {
    if (adaptServer == null || adaptServer.getSkillRegistry() == null) {
      return;
    }

    if (!ConfigMigrationManager.hasLegacySkillOrAdaptationJsonFiles()) {
      int deletedLegacyJson = ConfigMigrationManager.deleteMigratedLegacyJsonFiles();
      Adapt.info("Skipped skill/adaptation canonicalization (legacy json not found). deletedLegacyJson=" + deletedLegacyJson + ".");
      return;
    }

    int migratedSkills = 0;
    int migratedAdaptations = 0;
    SkillRegistry registry = adaptServer.getSkillRegistry();
    for (Skill<?> skill : registry.getAllSkills()) {
      int adaptationConfigs = 0;
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation instanceof SimpleAdaptation<?>) {
          adaptationConfigs++;
        }
      }
      if (registry.hotReloadSkillConfig(skill.getName())) {
        if (skill instanceof SimpleSkill<?>) {
          migratedSkills++;
        }
        migratedAdaptations += adaptationConfigs;
      }
    }
    int deletedLegacyJson = ConfigMigrationManager.deleteMigratedLegacyJsonFiles();
    Adapt.info("Canonicalized skill/adaptation configs to TOML (skills=" + migratedSkills + ", adaptations=" + migratedAdaptations + ", deletedLegacyJson=" + deletedLegacyJson + ").");
  }

  public void startSim() {
    long startTicker = System.currentTimeMillis();
    ticker = new Ticker();
    fxDirector = new FxDirector();
    fxDirector.activateRuntime();
    ViewerDisplayDirector.purgeOrphans();
    verbose("start-sim detail: ticker init in " + (System.currentTimeMillis() - startTicker) + "ms");

    long startServer = System.currentTimeMillis();
    adaptServer = new AdaptServer();
    adaptServer.startRuntime();
    int registeredPermissions = AdaptPermissionRegistrar.registerAll(Bukkit.getPluginManager(), adaptServer.getSkillRegistry().getAllSkills());
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
    WorldBlockScanScheduler.reset();
    ViewerDisplayDirector.clearAll();
    if (ticker != null) {
      ticker.shutdown();
    }
    MinionBurden.shutdown();
    postShutdown.forEach(Runnable::run);
    if (adaptServer != null) {
      adaptServer.unregister();
    }
    ProjectileReplacementRegistry.clear();
    PlayerStateRegistry.reset();
    AdaptAttributeService.shutdown();
    if (manager != null) {
      manager.disable();
    }
    MaterialValue.save();
    WorldData.stop();
    CustomModel.clear();
  }

  @Override
  public void stop() {
    unregisterPapiExpansion();
    if (!alreadyDrained.compareAndSet(false, true)) {
      return;
    }
    if (services != null) {
      services.values().forEach(AdaptService::onDisable);
    }
    if (metrics != null) {
      try {
        metrics.shutdown();
      } catch (Throwable e) {
        Adapt.verbose("Failed to shut down metrics: " + e.getMessage());
      } finally {
        metrics = null;
      }
    }
    stopSim();
    AdaptHud.stop();
    if (playerDataPersistenceQueue != null) {
      playerDataPersistenceQueue.flushAndShutdown(30_000L);
      playerDataPersistenceQueue = null;
    }
    if (redisSync != null) {
      try {
        redisSync.close();
      } catch (Exception e) {
        Adapt.verbose("Failed to close redis sync: " + e.getMessage());
      } finally {
        redisSync = null;
      }
    }
    if (sqlManager != null) {
      sqlManager.closeConnection();
    }
    if (viewerGlowCoordinator != null) {
      if (!viewerGlowCoordinator.clearAndAwait(2_000L)) {
        warn("Private viewer glow cleanup did not complete before shutdown.");
      }
      viewerGlowCoordinator = null;
    }
    if (glowingEntities != null) {
      glowingEntities.disable();
    }
    AbilityApiBridge.uninstall();
    vaultEconomy = null;
    if (protectorRegistry != null) {
      protectorRegistry.unregisterAll();
    }
    if (services != null) {
      services.clear();
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
    String supportedMcVersion = "26.2";
    Random r = new Random();
    int game = r.nextInt(100);
    if (game < 90) {
      Adapt.info("\n" + C.DARK_GRAY + " █████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "██████" + C.DARK_RED + "╗  " + C.DARK_GRAY + "█████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "██████" + C.DARK_RED + "╗ " + C.DARK_GRAY + "████████" + C.DARK_RED + "╗\n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "╗╚══" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══╝" + C.DARK_RED + "         Adapt, " + C.RED + "Abilities Refined" + C.RED + "[" + getReleaseTrain(instance.getDescription().getVersion()) + " RELEASE]\n" +
          C.DARK_GRAY + "███████" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "███████" + C.DARK_RED + "║" + C.DARK_GRAY + "██████" + C.DARK_RED + "╔╝   " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Version: " + C.DARK_RED + instance.getDescription().getVersion() + "     \n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "╔══" + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "╔═══╝    " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            By: " + C.WHITE + "Volmit Software (Arcane Arts)\n" +
          C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██████" + C.DARK_RED + "╔╝" + C.DARK_GRAY + "██" + C.DARK_RED + "║  " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.DARK_GRAY + "██" + C.DARK_RED + "║        " + C.DARK_GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Server: " + C.DARK_RED + getServerVersion() + C.WHITE + " | MC Support: " + C.DARK_RED + supportedMcVersion + "\n" +
          C.DARK_RED + "╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝        ╚═╝   " + C.WHITE + "            Java: " + C.DARK_RED + getJavaVersion() + C.WHITE + " | Date: " + C.DARK_RED + getStartupDate() + "\n");
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

  // bstats.org plugin id; 0 disables submission until the id is assigned
  private static final int BSTATS_PLUGIN_ID = 0;

  private void setupMetrics() {
    if (BSTATS_PLUGIN_ID <= 0 || !AdaptConfig.get().isMetrics()) {
      return;
    }

    Metrics m = new Metrics(this, BSTATS_PLUGIN_ID);
    metrics = m;
    // Chart callables run on the bStats daemon thread; keep them off Bukkit world/entity state.
    m.addCustomChart(new SingleLineChart("registered_skills", () -> SkillRegistry.skills.size()));
    m.addCustomChart(new SingleLineChart("learned_adaptations", () -> {
      Adapt adapt = Adapt.instance;
      if (adapt == null) {
        return null;
      }

      AdaptServer server = adapt.getAdaptServer();
      if (server == null) {
        return null;
      }

      return server.getLearnedAdaptationCount();
    }));
    m.addCustomChart(new SimplePie("storage_backend", () -> {
      AdaptConfig config = AdaptConfig.get();
      if (config.isUseRedis()) {
        return "redis";
      }

      return config.isUseSql() ? "sql" : "json";
    }));
    m.addCustomChart(new SimplePie("custom_models", () -> String.valueOf(AdaptConfig.get().isCustomModels())));
    m.addCustomChart(new SimplePie("advancements", () -> String.valueOf(AdaptConfig.get().isAdvancements())));
  }

}
