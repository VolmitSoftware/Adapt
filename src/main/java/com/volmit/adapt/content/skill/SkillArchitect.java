package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;

public class SkillArchitect extends SimpleSkill {
    public SkillArchitect() {
        super("architect");
        setColor(C.AQUA);
        setBarColor(BarColor.BLUE);
        setInterval(3700);
        setIcon(Material.IRON_BARS);
    }

    @EventHandler
    public void on(BlockPlaceEvent e)
    {
        xp(e.getPlayer(), 5);
    }

    @Override
    public void onTick() {

    }
}
