package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.HunterAdrenaline;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SkillHunter extends SimpleSkill {
    public SkillHunter() {
        super("hunter", "\u2620");
        setColor(C.RED);
        setBarColor(BarColor.RED);
        setInterval(4150);
        setIcon(Material.BONE);
        registerAdaptation(new HunterAdrenaline());
    }

    @EventHandler
    public void on(BlockBreakEvent e)
    {
        if(e.getBlock().getType().equals(Material.TURTLE_EGG))
        {
            xp(e.getBlock().getLocation(), 125, 9, 1000);
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent e)
    {
        if(e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock().getType().equals(Material.TURTLE_EGG))
        {
            xp(e.getClickedBlock().getLocation(), 125, 9, 1000);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e)
    {
        if(e.getEntity().getKiller() != null && e.getEntity().getKiller() != null)
        {
            xp(e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 3, 18, 3000);
            xp(e.getEntity().getKiller(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 6);
        }
    }

    @Override
    public void onTick() {

    }
}
