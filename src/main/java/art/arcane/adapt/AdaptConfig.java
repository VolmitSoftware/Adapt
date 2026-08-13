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

import art.arcane.adapt.api.value.MaterialValue;
import art.arcane.adapt.api.xp.Curves;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.config.TomlCodec;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.project.redis.RedisConfig;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
@Getter
public class AdaptConfig {
  private static final Object CONFIG_LOCK = new Object();
  private static AdaptConfig config = null;
  public boolean debug = false;
  public boolean autoUpdateCheck = true;
  public boolean splashScreen = true;
  public boolean xpInCreative = false;
  public boolean allowAdaptationsInCreative = false;
  public String adaptActivatorBlock = "BOOKSHELF";
  public String adaptActivatorBlockName = "a Bookshelf";
  public boolean adaptActivatorAllowVerticalFaces = false;
  public List<String> blacklistedWorlds = List.of("minecraft:some_world_adapt_should_not_run_in", "example:another_world");
  @ConfigDoc(value = "Hard level cap applied to every skill line.", impact = "A skill line that passes the xp for this level is dropped back to the xp of level cap-1 and the player is granted 1 wisdom; level lookups also clamp their search cursor here.")
  public int experienceMaxLevel = 1000;
  boolean preventHunterSkillsWhenHungerApplied = true;
  private ValueConfig value = new ValueConfig();
  private boolean metrics = true;
  private String language = "en_US";
  @ConfigDoc(value = "Level curve mapping skill xp to skill level. Default ADAPT_BALANCED is xp(L) = 100*L^2 + 1200*L, inverted as L(xp) = (sqrt(1440000 + 400*xp) - 1200) / 200.", impact = "Every family in the Curves enum is selectable and each one changes xp-per-level for skills, master xp and power; docs/05 - Configuration Math.md lists the formulas.")
  private Curves xpCurve = Curves.ADAPT_BALANCED;
  @ConfigDoc(value = "Flat master xp granted for each skill level a player gains.", impact = "Master xp for skill level i is playerXpPerSkillLevelUpBase + i*playerXpPerSkillLevelUpLevelMultiplier, so this lifts every level-up reward evenly.")
  private double playerXpPerSkillLevelUpBase = 489;
  @ConfigDoc(value = "Master xp added per skill level already reached when a skill levels up.", impact = "Master xp for skill level i is playerXpPerSkillLevelUpBase + i*playerXpPerSkillLevelUpLevelMultiplier, so this back-loads master progression toward high skill levels.")
  private double playerXpPerSkillLevelUpLevelMultiplier = 44;
  @ConfigDoc(value = "Power granted per master level.", impact = "Max power is floor(masterLevel * powerPerLevel) where masterLevel is xpCurve applied to master xp; lowering it shrinks how many adaptation levels a player can hold at once.")
  private double powerPerLevel = 0.65;
  private boolean hardcoreResetOnPlayerDeath = false;
  private boolean hardcoreNoRefunds = false;

  private boolean loginBonus = true;
  private boolean welcomeMessage = true;
  private boolean advancements = true;
  @ConfigDoc(value = "Controls whether Adapt shows the client advancement toast when an achievement unlocks.", impact = "Disable this to suppress Adapt's achievement popup and its client-controlled toast sound while still recording the achievement.")
  private boolean advancementUnlockToasts = true;
  @ConfigDoc(value = "Volume of Adapt's explicit level-up sounds on every tenth skill level.", impact = "Accepts 0.0 to mute these sounds through 1.0 for full volume; this cannot change the Minecraft client's built-in advancement-toast sound volume.")
  private double levelMilestoneSoundVolume = 0.35D;
  private boolean useEnchantmentTableParticleForActiveEffects = true;
  @ConfigDoc(value = "Controls whether pressing Escape closes the entire Adapt GUI stack.", impact = "When disabled, Escape returns to the parent Adapt menu until the player reaches the root Skills menu.")
  private boolean escClosesAllGuis = false;
  @ConfigDoc(value = "Shows Back buttons in Adapt menus that have a parent menu.", impact = "When disabled, the buttons are hidden; Escape navigation still follows escClosesAllGuis.")
  private boolean guiBackButton = true;
  private boolean customModels = true;
  private boolean automaticGradients = false;
  private int learnUnlearnButtonDelayTicks = 14;
  private int maxRecipeListPrecaution = 25;
  private boolean actionbarNotifyXp = true;
  private boolean actionbarNotifyLevel = true;
  private boolean unlearnAllButton = false;
  @ConfigDoc(value = "Lists every enabled skill in the skills GUI even when a player has no progress in it.", impact = "Display only; use permissions and progression requirements are still enforced.")
  private boolean guiShowAllSkills = false;
  private Gui gui = new Gui();
  private Effects effects = new Effects();
  private FarmPrevention farmPrevention = new FarmPrevention();
  private AdaptationXp adaptationXp = new AdaptationXp();
  private XpIntegrity xpIntegrity = new XpIntegrity();
  private RedisConfig redis = new RedisConfig();
  private SqlSettings sql = new SqlSettings();
  private Protector protectorSupport = new Protector();
  private AbilityApi abilityApi = new AbilityApi();
  private LearningEconomy learningEconomy = new LearningEconomy();
  private PermissionXpMultipliers permissionXpMultipliers = new PermissionXpMultipliers();
  private Map<String, List<String>> adaptationUsageConflicts = defaultAdaptationUsageConflicts();
  private Map<String, Map<String, Boolean>> protectionOverrides = Map.of(
      "adaptation-name", Map.of(
          "WorldGuard", true
      )
  );

