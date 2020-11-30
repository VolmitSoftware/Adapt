package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.HunterAdrenaline;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SkillHunter extends SimpleSkill {
    public SkillHunter() {
        super("hunter");
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
        if(e.getEntity().getKiller() != null && e.getEntity().getKiller() instanceof Player)
        {
            xp(e.getEntity().getLocation(), e.getEntity().getMaxHealth() * 9, 18, 3000);
        }
    }

    @Override
    public void onTick() {

    }
}
