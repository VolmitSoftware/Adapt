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

package com.volmit.adapt.api.world;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.notification.ActionBarNotification;
import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.notification.TitleNotification;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import lombok.Data;
import lombok.NoArgsConstructor;
import manifold.util.concurrent.ConcurrentHashSet;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@NoArgsConstructor
public class PlayerData {
    private static final Gson GSON = new Gson();
    private final Map<String, PlayerSkillLine> skillLines = new ConcurrentHashMap<>();
    private Map<String, Double> stats = new ConcurrentHashMap<>();
    private String last = "none";
    private Set<String> advancements = new ConcurrentHashSet<>();
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
    private List<XPMultiplier> multipliers = new CopyOnWriteArrayList<>();
    private long wisdom = 0;
    private double multiplier = 0;
    private long lastLogin = 0;
    private double masterXp = 1;
    private double lastMasterXp = 0;

    public static PlayerData fromJson(String json) {
        return GSON.fromJson(json, PlayerData.class);
    }

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
        double m = 1;
        for (XPMultiplier i : multipliers.copy()) {
            if (i.isExpired()) {
                multipliers.remove(i);
                continue;
            }

            m += i.getMultiplier();
        }

        for (XPMultiplier i : Adapt.instance.getAdaptServer().getData().getMultipliers().copy()) {
            if (i.isExpired()) {
                Adapt.instance.getAdaptServer().getData().getMultipliers().remove(i);
                continue;
            }

            m += i.getMultiplier();
        }

        if (m <= 0) {
            m = 0.01;
        }

        if (m > 1000) {
            m = 1000;
        }

        multiplier = m;

        for (String i : skillLines.k()) {
            if (getSkillLine(i) == null) {
                skillLines.remove(i);
                Adapt.warn("Removed unknown skill line '" + i + "' from " + p.getPlayer().getName());
                continue;
            }

            if (getSkillLine(i).getXp() == 0 && getSkillLine(i).getKnowledge() == 0) {
                skillLines.remove(i);
                continue;
            }

            getSkillLine(i).update(p, i, this);
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
                            .subtitle(C.GOLD + Localizer.dLocalize("snippets", "gui", "level") + " " + level)// I'm sorry I missed this!
                            .build());
            p.getActionBarNotifier().queue(
                    ActionBarNotification.builder()
                            .duration(450)
                            .group("power")
                            .title(C.GOLD + Form.f(level * AdaptConfig.get().getPowerPerLevel(), 0) + C.GRAY + " " + Localizer.dLocalize("snippets", "gui", "maxabilitypower")) // I'm sorry I missed this!
                            .build());

        }
    }

    public int getAvailablePower() {
        return getMaxPower() - getUsedPower();
    }

    public boolean hasPowerAvailable(int amount) {
        return getAvailablePower() >= amount;
    }

    public int getUsedPower() {
        return getSkillLines().values().stream().mapToInt(i -> i.getAdaptations().values().stream().mapToInt(PlayerAdaptation::getLevel).sum()).sum();
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

    public void addWisdom() {
        wisdom++;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