  @Setter
  private boolean verbose = false;

  public static AdaptConfig get() {
    synchronized (CONFIG_LOCK) {
      try {
        if (config == null) {
          config = loadConfig(new AdaptConfig(), true);
        }
      } catch (Throwable e) {
        e.printStackTrace();
        config = new AdaptConfig();
      }

      return config;
    }
  }

  public static boolean reload() {
    boolean reloaded;
    synchronized (CONFIG_LOCK) {
      try {
        config = loadConfig(config == null ? new AdaptConfig() : config, false);
        reloaded = true;
      } catch (Throwable e) {
        reloaded = false;
      }
    }
    if (reloaded) {
      MaterialValue.invalidateCache();
    }
    return reloaded;
  }

  public boolean isWorldBlacklisted(WorldInfo world) {
    return world != null && blacklistedWorlds.contains(WorldIdentity.serialize(world));
  }

  private static AdaptConfig loadConfig(AdaptConfig fallback, boolean overwriteOnFailure) throws IOException {
    File canonicalFile = Adapt.instance.getDataFile("adapt", "adapt.toml");
    File legacyFile = Adapt.instance.getDataFile("adapt", "adapt.json");
    return ConfigFileSupport.load(
        canonicalFile,
        legacyFile,
        AdaptConfig.class,
        fallback,
        overwriteOnFailure,
        "core-config",
        "Created missing config [adapt/adapt.toml] from defaults.",
        AdaptConfig::normalize,
        false,
        AdaptConfig::migrateLegacyValueMultiplierKey
    );
  }

  static double normalizeVolume(double volume, double fallback) {
    if (!Double.isFinite(volume)) {
      return fallback;
    }
    return Math.max(0D, Math.min(1D, volume));
  }

  private void normalize() {
    levelMilestoneSoundVolume = normalizeVolume(levelMilestoneSoundVolume, 0.35D);
  }

  static String migrateLegacyValueMultiplierKey(String raw) {
    return migrateLegacyValueMultiplierKey(raw, new File("adapt.toml"));
  }

