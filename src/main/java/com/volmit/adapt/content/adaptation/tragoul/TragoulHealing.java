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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import com.volmit.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;


public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
    private final Map<Player, Long> cooldowns;
    private final Map<Player, Long> healingWindow;

    public TragoulHealing() {
        super("tragoul-healing");
        registerConfiguration(TragoulHealing.Config.class);
        setDescription(Localizer.dLocalize("tragoul.healing.description"));
        setDisplayName(Localizer.dLocalize("tragoul.healing.name"));
        setIcon(Material.REDSTONE);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        healingWindow = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.REDSTONE)
                .key("challenge_tragoul_healing_500")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_healing_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_healing_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.RED_DYE)
                        .key("challenge_tragoul_healing_10k")
                        .title(Localizer.dLocalize("advancement.challenge_tragoul_healing_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_tragoul_healing_10k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_healing_500").goal(500).stat("tragoul.healing.health-stolen").reward(400).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_healing_10k").goal(10000).stat("tragoul.healing.health-stolen").reward(1500).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("tragoul.healing.lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.healing.lore2"));
        v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.healing.lore3") + (getConfig().minHealPercent + (getConfig().maxHealPercent - getConfig().minHealPercent) * (level - 1) / (getConfig().maxLevel - 1)) + "%");
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && hasAdaptation(p)) {
            if (isOnCooldown(p)) {
                return;
            }

            if (!healingWindow.containsKey(p)) {
                Adapt.verbose("Starting healing window for " + p.getName());
                startHealingWindow(p);
            }

            if (getConfig().showParticles) {
                vfxParticleLine(p.getLocation(), e.getEntity().getLocation(), 25, Particle.WHITE_ASH);
            }

            double healPercentage = getConfig().minHealPercent + (getConfig().maxHealPercent - getConfig().minHealPercent) * (getLevel(p) - 1) / (getConfig().maxLevel - 1);
            double healAmount = e.getDamage() * healPercentage;
            Adapt.verbose("Healing " + p.getName() + " for " + healAmount + " (" + healPercentage * 100 + "% of " + e.getDamage() + " damage)");
            var attribute = Version.get().getAttribute(p, Attributes.GENERIC_MAX_HEALTH);
            p.setHealth(Math.min(attribute == null ? p.getHealth() : attribute.getValue(), p.getHealth() + healAmount));
            getPlayer(p).getData().addStat("tragoul.healing.health-stolen", (int) healAmount);

        }
    }

    private boolean isOnCooldown(Player p) {
        Long cooldown = cooldowns.get(p);
        return cooldown != null && cooldown > System.currentTimeMillis();
    }

    private void startHealingWindow(Player p) {
        long currentTime = System.currentTimeMillis();
        healingWindow.put(p, currentTime + getConfig().windowDuration);
        Bukkit.getScheduler().runTaskLater(Adapt.instance, () -> {
            healingWindow.remove(p);
            cooldowns.put(p, currentTime + getConfig().windowDuration + getConfig().cooldownDuration);
        }, getConfig().windowDuration / 50);
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
    @ConfigDescription("Regain health based on the damage you deal.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Tragoul Healing adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1.10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Heal Percent for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minHealPercent = 0.10; // 0.10%
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Heal Percent for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHealPercent = 0.45; // 0.45%
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Duration for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int cooldownDuration = 1000; // 1 second
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Window Duration for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int windowDuration = 3000; // 3 seconds
    }
}
