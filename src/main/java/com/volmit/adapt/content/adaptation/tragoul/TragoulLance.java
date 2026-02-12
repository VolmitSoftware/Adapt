package com.volmit.adapt.content.adaptation.tragoul;/*------------------------------------------------------------------------------
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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class TragoulLance extends SimpleAdaptation<TragoulLance.Config> {
    private final Map<Player, Long> cooldowns;

    public TragoulLance() {
        super("tragoul-lance");
        registerConfiguration(TragoulLance.Config.class);
        setDescription(Localizer.dLocalize("tragoul.lance.description"));
        setDisplayName(Localizer.dLocalize("tragoul.lance.name"));
        setIcon(Material.TRIDENT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_tragoul_lance_200")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_lance_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_lance_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_SWORD)
                .key("challenge_tragoul_lance_kills_100")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_lance_kills_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_lance_kills_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_lance_200").goal(200).stat("tragoul.lance.lances-spawned").reward(400).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_lance_kills_100").goal(100).stat("tragoul.lance.lance-kills").reward(1000).build());
    }


    @EventHandler (priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent e) {
            if (e.getDamager() instanceof Player p && hasAdaptation(p)) {
                Long cooldown = cooldowns.get(p);
                if (cooldown != null && cooldown + 5000 > System.currentTimeMillis())
                    return;

                cooldowns.put(p, System.currentTimeMillis());
                int level = getLevel(p);
                double baseSeekerRange = 5 + 4 * level;
                double damageDealt = e.getDamage();
                double seekerDamage = getConfig().seekerDamageMultiplier * damageDealt;

                triggerSeeker(p, event.getEntity(), seekerDamage, level, baseSeekerRange);
                getPlayer(p).getData().addStat("tragoul.lance.lance-kills", 1);
            }
        }
    }

    private void triggerSeeker(Player p, Entity origin, double damage, int remainingSeekers, double range) {
        if (remainingSeekers <= 0) {
            return;
        }

        LivingEntity nearest = null;
        double minDistance = range;

        for (Entity e : origin.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity le && le != p) {
                double distance = origin.getLocation().distance(le.getLocation());
                if (distance < minDistance) {
                    nearest = le;
                    minDistance = distance;
                }
            }
        }

        if (nearest != null) {
            getPlayer(p).getData().addStat("tragoul.lance.lances-spawned", 1);
            vfxMovingSphere(origin.getLocation(), nearest.getLocation(), getConfig().seekerDelay, Color.MAROON, 0.25, 4);
            double seekerDamage = getConfig().seekerDamageMultiplier * damage;
            double selfDamage = getConfig().selfDamageMultiplier * seekerDamage;
            Adapt.verbose("Seeker damage: " + seekerDamage + " Self damage: " + selfDamage);

            p.damage(selfDamage, p);

            LivingEntity finalNearest = nearest;
            Bukkit.getScheduler().runTaskLater(Adapt.instance, () -> {
                double remainingHealth = finalNearest.getHealth() - damage;
                finalNearest.damage(damage, p);
                if (remainingHealth <= 0) {
                    triggerSeeker(p, finalNearest, damage * 0.5, remainingSeekers - 1, range);
                }
            }, getConfig().seekerDelay);
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

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("tragoul.lance.lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.lance.lore2") );
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.lance.lore3") + level);
    }

    @NoArgsConstructor
    @ConfigDescription("Killing an enemy spawns a lance that seeks and damages a nearby enemy.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Seeker Delay for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int seekerDelay = 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1.10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Seeker Damage Multiplier for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double seekerDamageMultiplier = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Self Damage Multiplier for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double selfDamageMultiplier = 0.5;
    }
}