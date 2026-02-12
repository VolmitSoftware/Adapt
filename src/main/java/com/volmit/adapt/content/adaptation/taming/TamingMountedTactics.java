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

package com.volmit.adapt.content.adaptation.taming;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class TamingMountedTactics extends SimpleAdaptation<TamingMountedTactics.Config> {
    public TamingMountedTactics() {
        super("tame-mounted-tactics");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.mounted_tactics.description"));
        setDisplayName(Localizer.dLocalize("taming.mounted_tactics.name"));
        setIcon(Material.SADDLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(10);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getMountedDamageBonus(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.mounted_tactics.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getMountedDamageReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.mounted_tactics.lore2"));
    }

    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p)) {
                continue;
            }

            int level = getLevel(p);
            Entity vehicle = p.getVehicle();
            if (vehicle instanceof AbstractHorse horse) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, getHorseSpeedAmplifier(level), false, false, true), true);
                if (p.isSprinting()) {
                    Vector push = p.getLocation().getDirection().clone().setY(0).normalize().multiply(getHorsePush(level));
                    horse.setVelocity(horse.getVelocity().multiply(0.8).add(push));
                }
            } else if (vehicle instanceof Strider strider) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false, true), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, getStriderSpeedAmplifier(level), false, false, true), true);
                if (strider.getLocation().getBlock().getType() == Material.LAVA || strider.getLocation().clone().subtract(0, 1, 0).getBlock().getType() == Material.LAVA) {
                    strider.setShivering(false);
                }
            } else if (vehicle instanceof Pig pig) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 25, getPigResistanceAmplifier(level), false, false, true), true);
                if (p.isSprinting()) {
                    Vector forward = p.getLocation().getDirection().clone().setY(0).normalize().multiply(getPigPush(level));
                    pig.setVelocity(pig.getVelocity().multiply(0.7).add(forward));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player attacker && hasAdaptation(attacker) && attacker.getVehicle() != null) {
            if (e.getEntity() instanceof Player victim) {
                if (!canPVP(attacker, victim.getLocation())) {
                    return;
                }
            } else if (!canPVE(attacker, e.getEntity().getLocation())) {
                return;
            }

            e.setDamage(e.getDamage() * (1D + getMountedDamageBonus(getLevel(attacker))));
            xp(attacker, e.getDamage() * getConfig().xpPerMountedDamage);
        }

        if (e.getEntity() instanceof Player defender && hasAdaptation(defender) && defender.getVehicle() != null) {
            e.setDamage(e.getDamage() * (1D - getMountedDamageReduction(getLevel(defender))));
        }
    }

    private double getMountedDamageBonus(int level) {
        return Math.min(getConfig().maxMountedDamageBonus, getConfig().mountedDamageBonusBase + (getLevelPercent(level) * getConfig().mountedDamageBonusFactor));
    }

    private double getMountedDamageReduction(int level) {
        return Math.min(getConfig().maxMountedDamageReduction, getConfig().mountedDamageReductionBase + (getLevelPercent(level) * getConfig().mountedDamageReductionFactor));
    }

    private int getHorseSpeedAmplifier(int level) {
        return Math.max(0, (int) Math.round(getConfig().horseSpeedAmplifierBase + (getLevelPercent(level) * getConfig().horseSpeedAmplifierFactor)));
    }

    private int getStriderSpeedAmplifier(int level) {
        return Math.max(0, (int) Math.round(getConfig().striderSpeedAmplifierBase + (getLevelPercent(level) * getConfig().striderSpeedAmplifierFactor)));
    }

    private int getPigResistanceAmplifier(int level) {
        return Math.max(0, (int) Math.round(getConfig().pigResistanceAmplifierBase + (getLevelPercent(level) * getConfig().pigResistanceAmplifierFactor)));
    }

    private double getHorsePush(int level) {
        return getConfig().horsePushBase + (getLevelPercent(level) * getConfig().horsePushFactor);
    }

    private double getPigPush(int level) {
        return getConfig().pigPushBase + (getLevelPercent(level) * getConfig().pigPushFactor);
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
    @ConfigDescription("Gain mount-specific combat and control bonuses while riding.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Bonus Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mountedDamageBonusBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Bonus Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mountedDamageBonusFactor = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Mounted Damage Bonus for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxMountedDamageBonus = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Reduction Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mountedDamageReductionBase = 0.06;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Reduction Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mountedDamageReductionFactor = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Mounted Damage Reduction for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxMountedDamageReduction = 0.28;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Horse Speed Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double horseSpeedAmplifierBase = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Horse Speed Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double horseSpeedAmplifierFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double striderSpeedAmplifierBase = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double striderSpeedAmplifierFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pig Resistance Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pigResistanceAmplifierBase = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pig Resistance Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pigResistanceAmplifierFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Horse Push Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double horsePushBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Horse Push Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double horsePushFactor = 0.16;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pig Push Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pigPushBase = 0.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Pig Push Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pigPushFactor = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mounted Damage for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerMountedDamage = 1.5;
    }
}
