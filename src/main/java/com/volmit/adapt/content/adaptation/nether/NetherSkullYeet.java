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
import com.volmit.adapt.util.*;
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
        SoundPlayer sp = SoundPlayer.of(p);

        if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(p)) {
            sp.play(p, Sound.BLOCK_CONDUIT_DEACTIVATE, 1F, 1F);
            return;
        }

        if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(p)) {
            return;
        }

        if (p.hasCooldown(p.getInventory().getItemInMainHand().getType())) {
            e.setCancelled(true);
            sp.play(p, Sound.BLOCK_CONDUIT_DEACTIVATE, 1F, 1F);
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
            sp.play(entity, Sound.ENTITY_WITHER_SHOOT, 1, 1);
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        public boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        private boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private int baseCooldown = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Level Cooldown for the Nether Skull Yeet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private int levelCooldown = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        private int baseCost = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        private double costFactor = 1.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        private int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        private int initialCost = 5;
    }
}
