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
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class TamingSharedPain extends SimpleAdaptation<TamingSharedPain.Config> {
    public TamingSharedPain() {
        super("tame-shared-pain");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.shared_pain.description"));
        setDisplayName(Localizer.dLocalize("taming.shared_pain.name"));
        setIcon(Material.POPPY);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1700);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getRedirectPercent(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.shared_pain.lore1"));
        v.addLore(C.YELLOW + "* " + Form.f(getOwnerHealthFloor(level), 1) + C.GRAY + " " + Localizer.dLocalize("taming.shared_pain.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Tameable tameable) || !tameable.isTamed() || !(tameable.getOwner() instanceof Player owner) || !hasAdaptation(owner)) {
            return;
        }

        if (!canPVE(owner, tameable.getLocation())) {
            return;
        }

        int level = getLevel(owner);
        double redirect = e.getDamage() * getRedirectPercent(level);
        if (redirect <= 0) {
            return;
        }

        double floor = getOwnerHealthFloor(level);
        double allowed = Math.max(0, owner.getHealth() - floor);
        redirect = Math.min(redirect, allowed);
        if (redirect <= 0.01) {
            return;
        }

        e.setDamage(Math.max(0, e.getDamage() - redirect));
        if (e.getDamage() <= 0.01) {
            e.setCancelled(true);
        }

        owner.damage(redirect);
        SoundPlayer sp = SoundPlayer.of(owner.getWorld());
        sp.play(owner.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.65f, 0.7f);
        sp.play(tameable.getLocation(), Sound.ENTITY_WOLF_WHINE, 0.55f, 1.2f);
        xp(owner, redirect * getConfig().xpPerRedirectedDamage);
    }

    private double getRedirectPercent(int level) {
        return Math.min(getConfig().maxRedirectPercent, getConfig().redirectPercentBase + (getLevelPercent(level) * getConfig().redirectPercentFactor));
    }

    private double getOwnerHealthFloor(int level) {
        return Math.max(1.0, getConfig().ownerHealthFloorBase + (getLevelPercent(level) * getConfig().ownerHealthFloorFactor));
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
    @ConfigDescription("Redirect part of your pet's incoming damage to you, preserving companion survivability.")
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Base for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double redirectPercentBase = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Factor for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double redirectPercentFactor = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Redirect Percent for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxRedirectPercent = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Owner Health Floor Base for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double ownerHealthFloorBase = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Owner Health Floor Factor for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double ownerHealthFloorFactor = 4.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Redirected Damage for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerRedirectedDamage = 2.0;
    }
}
