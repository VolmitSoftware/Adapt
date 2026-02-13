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

package com.volmit.adapt.content.adaptation.tragoul;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class TragoulGlobe extends SimpleAdaptation<TragoulGlobe.Config> {
    private final Map<Player, Long> cooldowns;

    public TragoulGlobe() {
        super("tragoul-globe");
        registerConfiguration(TragoulGlobe.Config.class);
        setDescription(Localizer.dLocalize("tragoul.globe.description"));
        setDisplayName(Localizer.dLocalize("tragoul.globe.name"));
        setIcon(Material.CRYING_OBSIDIAN);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GLASS)
                .key("challenge_tragoul_globe_1k")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_globe_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_globe_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_tragoul_globe_1k", "tragoul.globe.mobs-shared-with", 1000, 400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GLASS)
                .key("challenge_tragoul_globe_5")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_globe_5.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_globe_5.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("tragoul.globe.lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.globe.lore2") + ((getConfig().rangePerLevel * level) + getConfig().initalRange));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.globe.lore3") + (getConfig().bonusDamagePerLevel * level));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        Long cooldownTime = cooldowns.get(p);
        if (cooldownTime != null && cooldownTime + (1000 * getConfig().cooldown) > System.currentTimeMillis()) {
            return;
        }

        cooldowns.put(p, System.currentTimeMillis());
        double range = (getConfig().rangePerLevel * getLevel(p)) + getConfig().initalRange;

        int entitiesCount = 0;
        for (Entity entity : p.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity && !entity.equals(p)) {
                entitiesCount++;
            }
        }

        if (entitiesCount <= 1) {
            return;
        }

        double damagePerEntity = e.getDamage() / entitiesCount + (getConfig().bonusDamagePerLevel * getLevel(p));
        e.setDamage(damagePerEntity);

        int mobsSharedWith = 0;
        for (Entity entity : p.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity && !entity.equals(p)) {
                ((LivingEntity) entity).damage(damagePerEntity, p);
                mobsSharedWith++;
            }
        }

        getPlayer(p).getData().addStat("tragoul.globe.mobs-shared-with", mobsSharedWith);
        if (mobsSharedWith >= 5 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_tragoul_globe_5")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_tragoul_globe_5");
        }

        if (areParticlesEnabled()) {
            J.s(() -> vfxFastSphere(p.getLocation(), range, Color.BLACK, 400));
        }
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }


    @NoArgsConstructor
    @ConfigDescription("Spread your damage among all nearby enemies.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Tragoul Globe adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldown = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Range Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rangePerLevel = 3.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Inital Range for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double initalRange = 5.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusDamagePerLevel = 1;
    }
}
