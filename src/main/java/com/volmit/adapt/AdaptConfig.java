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

package com.volmit.adapt;

import com.google.gson.Gson;
import com.volmit.adapt.api.xp.Curves;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.JSONObject;
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
    private boolean hotReload = false;
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
    private Curves xpCurve = Curves.XL3L7;
    private double playerXpPerSkillLevelUpBase = 489;
    private double playerXpPerSkillLevelUpLevelMultiplier = 44;
    private double powerPerLevel = 0.73;
    private boolean hardcoreResetOnPlayerDeath = false;
    private boolean hardcoreNoRefunds = false;
    private boolean loginBonus = true;
    private boolean advancements = true;
    private boolean useSql = false;
    private int sqlSecondsCheckverify = 30;
    private boolean useEnchantmentTableParticleForActiveEffects = true;
    private boolean escClosesAllGuis = false;
    private boolean guiBackButton = false;
    private int learnUnlearnButtonDelayTicks = 14;
    private int maxRecipeListPrecaution = 25;
    private boolean actionbarNotifyXp = true;
    private boolean actionbarNotifyLevel = true;
    private boolean unlearnAllButton = false;
    private SqlSettings sql = new SqlSettings();
    private Protector protectorSupport = new Protector();
    private Map<String, Map<String, Boolean>> protectionOverrides = Map.of(
            "adaptation-name", Map.of(
                    "WorldGuard", true
            )
    );

    @Setter
    private boolean verbose = false;

    public static AdaptConfig get() {
        if (config == null) {
            AdaptConfig dummy = new AdaptConfig();
            File l = Adapt.instance.getDataFile("adapt", "adapt.json");


            if (!l.exists()) {
                try {
                    IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                } catch (IOException e) {
                    e.printStackTrace();
                    config = dummy;
                    return dummy;
                }
            }

            try {
                config = new Gson().fromJson(IO.readAll(l), AdaptConfig.class);
                IO.writeAll(l, new JSONObject(new Gson().toJson(config)).toString(4));
            } catch (IOException e) {
                e.printStackTrace();
                config = new AdaptConfig();
            }
        }

        return config;
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
}
