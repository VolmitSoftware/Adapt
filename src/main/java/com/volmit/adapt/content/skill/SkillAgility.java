package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillAgility extends SimpleSkill {
    public SkillAgility() {
        super("agility");
        setColor(C.GREEN);
        setBarColor(BarColor.GREEN);
        setInterval(3500);
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(e.getPlayer().isSprinting() && e.getFrom().getWorld().equals(e.getTo().getWorld()) && e.getTo().distanceSquared(e.getFrom()) > 0)
        {
            xp(e.getPlayer(), 1);
        }
    }

    @Override
    public void onTick() {

    }
}
