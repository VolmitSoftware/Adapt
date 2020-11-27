package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillSeaborne extends SimpleSkill {
    public SkillSeaborne() {
        super("seaborne");
        setColor(C.BLUE);
        setBarColor(BarColor.BLUE);
        setBarStyle(BarStyle.SEGMENTED_6);
        setInterval(1320);
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(e.getPlayer().isSwimming() && e.getTo().getWorld().equals(e.getFrom().getWorld()) && e.getTo().distanceSquared(e.getFrom()) > 0)
        {
            xp(e.getPlayer(), 1.77);
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            if(i.isSwimming() || i.getRemainingAir() < i.getMaximumAir())
            {
                xp(i, 9.7);
            }
        }
    }
}
