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

package art.arcane.adapt.api.world;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.notification.ActionBarNotification;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.notification.TitleNotification;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

@Data
@NoArgsConstructor
public class PlayerData {
    private final KMap<String, PlayerSkillLine> skillLines = new KMap<>();
    private KMap<String, Double> stats = new KMap<>();
    private String last = "none";
    private KSet<String> advancements = new KSet<>();
    private Discovery<String> seenBiomes = new Discovery<>();
    private Discovery<EntityType> seenMobs = new Discovery<>();
    private Discovery<Material> seenFoods = new Discovery<>();
    private Discovery<Material> seenItems = new Discovery<>();
    private Discovery<String> seenRecipes = new Discovery<>();
    private Discovery<String> seenEnchants = new Discovery<>();
    private Discovery<String> seenWorlds = new Discovery<>();
    private Discovery<String> seenPeople = new Discovery<>();
    private Discovery<World.Environment> seenEnvironments = new Discovery<>();
    private Discovery<String> seenPotionEffects = new Discovery<>();
    private Discovery<String> seenBlocks = new Discovery<>();
    private KList<XPMultiplier> multipliers = new KList<>();
    private long wisdom = 0;
    private double multiplier = 0;
    private long lastLogin = 0;
    private double masterXp = 1;
    private double lastMasterXp = 0;

    public void giveMasterXp(double xp) {
        masterXp += xp;
    }

    public void globalXPMultiplier(double v, int duration) {
        multipliers.add(new XPMultiplier(v, duration));
    }

    public boolean isGranted(String advancement) {
        return advancements.contains(advancement);
    }

    public void ensureGranted(String advancement) {
        advancements.add(advancement);
    }

    public double getStat(String key) {
        Double d = stats.get(key);
        return d == null ? 0 : d;
    }

    public void addStat(String key, double amt) {
        if (!stats.containsKey(key)) {
            stats.put(key, amt);
        } else {
            stats.put(key, stats.get(key) + amt);
        }
    }

