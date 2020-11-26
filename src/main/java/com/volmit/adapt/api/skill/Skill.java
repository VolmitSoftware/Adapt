package com.volmit.adapt.api.skill;

import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;

public interface Skill extends Ticked
{
    public String getName();

    public C getColor();

    public BarColor getBarColor();

    public BarStyle getBarStyle();

    public default String getDisplayName()
    {
        return C.RESET + "" + C.BOLD + getColor().toString() + getName();
    }

    public default void dropXP(Location l, int xp)
    {
        ExperienceOrb e = (ExperienceOrb) l.getWorld().spawnEntity(l, EntityType.EXPERIENCE_ORB);
        e.setCustomName("+ " + C.UNDERLINE + C.WHITE + Form.f(xp)+ C.RESET + " " + getDisplayName());
        e.setGlowing(true);
        e.setGravity(false);
        e.setExperience(0);
        e.setPortalCooldown(10000);
        e.setCustomNameVisible(true);
    }

    public default String getDisplayName(int level)
    {
        return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    public default void xp(Player p, double xp)
    {
        XP.xp(p, this, xp);
    }

    public default void knowledge(Player p, long k)
    {
        XP.knowledge(p, this, k);
    }

    public default void wisdom(Player p, long w)
    {
        XP.wisdom(p, w);
    }
}
