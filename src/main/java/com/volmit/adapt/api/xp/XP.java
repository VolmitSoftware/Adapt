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

package com.volmit.adapt.api.xp;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.M;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class XP {
    public static void xp(Player p, Skill skill, double xp) {
        xp(Adapt.instance.getAdaptServer().getPlayer(p), skill, xp);
    }

    public static void xp(AdaptPlayer p, Skill skill, double xp) {
        PlayerSkillLine skillLine = p.getSkillLine(skill.getName());
        if (skillLine != null) {
            p.getData().resetMonotonyForOtherSkills(skill.getName());
            skillLine.giveXP(p.getNot(), xp);
        }
    }

    public static void xpSilent(Player p, Skill skill, double xp) {
        xpSilent(Adapt.instance.getAdaptServer().getPlayer(p), skill, xp);
    }

    public static void xpSilent(AdaptPlayer p, Skill skill, double xp) {
        if (p.getSkillLine(skill.getName()) != null) {
            p.getData().resetMonotonyForOtherSkills(skill.getName());
            p.getSkillLine(skill.getName()).giveXP(null, xp);
        }
    }

    public static void spatialXP(Location l, Skill skill, double xp, int rad, long duration) {
        Adapt.instance.getAdaptServer().offer(new SpatialXP(l, skill, xp, rad, duration));
    }

    public static void wisdom(Player p, long k) {
        wisdom(Adapt.instance.getAdaptServer().getPlayer(p), k);
    }

    public static void wisdom(AdaptPlayer p, long k) {
        p.getData().setWisdom(p.getData().getWisdom() + k);
    }

    public static void knowledge(Player p, Skill skill, long k) {
        knowledge(Adapt.instance.getAdaptServer().getPlayer(p), skill, k);
    }

    public static void knowledge(AdaptPlayer p, Skill skill, long k) {
        p.getSkillLine(skill.getName()).giveKnowledge(k);
    }

    public static void boostXP(Player p, Skill skill, double percentChange, int durationMS) {
        boostXP(Adapt.instance.getAdaptServer().getPlayer(p), skill, percentChange, durationMS);
    }

    public static void boostXP(AdaptPlayer p, Skill skill, double percentChange, int durationMS) {
        p.getSkillLine(skill.getName()).boost(percentChange, durationMS);
    }

    public static double getXpUntilLevelUp(double xp) {
        double level = getLevelForXp(xp);
        double xa = getXpForLevel((int) level);
        double xb = getXpForLevel((int) level + 1);
        return M.lerp(xb - xa, 0, level - (int) level);
    }

    public static double getLevelProgress(double xp) {
        double level = getLevelForXp(xp);
        return level - (int) level;
    }

    public static double getXpForLevel(double level) {
        return AdaptConfig.get().getXpCurve().getCurve().getXPForLevel(level);
    }

    public static double getLevelForXp(double xp) {
        return AdaptConfig.get().getXpCurve().getCurve().computeLevelForXP(xp, 0.000001);
    }
}
