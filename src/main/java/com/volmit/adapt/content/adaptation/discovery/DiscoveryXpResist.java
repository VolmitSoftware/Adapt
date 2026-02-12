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

package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscoveryXpResist extends SimpleAdaptation<DiscoveryXpResist.Config> {
    private static final long COOLDOWN_MILLIS = 15000L;
    private final Map<UUID, Long> cooldowns;

    public DiscoveryXpResist() {
        super("discovery-xp-resist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.resist.description"));
        setDisplayName(Localizer.dLocalize("discovery.resist.name"));
        setIcon(Material.EMERALD);
        setInterval(5215);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        cooldowns = new HashMap<>();
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("discovery.resist.lore0"));
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + Localizer.dLocalize("discovery.resist.lore1"));
        v.addLore(C.GREEN + "+ " + getXpTaken(level) + " " + C.GRAY + Localizer.dLocalize("discovery.resist.lore2"));
    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
    }

    private int getXpTaken(double level) {
        double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
        return Math.max(1, (int) Math.round(d));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        if (!isCriticalHealthDamage(p, e)) {
            return;
        }

        SoundPlayer sp = SoundPlayer.of(p);
        int level = getLevel(p);
        int xpCost = getXpTaken(level);
        if (p.getLevel() < xpCost) {
            vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.RED);
            sp.play(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 15, 0.01f);
            return;
        }
        UUID id = p.getUniqueId();
        Long cooldown = cooldowns.get(id);
        if (cooldown == null || M.ms() - cooldown > COOLDOWN_MILLIS) {
            double effectiveness = getEffectiveness(getLevelPercent(level));
            e.setDamage(Math.max(0D, e.getDamage() * (1D - effectiveness)));
            xp(p, 5);
            cooldowns.put(id, M.ms());
            p.setLevel(p.getLevel() - xpCost);
            vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.LIME);
            sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 3, 0.01f);
            sp.play(p.getLocation(), Sound.BLOCK_SHROOMLIGHT_HIT, 15, 0.01f);
        } else {
            vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.RED);
            sp.play(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 15, 0.01f);
        }
    }

    private boolean isCriticalHealthDamage(Player p, EntityDamageEvent e) {
        double threshold = Math.max(0D, getConfig().triggerHealthThreshold);
        double absorption = Math.max(0D, p.getAbsorptionAmount());
        double rawDamage = Math.max(0D, e.getDamage());
        double finalDamage = Math.max(0D, e.getFinalDamage());
        double healthAfterRaw = p.getHealth() - Math.max(0D, rawDamage - absorption);
        double healthAfterFinal = p.getHealth() - Math.max(0D, finalDamage - absorption);
        double predictedHealth = Math.min(healthAfterRaw, healthAfterFinal);
        return predictedHealth <= 0D || predictedHealth <= threshold || p.getHealth() <= threshold;
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Consume experience to mitigate damage when a hit would drop you below 5 hearts.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double effectivenessBase = 0.15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxEffectiveness = 0.95;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Level Drain for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int levelDrain = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Level Cost Add for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int levelCostAdd = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Amplifier for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double amplifier = 1.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trigger Health Threshold for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double triggerHealthThreshold = 10.0;
    }
}
