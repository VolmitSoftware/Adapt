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
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.content.adaptation.tragoul.TragoulThorns;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import de.slikey.effectlib.effect.CloudEffect;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillTragOul extends SimpleSkill<SkillTragOul.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillTragOul() {
        super("tragoul", Localizer.dLocalize("skill", "tragoul", "icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Localizer.dLocalize("skill", "tragoul", "description"));
        setDisplayName(Localizer.dLocalize("skill", "tragoul", "name"));
        setInterval(2755);
        setIcon(Material.CRIMSON_ROOTS);
        cooldowns = new HashMap<>();
        registerAdaptation(new TragoulThorns());

    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (!e.isCancelled()) {
            if (e.getEntity() instanceof Player p) {
                if (e.isCancelled()) {
                    return;
                }
                if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                    return;
                }
                if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))
                        || e.getEntity().isDead()
                        || e.getEntity().isInvulnerable()
                        || p.isDead()
                        || p.isInvulnerable()
                        && !checkValidEntity(e.getEntity().getType())) {
                    return;
                }
                if (p.isBlocking() || p.isDead() || p.isInvulnerable()) {
                    return;
                }
                AdaptPlayer a = getPlayer(p);
                getPlayer(p).getData().addStat("trag.hitsrecieved", 1);
                getPlayer(p).getData().addStat("trag.damage", e.getDamage());
                if (cooldowns.containsKey(p)) {
                    if (cooldowns.get(p) + getConfig().cooldownDelay > System.currentTimeMillis()) {
                        return;
                    } else {
                        cooldowns.remove(p);
                    }
                }
                cooldowns.put(p, System.currentTimeMillis());
                xp(a.getPlayer(), getConfig().damageReceivedXpMultiplier * e.getDamage());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerDeathEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (AdaptConfig.get().blacklistedWorlds.contains(e.getEntity().getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (e.getEntity().getGameMode().equals(GameMode.CREATIVE) || e.getEntity().getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }

        if (AdaptConfig.get().isHardcoreResetOnPlayerDeath()) {
            Adapt.info("Resetting " + e.getEntity().getName() + "'s skills due to death");
            Player p = e.getEntity();
            AdaptPlayer ap = getPlayer(p);
            ap.delete(p.getUniqueId());
            return;
        }
        if (getConfig().takeAwaySkillsOnDeath) {
            if (getConfig().showParticles) {
                CloudEffect ce = new CloudEffect(Adapt.instance.adaptEffectManager);
                ce.mainParticle = Particle.ASH;
                ce.cloudParticle = Particle.REDSTONE;
                ce.duration = 10000;
                ce.iterations = 1000;
                ce.setEntity(e.getEntity());
                ce.start();
            }
            AdaptPlayer a = getPlayer(e.getEntity());
            Player p = a.getPlayer();
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1f, 1f);
            if (a.getData().getSkillLines().get("tragoul") != null) {
                double xp = a.getData().getSkillLines().get("tragoul").getXp();
                if (a.getData().getSkillLines().get("tragoul").getXp() > getConfig().deathXpLoss) {
                    xp(p, getConfig().deathXpLoss);
                } else {
                    a.getData().getSkillLines().get("tragoul").setXp(0);
                }
                a.getData().getSkillLines().get("tragoul").setXp(xp);
                a.getData().getSkillLines().get("tragoul").setLastXP(xp);
                for (PlayerAdaptation adapt : a.getData().getSkillLines().get("tragoul").getAdaptations().values()) {
                    if (adapt.getLevel() > 0) {
                        adapt.setLevel(adapt.getLevel() - 1);
                    } else {
                        adapt.setLevel(0);
                    }
                }
                recalcTotalExp(p);
            }
        }
    }


    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
            if (AdaptConfig.get().blacklistedWorlds.contains(i.getWorld().getName())) {
                return;
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        public double deathXpLoss = -750;
        boolean takeAwaySkillsOnDeath = false;
        boolean enabled = true;
        boolean showParticles = true;
        long cooldownDelay = 1000;
        double damageReceivedXpMultiplier = 2.26;
    }
}
