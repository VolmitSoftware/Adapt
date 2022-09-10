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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;


public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
    private final Map<Player, Long> lastJump = new HashMap<>();

    public AgilitySuperJump() {
        super("agility-super-jump");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Agility", "SuperJump", "Description"));
        setDisplayName(Adapt.dLocalize("Agility", "SuperJump", "Name"));
        setIcon(Material.LEATHER_BOOTS);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9999);
    }

    private double getJumpHeight(int level) {
        return getConfig().baseJumpMultiplier + (getConfig().jumpLevelMultiplier * level);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getJumpHeight(level), 0) + C.GRAY + " " + Adapt.dLocalize("Agility", "SuperJump", "Lore1"));
        v.addLore(C.LIGHT_PURPLE + " " + Adapt.dLocalize("Agility", "SuperJump", "Lore2"));

    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }

        if (e.isSneaking() && e.getPlayer().isOnGround()) {
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3f, 0.35f);
            xp(e.getPlayer(), 2);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        lastJump.remove(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
            return;
        }

        if (p.isSneaking() && getLevel(p) > 0) {
            Vector velocity = p.getVelocity();

            if (velocity.getY() > 0) {
                double jumpVelocity = 0.4;
                PotionEffect jumpPotion = p.getPotionEffect(PotionEffectType.JUMP);

                if (jumpPotion != null) {
                    jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
                }

                if (lastJump.get(p) != null && M.ms() - lastJump.get(p) < 1000) {
                    return;
                } else if (lastJump.get(p) != null && M.ms() - lastJump.get(p) > 1500) {
                    lastJump.remove(p);
                }

                if (p.getLocation().getBlock().getType() != Material.LADDER && velocity.getY() > jumpVelocity && p.isOnline()) {
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 0.7f);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 1.7f);
                    if (getConfig().showParticles) {

                        p.getWorld().spawnParticle(Particle.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.8, 0.1, 0.1, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData());
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
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 2;
        double costFactor = 0.55;
        int maxLevel = 3;
        int initialCost = 5;
        double baseJumpMultiplier = 0.23;
        double jumpLevelMultiplier = 0.23;
    }
}