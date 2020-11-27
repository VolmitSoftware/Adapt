package com.volmit.adapt.api.xp;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class XP {
    public static void xp(Player p, Skill skill, double xp)
    {
        xp(Adapt.instance.getAdaptServer().getPlayer(p), skill, xp);
    }

    public static void xp(AdaptPlayer p, Skill skill, double xp)
    {
        p.getSkillLine(skill.getName()).giveXP(p.getNot(), xp);
    }

    public static void spatialXP(Location l, Skill skill, double xp, int rad, long duration)
    {
        Adapt.instance.getAdaptServer().offer(new SpatialXP(l, skill, xp, rad, duration));
    }

    public static void wisdom(Player p, long k)
    {
        wisdom(Adapt.instance.getAdaptServer().getPlayer(p), k);
    }

    public static void wisdom(AdaptPlayer p, long k)
    {
        p.getData().setWisdom(p.getData().getWisdom() + k);
    }

    public static void knowledge(Player p, Skill skill, long k)
    {
        knowledge(Adapt.instance.getAdaptServer().getPlayer(p), skill, k);
    }

    public static void knowledge(AdaptPlayer p, Skill skill, long k)
    {
        p.getSkillLine(skill.getName()).giveKnowledge(k);
    }

    public static void boostXP(Player p, Skill skill, double percentChange, int durationMS)
    {
        boostXP(Adapt.instance.getAdaptServer().getPlayer(p), skill, percentChange, durationMS);
    }

    public static void boostXP(AdaptPlayer p, Skill skill, double percentChange, int durationMS)
    {
        p.getSkillLine(skill.getName()).boost(percentChange, durationMS);
    }

    public static double getXpForLevel(double level)
    {
        return Math.pow(level, 4);
    }

    public static double getLevelForXp(double xp)
    {
        return Math.pow(xp, 1D/4D);
    }
}
