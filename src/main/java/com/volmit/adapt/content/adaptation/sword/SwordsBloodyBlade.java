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

package com.volmit.adapt.content.adaptation.sword;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class SwordsBloodyBlade extends SimpleAdaptation<SwordsBloodyBlade.Config> {
    private final Map<Player, Long> cooldowns;

    public SwordsBloodyBlade() {
        super("sword-bloody-blade");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword.bloody_blade.description"));
        setDisplayName(Localizer.dLocalize("sword.bloody_blade.name"));
        setIcon(Material.RED_DYE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5534);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + " " + Localizer.dLocalize("sword.bloody_blade.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getDurationOfEffect(level), 1) + C.GRAY + " " + Localizer.dLocalize("sword.bloody_blade.lore2"));
        v.addLore(C.RED + "* " + Form.duration(getCooldown(level), 1) + C.GRAY + " " + Localizer.dLocalize("sword.bloody_blade.lore3"));
    }

    public long getCooldown(int level) {
        return getConfig().cooldown * level;
    }

    public long getDurationOfEffect(int level) {
        return getConfig().effectDuration * level;
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p) && ItemListings.getToolSwords().contains(p.getInventory().getItemInMainHand().getType())) {
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown > System.currentTimeMillis())
                return;
            Entity victim = e.getEntity();
            cooldowns.put(p, System.currentTimeMillis() + getCooldown(getLevel(p)));
            if (victim instanceof Player pvic) {
                if (!canPVP(p, pvic.getLocation())) return;
            } else {
                if (!canPVE(p, victim.getLocation())) return;
            }
            if (getConfig().showParticles) {
                BleedEffect blood = victim instanceof LivingEntity l ? new DamagingBleedEffect(Adapt.instance.adaptEffectManager, getConfig().damagePerBleedProc, l) : new BleedEffect(Adapt.instance.adaptEffectManager);
                blood.setEntity(victim);
                blood.material = Material.CRIMSON_ROOTS;
                blood.height = -1;
                blood.iterations = Math.toIntExact(2 * (3 + (getDurationOfEffect(getLevel(p)) / 1000)));
                blood.period = 5; //5 Every second, make a proc
                blood.hurt = false;
//                blood.callback = () -> {
//                    Adapt.mAdapt.msgp(sender.player(),(p,"You bled out..");
//                    p.setHealth(1d);
//                };
                blood.start();
            } else {
                BleedEffect blood = victim instanceof LivingEntity l ? new DamagingBleedEffect(Adapt.instance.adaptEffectManager, getConfig().damagePerBleedProc, l) : new BleedEffect(Adapt.instance.adaptEffectManager);
                blood.setEntity(victim);
                blood.material = Material.VOID_AIR;
                blood.height = -1;
                blood.iterations = Math.toIntExact(2 * (3 + (getDurationOfEffect(getLevel(p)) / 1000)));
                blood.period = 5; //5 Every second, make a proc
                blood.hurt = false;
                blood.start();
            }

        }
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
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public long cooldown = 5000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Per Bleed Proc for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double damagePerBleedProc = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effect Duration for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public long effectDuration = 1000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Swords Bloody Blade adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.325;

    }
}
