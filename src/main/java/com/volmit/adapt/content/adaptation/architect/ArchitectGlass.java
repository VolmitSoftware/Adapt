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

package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;


public class ArchitectGlass extends SimpleAdaptation<ArchitectGlass.Config> {
    public ArchitectGlass() {
        super("architect-glass");
        registerConfiguration(ArchitectGlass.Config.class);
        setDescription(Localizer.dLocalize("architect.glass.description"));
        setDisplayName(Localizer.dLocalize("architect.glass.name"));
        setIcon(Material.GLASS);
        setInterval(25000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("architect.glass.lore1"));
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (hasAdaptation(p) && (p.getInventory().getItemInMainHand().getType() == Material.AIR || !isTool(p.getInventory().getItemInMainHand())) && !e.isCancelled()) {
            if (!canBlockBreak(p, e.getBlock().getLocation())) {
                return;
            }
            if (e.getBlock().getType().toString().contains("GLASS") && !e.getBlock().getType().toString().contains("TINTED_GLASS")) {
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(e.getBlock().getType(), 1));
                SoundPlayer spw = SoundPlayer.of(e.getBlock().getWorld());
                spw.play(e.getBlock().getLocation(), Sound.BLOCK_LARGE_AMETHYST_BUD_BREAK, 1.0f, 1.0f);
                if (getConfig().showParticles) {

                    e.getBlock().getWorld().spawnParticle(Particle.SCRAPE, e.getBlock().getLocation(), 1);
                    J.a(() -> vfxCuboidOutline(e.getBlock(), Particle.REVERSE_PORTAL));
                }
                e.getBlock().breakNaturally();
            }
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
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Architect Glass adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 5;
    }
}