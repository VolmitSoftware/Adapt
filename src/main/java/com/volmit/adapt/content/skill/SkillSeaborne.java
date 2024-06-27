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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.seaborrne.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillSeaborne() {
        super("seaborne", Localizer.dLocalize("skill", "seaborne", "icon"));
        registerConfiguration(Config.class);
        setColor(C.BLUE);
        setDescription(Localizer.dLocalize("skill", "seaborne", "description"));
        setDisplayName(Localizer.dLocalize("skill", "seaborne", "name"));
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

    private boolean isOnCooldown(Player p, long cooldown) {
        Long lastCooldown = cooldowns.get(p);
        return lastCooldown != null && lastCooldown + cooldown > System.currentTimeMillis();
    }

    private void setCooldown(Player p) {
        cooldowns.put(p, System.currentTimeMillis());
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> {
                if (i.getWorld().getBlockAt(i.getLocation()).isLiquid() && i.isSwimming() && i.getPlayer() != null && i.getPlayer().getRemainingAir() < i.getMaximumAir()) {
                    Adapt.verbose("seaborne Tick");
                    checkStatTrackers(getPlayer(i));
                    xpSilent(i, getConfig().swimXP);
                }
            });

        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerFishEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getState().equals(PlayerFishEvent.State.CAUGHT_FISH)) {
                xp(p, 300);
            } else if (e.getState().equals(PlayerFishEvent.State.CAUGHT_ENTITY)) {
                xp(p, 10);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (isOnCooldown(p, getConfig().seaPickleCooldown)) {
                return;
            }
            setCooldown(p);
            if (e.getBlock().getType().equals(Material.SEA_PICKLE) && p.isSwimming() && p.getRemainingAir() < p.getMaximumAir()) { // BECAUSE I LIKE PICKLES
                xpSilent(p, 10);
            } else {
                xpSilent(p, 3);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Drowned && e.getDamager() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> {
                if (isOnCooldown(p, getConfig().seaPickleCooldown)) {
                    return;
                }
                setCooldown(p);
                xp(p, getConfig().damagedrownxpmultiplier * Math.min(e.getDamage(), ((LivingEntity) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
            });
        }
        if (e.getDamager() instanceof Projectile projectile && projectile instanceof Trident && ((Projectile) e.getDamager()).getShooter() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> xp(p, getConfig().tridentxpmultiplier * Math.min(e.getDamage(), ((LivingEntity) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue())));
        }
        if (e.getDamager() instanceof Player p && p.getInventory().getItemInMainHand().getType().equals(Material.TRIDENT)) {
            shouldReturnForPlayer(p, e, () -> xp(p, getConfig().tridentxpmultiplier * Math.min(e.getDamage(), ((LivingEntity) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue())));
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
        double damagedrownxpmultiplier = 3;
        boolean enabled = true;
        double challengeSwim1nmReward = 750;
        double swimXP = 28.7;
    }
}
