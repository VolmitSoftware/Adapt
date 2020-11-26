package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.skill.SkillLine;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.RNG;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillCrafting extends SimpleSkill {
    public SkillCrafting() {
        super("crafting");
        setColor(C.YELLOW);
        setBarColor(BarColor.YELLOW);
        setInterval(3700);
    }

    @EventHandler
    public void on(CraftItemEvent e)
    {
        xp((Player)e.getWhoClicked(), 35);
        dropXP(e.getWhoClicked().getLocation(), RNG.r.i(31, 67));
    }

    @Override
    public void onTick() {

    }
}
