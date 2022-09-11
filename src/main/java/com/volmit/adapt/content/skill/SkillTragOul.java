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
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.content.adaptation.tragoul.TragoulThorns;
import com.volmit.adapt.util.C;
import de.slikey.effectlib.effect.CloudEffect;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class SkillTragOul extends SimpleSkill<SkillTragOul.Config> {
    public SkillTragOul() {
        super("tragoul", Adapt.dLocalize("Skill", "TragOul", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Adapt.dLocalize("Skill", "TragOul", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "TragOul", "Name"));
        setInterval(2755);
        setIcon(Material.CRIMSON_ROOTS);
        registerAdaptation(new TragoulThorns());

    }


    @EventHandler (priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (!e.isCancelled()) {
            if (e.getDamager() instanceof Player p) {
                AdaptPlayer a = getPlayer(p);
                xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());

            } else if (e.getEntity() instanceof Player p) {
                AdaptPlayer a = getPlayer(p);
                xp(a.getPlayer(), getConfig().damageReceivedXpMultiplier * e.getDamage());
            }
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void on(PlayerDeathEvent e) {
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

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        public double deathXpLoss = -500;
        boolean takeAwaySkillsOnDeath = true;
        boolean enabled = true;
        boolean showParticles = true;
        double damageXPMultiplier = 5.26;
        double damageReceivedXpMultiplier = 2.26;
    }
}