    public void update(AdaptPlayer p) {
        double m = 1D;
        m += collectActivePlayerMultiplierBonus();
        m += collectGlobalMultiplierBonus();

        if (m <= 0) {
            m = 0.01;
        }

        if (m > 1000) {
            m = 1000;
        }

        multiplier = m;

        for (var entry : skillLines.entrySet()) {
            String lineId = entry.getKey();
            Skill<?> loadedSkill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(lineId);
            if (loadedSkill == null) {
                // Never prune unknown lines automatically; missing skills can be transient
                // during startup/reload or due temporary config disables.
                continue;
            }

            PlayerSkillLine lineData = entry.getValue();
            if (lineData == null) {
                skillLines.remove(lineId);
                continue;
            }

            if (lineData.getXp() == 0 && lineData.getKnowledge() == 0) {
                skillLines.remove(lineId, lineData);
                continue;
            }

            lineData.update(p, lineId, this);
        }

        int oldLevel = (int) XP.getLevelForXp(getLastMasterXp());
        int level = (int) XP.getLevelForXp(getMasterXp());

        if (oldLevel != level) {
            setLastMasterXp(getMasterXp());
            p.getNot().queue(SoundNotification.builder()
                            .sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE)
                            .volume(1f)
                            .pitch(0.54f)
                            .group("lvl")
                            .build(),
                    SoundNotification.builder()
                            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
                            .volume(1f)
                            .pitch(0.44f)
                            .group("lvl")
                            .build(),
                    SoundNotification.builder()
                            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
                            .volume(1f)
                            .pitch(0.74f)
                            .group("lvl")
                            .build(),
                    SoundNotification.builder()
                            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
                            .volume(1f)
                            .pitch(1.34f)
                            .group("lvl")
                            .build(),
                    TitleNotification.builder()
                            .in(250)
                            .stay(1450)
                            .out(2250)
                            .group("lvl")
                            .title("")
                            .subtitle(C.GOLD + Localizer.dLocalize("snippets.gui.level") +" " + level)// I'm sorry I missed this!
                            .build());
            p.getActionBarNotifier().queue(
                    ActionBarNotification.builder()
                            .duration(450)
                            .group("power")
                            .title(C.GOLD + "" + Form.f(level * AdaptConfig.get().getPowerPerLevel(), 0) + C.GRAY + " " + Localizer.dLocalize("snippets.gui.max_ability_power")) // I'm sorry I missed this!
                            .build());

        }
    }

    private double collectActivePlayerMultiplierBonus() {
        double bonus = 0D;
        for (int i = multipliers.size() - 1; i >= 0; i--) {
            XPMultiplier active = multipliers.get(i);
            if (active == null || active.isExpired()) {
                multipliers.remove(i);
                continue;
            }
            bonus += active.getMultiplier();
        }
        return bonus;
    }

    private double collectGlobalMultiplierBonus() {
        double bonus = 0D;
        KList<XPMultiplier> globalMultipliers = Adapt.instance.getAdaptServer().getData().getMultipliers();
        for (int i = 0; i < globalMultipliers.size(); i++) {
            XPMultiplier active = globalMultipliers.get(i);
            if (active == null || active.isExpired()) {
                continue;
            }
            bonus += active.getMultiplier();
        }
        return bonus;
    }

    public int getAvailablePower() {
        return getMaxPower() - getUsedPower();
    }

    public boolean hasPowerAvailable() {
        return hasPowerAvailable(1);
    }

    public boolean hasPowerAvailable(int amount) {
        return getAvailablePower() >= amount;
    }

    public int getUsedPower() {
        return skillLines.values().stream().mapToInt(i -> i.getAdaptations().values().stream().mapToInt(PlayerAdaptation::getLevel).sum()).sum();
    }

    public int getLevel() {
        return (int) XP.getLevelForXp(getMasterXp());
    }

    public int getMaxPower() {
        return (int) (XP.getLevelForXp(getMasterXp()) * AdaptConfig.get().getPowerPerLevel());
    }

    public PlayerSkillLine getSkillLine(String skillLine) {
        if (Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillLine) == null) {
            return null;
        }

        synchronized (skillLines) {
            try {
                PlayerSkillLine s = skillLines.get(skillLine);

                if (s != null) {
                    return s;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                Adapt.error("Failed to get skill line " + skillLine);
            }

            PlayerSkillLine s = new PlayerSkillLine();
            s.setLine(skillLine);
            skillLines.put(skillLine, s);
            return s;
        }
    }

    public PlayerSkillLine getSkillLineNullable(String skillLine) {
        return skillLines.get(skillLine);
    }

    public void resetMonotonyForOtherSkills(String currentSkill) {
        for (PlayerSkillLine line : skillLines.values()) {
            if (!line.getLine().equals(currentSkill)) {
                line.relaxStalenessForActivitySwitch();
            }
        }
    }

    public void addWisdom() {
        wisdom++;
    }

    public void clearXp() {
        for (PlayerSkillLine line : skillLines.values()) {
            line.setXp(0);
            line.setLastXP(0);
            line.setLastLevel(0);
            line.setMonotonyCounter(0);
            line.setMonotonyMultiplier(1.0);
            line.setLastXpTimestamp(0);
            line.setSkillStaleness(new PlayerSkillLine.RewardStalenessState());
            line.getActivityStaleness().clear();
            line.getAdaptations().clear();
        }
        masterXp = 1;
        lastMasterXp = 0;
    }

    public void clearKnowledge() {
        for (PlayerSkillLine line : skillLines.values()) {
            line.setKnowledge(0);
        }
    }

    public void clearAdaptations() {
        for (PlayerSkillLine line : skillLines.values()) {
            line.getAdaptations().clear();
        }
    }

    public void clearStats() {
        stats.clear();
    }

    public void clearDiscoveries() {
        seenBiomes = new Discovery<>();
        seenMobs = new Discovery<>();
        seenFoods = new Discovery<>();
        seenItems = new Discovery<>();
        seenRecipes = new Discovery<>();
        seenEnchants = new Discovery<>();
        seenWorlds = new Discovery<>();
        seenPeople = new Discovery<>();
        seenEnvironments = new Discovery<>();
        seenPotionEffects = new Discovery<>();
        seenBlocks = new Discovery<>();
    }

    public void pruneAdaptationsForPowerBudget() {
        while (getUsedPower() > getMaxPower()) {
            String worstSkill = null;
            String worstAdaptation = null;
            int worstLevel = Integer.MAX_VALUE;

            for (var skillEntry : skillLines.entrySet()) {
                for (var adaptEntry : skillEntry.getValue().getAdaptations().entrySet()) {
                    int level = adaptEntry.getValue().getLevel();
                    if (level > 0 && level < worstLevel) {
                        worstLevel = level;
                        worstSkill = skillEntry.getKey();
                        worstAdaptation = adaptEntry.getKey();
                    }
                }
            }

            if (worstSkill == null) {
                break;
            }

            PlayerAdaptation adapt = skillLines.get(worstSkill).getAdaptations().get(worstAdaptation);
            if (adapt.getLevel() <= 1) {
                skillLines.get(worstSkill).getAdaptations().remove(worstAdaptation);
            } else {
                adapt.setLevel(adapt.getLevel() - 1);
            }
        }
    }

    public void clearAll() {
        clearXp();
        clearKnowledge();
        clearAdaptations();
        clearStats();
        clearDiscoveries();
        advancements.clear();
        multipliers.clear();
        wisdom = 0;
    }

    public String toJson(boolean raw) {
        synchronized (skillLines) {
            return Json.toJson(this, !raw);
        }
    }

    public static PlayerData fromJson(String json) {
        return Json.fromJson(json, PlayerData.class);
    }
}
