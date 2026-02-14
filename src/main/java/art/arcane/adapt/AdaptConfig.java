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

import art.arcane.adapt.api.xp.Curves;
import art.arcane.adapt.util.project.redis.RedisConfig;
import art.arcane.adapt.util.config.ConfigFileSupport;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
@Getter
public class AdaptConfig {
    private static AdaptConfig config = null;
    private static final Object CONFIG_LOCK = new Object();
    public boolean debug = false;
    public boolean autoUpdateCheck = true;
    public boolean autoUpdateLanguage = true;
    public boolean splashScreen = true;
    public boolean xpInCreative = false;
    public boolean allowAdaptationsInCreative = false;
    public String adaptActivatorBlock = "BOOKSHELF";
    public String adaptActivatorBlockName = "a Bookshelf";
    public List<String> blacklistedWorlds = List.of("some_world_adapt_should_not_run_in", "anotherWorldFolderName");
    public int experienceMaxLevel = 1000;
    boolean preventHunterSkillsWhenHungerApplied = true;
    private ValueConfig value = new ValueConfig();
    private boolean metrics = true;
    private String language = "en_US";
    private String fallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing = "en_US";
    private Curves xpCurve = Curves.ADAPT_BALANCED;
    private double playerXpPerSkillLevelUpBase = 489;
    private double playerXpPerSkillLevelUpLevelMultiplier = 44;
    private double powerPerLevel = 0.65;
    private boolean hardcoreResetOnPlayerDeath = false;
    private boolean hardcoreNoRefunds = false;
    private boolean loginBonus = true;
    private boolean welcomeMessage = true;
    private boolean advancements = true;
    private boolean useSql = false;
    private boolean useRedis = false;
    private int sqlSecondsCheckverify = 30;
    private boolean useEnchantmentTableParticleForActiveEffects = true;
    private boolean escClosesAllGuis = false;
    private boolean guiBackButton = false;
    private boolean customModels = true;
    private boolean automaticGradients = false;
    private int learnUnlearnButtonDelayTicks = 14;
    private int maxRecipeListPrecaution = 25;
    private boolean actionbarNotifyXp = true;
    private boolean actionbarNotifyLevel = true;
    private boolean unlearnAllButton = false;
    private Effects effects = new Effects();
    private FarmPrevention farmPrevention = new FarmPrevention();
    private AdaptationXp adaptationXp = new AdaptationXp();
    private RedisConfig redis = new RedisConfig();
    private SqlSettings sql = new SqlSettings();
    private Protector protectorSupport = new Protector();
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
        synchronized (CONFIG_LOCK) {
            try {
                config = loadConfig(config == null ? new AdaptConfig() : config, false);
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
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
                "Created missing config [adapt/adapt.toml] from defaults."
        );
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
        private String host = "localhost";
        private int port = 1337;
        private String database = "adapt";
        private String username = "user";
        private String password = "password";
        private int poolSize = 10;
        private long connectionTimeout = 5000;
    }

    @Getter
    public static class ValueConfig {
        private double baseValue = 1;
        private Map<String, Double> valueMutlipliers = defaultValueMultipliersOverrides();

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

    private static Map<String, List<String>> defaultAdaptationUsageConflicts() {
        return new HashMap<>();
    }

}
