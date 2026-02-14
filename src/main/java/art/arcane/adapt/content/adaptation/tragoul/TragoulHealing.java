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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;


import art.arcane.adapt.util.common.inventorygui.Window;
import art.arcane.adapt.util.reflect.registries.Particles;

public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
    private final Map<Player, Long> cooldowns;
    private final Map<Player, Long> healingWindow;

    public TragoulHealing() {
        super("tragoul-healing");
        registerConfiguration(TragoulHealing.Config.class);
        setDescription(Localizer.dLocalize("tragoul.healing.description"));
        setDisplayName(Localizer.dLocalize("tragoul.healing.name"));
        setIcon(Material.GLISTERING_MELON_SLICE);
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
        registerMilestone("challenge_tragoul_healing_500", "tragoul.healing.health-stolen", 500, 400);
        registerMilestone("challenge_tragoul_healing_10k", "tragoul.healing.health-stolen", 10000, 1500);
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

            if (areParticlesEnabled()) {
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
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Tragoul Healing adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Heal Percent for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minHealPercent = 0.10; // 0.10%
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Heal Percent for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHealPercent = 0.45; // 0.45%
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Duration for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int cooldownDuration = 1000; // 1 second
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Duration for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int windowDuration = 3000; // 3 seconds
    }
}
