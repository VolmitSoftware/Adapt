package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillAirborne extends SimpleSkill {
    public SkillAirborne() {
        super("airborne");
        setColor(C.BLUE);
        setBarColor(BarColor.BLUE);
        setBarStyle(BarStyle.SEGMENTED_6);
        setInterval(3600);
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(e.getFrom().getWorld().equals(e.getTo().getWorld()) && e.getTo().distanceSquared(e.getFrom()) > 0)
        {
            if(e.getPlayer().getFallDistance() > 0)
            {
                xp(e.getPlayer(), Math.min(e.getPlayer().getFallDistance(), 0.6));
            }

            if(e.getPlayer().isFlying() || e.getPlayer().isGliding())
            {
                xp(e.getPlayer(), 1.85);
            }
        }
    }

    @Override
    public void onTick() {

    }
}
