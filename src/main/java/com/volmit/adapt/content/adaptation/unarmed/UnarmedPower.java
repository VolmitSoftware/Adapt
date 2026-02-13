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

package com.volmit.adapt.content.adaptation.unarmed;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class UnarmedPower extends SimpleAdaptation<UnarmedPower.Config> {
    public UnarmedPower() {
        super("unarmed-power");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("unarmed.power.description"));
        setDisplayName(Localizer.dLocalize("unarmed.power.name"));
        setIcon(Material.IRON_INGOT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(4444);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_INGOT)
                .key("challenge_unarmed_power_500")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_power_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_power_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND)
                        .key("challenge_unarmed_power_5k")
                        .title(Localizer.dLocalize("advancement.challenge_unarmed_power_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_unarmed_power_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_unarmed_power_500", "unarmed.power.unarmed-kills", 500, 400);
        registerMilestone("challenge_unarmed_power_5k", "unarmed.power.unarmed-kills", 5000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getUnarmedDamage(level), 0) + C.GRAY + Localizer.dLocalize("unarmed.power.lore1"));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                return;
            }

            if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
                return;
            }
            double factor = getLevelPercent(p);

            if (factor <= 0) {
                return;
            }
            e.setDamage(e.getDamage() * (1 + getUnarmedDamage(getLevel(p))));
            xp(p, 0.321 * factor * e.getDamage(), "unarmed-hit");

        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
                && dmg.getDamager() instanceof Player p
                && hasAdaptation(p)
                && !isTool(p.getInventory().getItemInMainHand())
                && !isTool(p.getInventory().getItemInOffHand())) {
            getPlayer(p).getData().addStat("unarmed.power.unarmed-kills", 1);
        }
    }

    private double getUnarmedDamage(int level) {
        return getLevelPercent(level) * getConfig().damageFactor;
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
    @ConfigDescription("Improved base unarmed damage.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.425;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Power adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageFactor = 2.57;
    }
}
