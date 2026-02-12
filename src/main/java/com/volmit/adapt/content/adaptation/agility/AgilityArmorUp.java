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

package com.volmit.adapt.content.adaptation.agility;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.registries.Attributes;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgilityArmorUp extends SimpleAdaptation<AgilityArmorUp.Config> {
    private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-armor-up".getBytes());
    private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString( "adapt:armor-up");
    private final Map<Player, Integer> ticksRunning;


    public AgilityArmorUp() {
        super("agility-armor-up");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility.armor_up.description"));
        setIcon(Material.IRON_CHESTPLATE);
        setDisplayName(Localizer.dLocalize("agility.armor_up.name"));
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(350);
        ticksRunning = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_CHESTPLATE)
                .key("challenge_agility_armor_up_30min")
                .title(Localizer.dLocalize("advancement.challenge_agility_armor_up_30min.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_armor_up_30min.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_CHESTPLATE)
                        .key("challenge_agility_armor_up_5hr")
                        .title(Localizer.dLocalize("advancement.challenge_agility_armor_up_5hr.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_armor_up_5hr.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_agility_armor_up_30min")
                .goal(36000)
                .stat("agility.armor-up.ticks-armored")
                .reward(500)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_agility_armor_up_5hr")
                .goal(360000)
                .stat("agility.armor-up.ticks-armored")
                .reward(1500)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getWindupArmor(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("agility.armor_up.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + " " + C.GRAY + Localizer.dLocalize("agility.armor_up.lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ticksRunning.remove(p);
    }

    private double getWindupTicks(double factor) {
        return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
    }

    private double getWindupArmor(double factor) {
        return getConfig().windupArmorBase + (factor * getConfig().windupArmorLevelMultiplier);
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
            if (attribute == null) continue;

            try {
                attribute.removeModifier(MODIFIER, MODIFIER_KEY);
            } catch (Exception e) {
                Adapt.verbose("Failed to remove windup modifier: " + e.getMessage());
            }

            if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSneaking()) {
                ticksRunning.remove(p);
                continue;
            }

            if (p.isSprinting() && hasAdaptation(p)) {
                ticksRunning.compute(p, (k, v) -> {
                    if (v == null) {
                        return 1;
                    }
                    return v + 1;
                });

                Integer tr = ticksRunning.get(p);

                if (tr == null || tr <= 0) {
                    continue;
                }

                double factor = getLevelPercent(p);
                double ticksToMax = getWindupTicks(factor);
                double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), 1);
                double armorInc = M.lerp(0, getWindupArmor(factor), progress);

                if (getConfig().showParticles) {
                    if (M.r(0.2 * progress)) {
                        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(), 1);
                    }

                    if (M.r(0.25 * progress)) {
                        p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation(), 1, 0, 0, 0, 0);
                    }
                }
                attribute.setModifier(MODIFIER, MODIFIER_KEY, armorInc * 10, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                getPlayer(p).getData().addStat("agility.armor-up.ticks-armored", 1);
            } else {
                ticksRunning.remove(p);
            }
        }
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
    @ConfigDescription("Gain more armor the longer you sprint.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Agility Armor Up adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Slowest for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupTicksSlowest = 180;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Fastest for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupTicksFastest = 60;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Armor Base for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupArmorBase = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Windup Armor Level Multiplier for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windupArmorLevelMultiplier = 0.525;
    }

}
