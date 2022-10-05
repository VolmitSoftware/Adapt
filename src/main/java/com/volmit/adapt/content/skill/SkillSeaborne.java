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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.seaborrne.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillSeaborne() {
        super("seaborne", Adapt.dLocalize("skill", "seaborne", "icon"));
        registerConfiguration(Config.class);
        setColor(C.BLUE);
        setDescription(Adapt.dLocalize("skill", "seaborne", "description"));
        setDisplayName(Adapt.dLocalize("skill", "seaborne", "name"));
        setInterval(2120);
        setIcon(Material.TRIDENT);
        registerAdaptation(new SeaborneOxygen());
        registerAdaptation(new SeaborneSpeed());
        registerAdaptation(new SeaborneFishersFantasy());
        registerAdaptation(new SeaborneTurtlesVision());
        registerAdaptation(new SeaborneTurtlesMiningSpeed());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_HELMET)
                .key("challenge_swim_1nm")
                .title("Human Submarine!")
                .description("Swim 1 Nautical Mile (1,852 blocks)")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_1nm").goal(1852).stat("move.swim").reward(getConfig().challengeSwim1nmReward).build());
        cooldowns = new HashMap<>();
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (AdaptConfig.get().blacklistedWorlds.contains(i.getWorld().getName())) {
                return;
            }
            if (!AdaptConfig.get().isXpInCreative() && (i.getGameMode().equals(GameMode.CREATIVE) || i.getGameMode().equals(GameMode.SPECTATOR))) {
                return;
            }
            if (i.getWorld().getBlockAt(i.getLocation()).isLiquid() && i.isSwimming() && i.getPlayer() != null && i.getPlayer().getRemainingAir() < i.getMaximumAir()) {
                Adapt.verbose("seaborne Tick");
                checkStatTrackers(getPlayer(i));
                xpSilent(i, getConfig().swimXP);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerFishEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        Adapt.verbose("Fishing");
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        if (e.getState().equals(PlayerFishEvent.State.CAUGHT_FISH)) {
            xp(p, 300);
        } else if (e.getState().equals(PlayerFishEvent.State.CAUGHT_ENTITY)) {
            xp(p, 10);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        if (cooldowns.containsKey(e.getPlayer())) {
            if (cooldowns.get(e.getPlayer()) + getConfig().seaPickleCooldown > System.currentTimeMillis()) {
                return;
            } else {
                cooldowns.remove(e.getPlayer());
            }
        }

        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        Adapt.verbose("Block Break Event");
        if (e.getBlock().getType().equals(Material.SEA_PICKLE) && p.isSwimming() && p.getRemainingAir() < p.getMaximumAir()) { // BECAUSE I LIKE PICKLES
            cooldowns.put(e.getPlayer(), System.currentTimeMillis());
            xpSilent(p, 10);
        } else {
            cooldowns.put(e.getPlayer(), System.currentTimeMillis());
            xpSilent(p, 3);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }

        if (e.getEntity() instanceof Drowned && e.getDamager() instanceof Player p) {
            if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
                return;
            }
            if (cooldowns.containsKey(p)) {
                if (cooldowns.get(p) + getConfig().seaPickleCooldown > System.currentTimeMillis()) {
                    return;
                } else {
                    cooldowns.remove(p);
                }
            }
            cooldowns.put(p, System.currentTimeMillis());
            xp(p, getConfig().damagedrownxpmultiplier * Math.min(e.getDamage(), ((LivingEntity) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if (e.getDamager() instanceof Projectile projectile && projectile instanceof Trident && ((Projectile) e.getDamager()).getShooter() instanceof Player p) {
            xp(p, getConfig().tridentxpmultiplier * Math.min(e.getDamage(), ((LivingEntity) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if (e.getDamager() instanceof Player p && p.getInventory().getItemInMainHand().getType().equals(Material.TRIDENT)) {
            xp(p, getConfig().tridentxpmultiplier * Math.min(e.getDamage(), ((LivingEntity) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        public long seaPickleCooldown = 60000;
        public double tridentxpmultiplier = 2.5;
        double damagedrownxpmultiplier = 4;
        boolean enabled = true;
        double challengeSwim1nmReward = 750;
        double swimXP = 28.7;
    }
}
