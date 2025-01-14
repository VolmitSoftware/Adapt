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

package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class RiftDescent extends SimpleAdaptation<RiftDescent.Config> {
    private final List<Player> cooldown = new ArrayList<>();

    public RiftDescent() {
        super("rift-descent");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift", "descent", "description"));
        setDisplayName(Localizer.dLocalize("rift", "descent", "name"));
        setMaxLevel(1);
        setIcon(Material.SHULKER_BOX);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(9544);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.YELLOW + Localizer.dLocalize("rift", "descent", "lore1"));
        v.addLore(C.GREEN + Localizer.dLocalize("rift", "descent", "lore2") + " " + C.WHITE + getConfig().cooldown + "s");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (p.getPotionEffect(PotionEffectType.LEVITATION) == null) {
            return;
        }
        if (!hasAdaptation(p)) {
            return;
        }
        if (cooldown.contains(p)) {
            return;
        }

        PotionEffect levi = p.getPotionEffect(PotionEffectType.LEVITATION);

        if (!e.isSneaking() && (levi != null)) {
            p.removePotionEffect(PotionEffectType.LEVITATION);
            J.a(() -> {
                cooldown.add(p);
                try {
                    Thread.sleep((long) (getConfig().cooldown * 1000));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                cooldown.remove(p);

            });

            J.s(() -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int) (20 * getConfig().cooldown), 0));
                sp.play(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);
            });
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = true;
        final boolean enabled = true;
        final double cooldown = 5.0;
        final int baseCost = 1;
        final double costFactor = 2;
        final int initialCost = 3;
    }

}
