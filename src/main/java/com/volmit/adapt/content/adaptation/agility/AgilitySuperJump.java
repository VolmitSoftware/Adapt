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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.enums.Particles;
import com.volmit.adapt.util.reflect.enums.PotionEffectTypes;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;


public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
    private final Map<Player, Long> lastJump;

    public AgilitySuperJump() {
        super("agility-super-jump");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility", "superjump", "description"));
        setDisplayName(Localizer.dLocalize("agility", "superjump", "name"));
        setIcon(Material.LEATHER_BOOTS);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9999);
        lastJump = new HashMap<>();
    }

    private double getJumpHeight(int level) {
        return getConfig().baseJumpMultiplier + (getConfig().jumpLevelMultiplier * level);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getJumpHeight(level), 0) + C.GRAY + " " + Localizer.dLocalize("agility", "superjump", "lore1"));
        v.addLore(C.LIGHT_PURPLE + " " + Localizer.dLocalize("agility", "superjump", "lore2"));

    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        if (e.isSneaking() && p.isOnGround()) {
            SoundPlayer sp = SoundPlayer.of(p);
            sp.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3f, 0.35f);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lastJump.remove(p);
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
            return;
        }

        if (p.isSneaking() && hasAdaptation(p) && canUse(getPlayer(p))) {
            Vector velocity = p.getVelocity();

            if (velocity.getY() > 0) {
                double jumpVelocity = 0.4;
                PotionEffect jumpPotion = p.getPotionEffect(PotionEffectTypes.JUMP);

                if (jumpPotion != null) {
                    jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
                }

                if (lastJump.get(p) != null && M.ms() - lastJump.get(p) < 1000) {
                    return;
                } else if (lastJump.get(p) != null && M.ms() - lastJump.get(p) > 1500) {
                    lastJump.remove(p);
                }
                if (p.getLocation().getBlock().getType() != Material.LADDER && velocity.getY() > jumpVelocity && p.isOnline()) {
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 0.7f);
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 1.7f);
                    if (getConfig().showParticles) {
                        p.getWorld().spawnParticle(Particles.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.8, 0.1, 0.1, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData());
                    }
                    p.setVelocity(p.getVelocity().setY(getJumpHeight(getLevel(p))));
                    lastJump.put(p, M.ms());
                }
            }
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
        final boolean permanent = false;
        final boolean enabled = true;
        final boolean showParticles = true;
        final int baseCost = 2;
        final double costFactor = 0.55;
        final int maxLevel = 3;
        final int initialCost = 5;
        final double baseJumpMultiplier = 0.23;
        final double jumpLevelMultiplier = 0.23;
    }
}
