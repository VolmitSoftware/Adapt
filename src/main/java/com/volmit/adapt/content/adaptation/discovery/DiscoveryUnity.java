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

package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.List;

import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;

public class DiscoveryUnity extends SimpleAdaptation<DiscoveryUnity.Config> {
    public DiscoveryUnity() {
        super("discovery-unity");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.unity.description"));
        setDisplayName(Localizer.dLocalize("discovery.unity.name"));
        setIcon(Material.REDSTONE);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInterval(666);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("discovery.unity.lore", Form.f(getXPGained(getLevelPercent(level), 1), 0)));
    }

    //Give random XP to the player when they gain XP!
    @EventHandler(priority = EventPriority.LOW)
    public void on(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        AdaptPlayer ap = getPlayer(p);
        if (hasAdaptation(p) && e.getAmount() > 0) {
            xp(p, 5);
            sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.9f);
            //get a random skill that they have unlocked already
            List<PlayerSkillLine> skills = ap.getData().getSkillLines().sortV();
            if (skills.size() > 0) {
                PlayerSkillLine skill = skills.get(RANDOM.nextInt(skills.size()));
                //give them a random amount of XP in that skill
                skill.giveXPFresh(Adapt.instance.getAdaptServer().getPlayer(p).getNot(), getXPGained(getLevelPercent(getLevel(p)), RANDOM.nextInt(3) + 1));
            }

        }
    }

    private double getXPGained(double factor, int amount) {
        return amount * getConfig().xpGainedMultiplier * factor;
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 3;
        double costFactor = 0.3;
        int maxLevel = 7;
        double xpGainedMultiplier = 8;
        double xpBoostMultiplier = 0.01;
        int xpBoostDuration = 15000;
    }
}