  private static String migrateLegacyValueMultiplierKey(String raw, File file) {
    if (raw == null || !raw.contains("valueMutlipliers")) {
      return raw;
    }
    JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, file);
    if (parsed == null || !parsed.isJsonObject()) {
      throw new IllegalArgumentException("Unable to parse legacy value multiplier config");
    }
    JsonObject root = parsed.getAsJsonObject();
    JsonElement valueElement = root.get("value");
    if (valueElement == null || !valueElement.isJsonObject()) {
      return raw;
    }
    JsonObject value = valueElement.getAsJsonObject();
    JsonElement legacyElement = value.get("valueMutlipliers");
    if (legacyElement == null || !legacyElement.isJsonObject()) {
      return raw;
    }
    JsonObject merged = new JsonObject();
    if (value.has("valueMultipliers") && value.get("valueMultipliers").isJsonObject()) {
      for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject("valueMultipliers").entrySet()) {
        merged.add(entry.getKey(), entry.getValue());
      }
    }
    for (Map.Entry<String, JsonElement> entry : legacyElement.getAsJsonObject().entrySet()) {
      if (!merged.has(entry.getKey())) {
        merged.add(entry.getKey(), entry.getValue());
      }
    }
    value.remove("valueMutlipliers");
    value.add("valueMultipliers", merged);
    return ConfigFileSupport.isTomlFile(file)
        ? TomlCodec.toToml(root)
        : Json.toJson(root, true);
  }

  private static Map<String, List<String>> defaultAdaptationUsageConflicts() {
    return new HashMap<>();
  }

  @Getter
  public static class AbilityApi {
    private boolean enabled = true;
    private String usePolicyFailureMode = "deny";
    private String costProviderFailureMode = "allow";
    private int providerFaultLimit = 5;
    private int slowProviderMillis = 2;
    private int denyMessageThrottleMillis = 2000;
  }

  @Getter
  public static class LearningEconomy {
    @ConfigDoc(value = "Charges Vault currency when players learn Adaptations.", impact = "Vault and an economy provider are optional; without them, normal Knowledge-only learning remains active.")
    private boolean enabled = false;
    @ConfigDoc(value = "Vault currency charged for each point of Knowledge spent.", impact = "The final price is the Adaptation's Knowledge cost multiplied by this value.")
    private double moneyPerKnowledge = 1D;
    @ConfigDoc(value = "Percentage of the paid Vault cost returned when an Adaptation is unlearned.", impact = "Use 0 to disable money refunds or values up to 100 for partial or full refunds.")
    private double refundPercent = 100D;
  }

  @Getter
  public static class PermissionXpMultipliers {
    @ConfigDoc(value = "Grants passive XP multipliers to players holding configured permission nodes.", impact = "While disabled the node table is ignored and no permissions are registered.")
    private boolean enabled = false;
    @ConfigDoc(value = "Permission node to XP multiplier, for example \"adapt.xpmultiplier.vip\" = 1.5.", impact = "With stack off only the highest node a player holds is multiplied into the boost total; with stack on every held node is multiplied together. Values at or below zero are ignored.")
    private Map<String, Double> multipliers = new LinkedHashMap<>();
    @ConfigDoc(value = "Multiplies every held permission multiplier together instead of using only the highest.", impact = "False resolves to the single highest matched node; true returns the product of all matched nodes, so holding 1.5 and 2.0 yields 3.0.")
    private boolean stack = false;
  }

  @Getter
  public static class Protector {
    private boolean worldguard = true;
    private boolean griefdefender = true;
    private boolean factionsClaim = false;
    private boolean residence = true;
    private boolean chestProtect = true;
    private boolean griefprevention = true;
    private boolean lockettePro = true;
  }

  @Getter
  public static class SqlSettings {
    @ConfigDoc(value = "Stores player data in a MySQL-compatible database instead of local json files.", impact = "When disabled Adapt reads and writes data/players/<uuid>.json; when enabled the settings below must point at a reachable server or logins fall back to the local file.")
    private boolean enabled = false;
    @ConfigDoc(value = "Hostname or address of the SQL server.", impact = "Used to build the jdbc:mysql url; only read while sql.enabled is true.")
    private String host = "localhost";
    @ConfigDoc(value = "TCP port the SQL server listens on.", impact = "Must match the server; 3306 is the MySQL default.")
    private int port = 3306;
    @ConfigDoc(value = "Schema the ADAPT_DATA table lives in.", impact = "The schema must already exist; Adapt creates the table but never the database.")
    private String database = "adapt";
    @ConfigDoc(value = "SQL account Adapt authenticates with.", impact = "The account needs SELECT, INSERT, UPDATE, DELETE and CREATE TABLE on the configured database.")
    private String username = "user";
    @ConfigDoc(value = "Password for the SQL account.", impact = "Sent in plain text unless the SQL server enforces TLS.")
    private String password = "password";
    @ConfigDoc(value = "Connection pool size requested by the advancement database backend.", impact = "Only the advancement backend uses it; higher values allow more concurrent advancement queries at the cost of open server connections.")
    private int poolSize = 10;
    @ConfigDoc(value = "Milliseconds allowed for the jdbc connect handshake.", impact = "Clamped up to 1000; the socket timeout is derived as twice the resulting value.")
    private long connectionTimeout = 5000;
    @ConfigDoc(value = "Seconds passed to Connection.isValid when probing the SQL link.", impact = "Startup validation clamps this to 1-10; the reconnect probe uses the raw value, so large numbers stall the calling thread longer before a reconnect.")
    private int secondsCheckverify = 30;
  }

  @Getter
  public static class Gui {
    @ConfigDoc(value = "Row count for the main skills GUI.", impact = "0 auto-sizes the window to the page contents; 1-6 forces that many rows and pages anything that does not fit.")
    private int skillsGuiRows = 0;
    @ConfigDoc(value = "Skill registry name to Bukkit Material name used as that skill's icon.", impact = "Overrides the built-in icon; models.toml entries still win while customModels is enabled, and unknown material names fall back to the built-in icon.")
    private Map<String, String> skillIcons = new KMap<>();
    @ConfigDoc(value = "Adaptation registry name to Bukkit Material name used as that adaptation's icon.", impact = "Overrides the built-in icon; models.toml entries still win while customModels is enabled, and unknown material names fall back to the built-in icon.")
    private Map<String, String> adaptationIcons = new KMap<>();
    @ConfigDoc(value = "Skill registry names in the order they should appear in the skills GUI.", impact = "Listed skills are placed first in this order; every other skill follows in the normal alphabetical order and unknown names are ignored.")
    private List<String> skillOrder = new KList<>();
    @ConfigDoc(value = "Skill registry name to the ordered adaptation registry names for that skill.", impact = "Listed adaptations are placed first in this order; every other adaptation follows in the normal alphabetical order and unknown names are ignored.")
    private Map<String, List<String>> adaptationOrder = new KMap<>();
  }

  @Getter
  public static class ValueConfig {
    private double baseValue = 1;
    private Map<String, Double> valueMultipliers = defaultValueMultipliersOverrides();

    private Map<String, Double> defaultValueMultipliersOverrides() {
      Map<String, Double> f = new HashMap<>();
      f.put(Material.BLAZE_ROD.name(), 50D);
      f.put(Material.ENDER_PEARL.name(), 75D);
      f.put(Material.GHAST_TEAR.name(), 100D);
      f.put(Material.LEATHER.name(), 1.5D);
      f.put(Material.BEEF.name(), 1.125D);
      f.put(Material.PORKCHOP.name(), 1.125D);
      f.put(Material.EGG.name(), 1.335D);
      f.put(Material.CHICKEN.name(), 1.13D);
      f.put(Material.MUTTON.name(), 1.125D);
      f.put(Material.WHEAT.name(), 1.25D);
      f.put(Material.BEETROOT.name(), 1.25D);
      f.put(Material.CARROT.name(), 1.25D);
      f.put(Material.FLINT.name(), 1.35D);
      f.put(Material.IRON_ORE.name(), 1.75D);
      f.put(Material.DIAMOND_ORE.name(), 5D);
      f.put(Material.GOLD_ORE.name(), 4D);
      f.put(Material.LAPIS_ORE.name(), 3.5D);
      f.put(Material.COAL_ORE.name(), 1.35D);
      f.put(Material.REDSTONE_ORE.name(), 4.5D);
      f.put(Material.NETHER_GOLD_ORE.name(), 4.5D);
      f.put(Material.NETHER_QUARTZ_ORE.name(), 1.11D);
      return f;
    }
  }

  @Getter
  public static class FarmPrevention {
    private boolean enabled = true;
    private boolean perActivityTracking = true;
    private long skillRecoveryMillis = 180000;
    private long activityRecoveryMillis = 300000;
    private long activityStateTtlMillis = 1800000;
    private double skillBasePressure = 1.0;
    private double skillXpPressure = 0.02;
    private double skillDecayCurve = 14.0;
    private double skillFloorMultiplier = 0.08;
    private double activityBasePressure = 1.0;
    private double activityXpPressure = 0.03;
    private double activityDecayCurve = 9.0;
    private double activityFloorMultiplier = 0.12;
    private double crossSkillRecoveryFactor = 0.9;
  }

  @Getter
  public static class XpIntegrity {
    private boolean provenanceEnabled = true;
    private long placedBlockTtlMillis = 86400000;
    private long replaceDenyTtlMillis = 900000;
    private boolean bonemealTrackingEnabled = true;
    private long bonemealTtlMillis = 600000;
    private double bonemealHarvestMultiplier = 0.5;
    private boolean noveltyEnabled = true;
    private int spatialCellShift = 2;
    private int spatialCellCap = 256;
    private long spatialCellTtlMillis = 900000;
    private double spatialRepeatDecay = 0.3;
    private double spatialFloorMultiplier = 0.25;
    private int entropyWindow = 48;
    private double entropyFloorMultiplier = 0.7;
    private boolean adjacencyBonusEnabled = true;
    private double adjacencyBonusMax = 0.25;
    private double adjacencyBonusPerStreak = 0.05;
    private boolean stillnessEnabled = true;
    private long stillnessWindowMillis = 60000;
    private int stillnessMinEvents = 20;
    private double stillnessEpsilon = 0.75;
    private double stillnessFloorMultiplier = 0.25;
    private long fieldCycleMillis = 240000;
    private double fieldCycleFloorMultiplier = 0.15;
    private boolean pooledPayoutEnabled = true;
    private long pooledWindowMillis = 30000;
    private long pooledIdleFlushMillis = 8000;
    private boolean inspiredPopupEnabled = false;
    private long inspiredCooldownMillis = 300000;
  }

  @Getter
  public static class Effects {
    private boolean particlesEnabled = true;
    private boolean soundsEnabled = true;
    private Map<String, Boolean> adaptationParticleOverrides = Map.of(
        "adaptation-name", true
    );
    private Map<String, Boolean> skillParticleOverrides = Map.of(
        "skill-name", true
    );
  }

  @Getter
  public static class AdaptationXp {
    private boolean usageBaselineEnabled = true;
    private double usageBaselineXp = 0.8;
    private double usageBaselineXpPerLevel = 0.18;
    private long usageBaselineCooldownMillis = 12000;
  }

}
