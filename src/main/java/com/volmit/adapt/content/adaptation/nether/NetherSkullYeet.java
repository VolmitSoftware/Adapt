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

package com.volmit.adapt.content.adaptation.nether;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class NetherSkullYeet extends SimpleAdaptation<NetherSkullYeet.Config> {

    private final Map<Player, Long> lastJump = new HashMap<>();

    public NetherSkullYeet() {
        super("nether-skull-toss");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether", "skulltoss", "description1") + C.ITALIC + " " + Localizer.dLocalize("nether", "skulltoss", "description2") + " " + C.GRAY + Localizer.dLocalize("nether", "skulltoss", "description3"));
        setDisplayName(Localizer.dLocalize("nether", "skulltoss", "name"));
        setIcon(Material.WITHER_SKELETON_SKULL);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(2314);
    }

    @Override
    public void addStats(int level, Element v) {
        int chance = getConfig().getBaseCooldown() - getConfig().getLevelCooldown() * level;
        v.addLore(C.GREEN + String.valueOf(chance) + C.GRAY + " " + Localizer.dLocalize("nether", "skulltoss", "lore1"));
        v.addLore(C.GRAY + Localizer.dLocalize("nether", "skulltoss", "lore2") + C.DARK_GRAY + Localizer.dLocalize("nether", "skulltoss", "lore3") + C.GRAY + ", " + Localizer.dLocalize("nether", "skulltoss", "lore4"));
    }

    private int getCooldownDuration(Player p) {
        return (getConfig().getBaseCooldown() - getConfig().getLevelCooldown() * getLevel(p)) * 20;
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lastJump.remove(p);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (e.useItemInHand() == Event.Result.DENY) {
            return;
        }

        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null || e.getMaterial() != Material.WITHER_SKELETON_SKULL) {
            return;
        }

        Player p = e.getPlayer();

        if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(p)) {
            p.playSound(p, Sound.BLOCK_CONDUIT_DEACTIVATE, 1F, 1F);
            return;
        }

        if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(p)) {
            return;
        }

        if (p.hasCooldown(p.getInventory().getItemInMainHand().getType())) {
            e.setCancelled(true);
            p.playSound(p, Sound.BLOCK_CONDUIT_DEACTIVATE, 1F, 1F);
            return;
        } else {
            p.setCooldown(Material.WITHER_SKELETON_SKULL, getCooldownDuration(p));
        }


        if (p.getGameMode() != GameMode.CREATIVE) {
            e.getItem().setAmount(e.getItem().getAmount() - 1);
            lastJump.put(p, M.ms());
        }

        Vector dir = p.getEyeLocation().getDirection();
        Location spawn = p.getEyeLocation().add(new Vector(.5, -.5, .5)).add(dir);
        p.getWorld().spawn(spawn, WitherSkull.class, entity -> {
            p.playSound(entity, Sound.ENTITY_WITHER_SHOOT, 1, 1);
            entity.setRotation(p.getEyeLocation().getYaw(), p.getEyeLocation().getPitch());
            entity.setCharged(false);
            entity.setBounce(false);
            entity.setDirection(dir);
            entity.setShooter(p);
            xp(p, 100);
        });
    }

    @Override
    public boolean isEnabled() {
        return getConfig().isEnabled();
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }


    @Data
    @NoArgsConstructor
    public static class Config {
        public boolean permanent = false;
        private boolean enabled = true;
        private int baseCooldown = 15;
        private int levelCooldown = 5;
        private int baseCost = 10;
        private double costFactor = 1.5;
        private int maxLevel = 3;
        private int initialCost = 5;
    }
}
