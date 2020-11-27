package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillSwords extends SimpleSkill {
    public SkillSwords() {
        super("swords");
        setColor(C.YELLOW);
        setBarColor(BarColor.YELLOW);
        setInterval(2150);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getDamager() instanceof Player)
        {
            AdaptPlayer a = getPlayer((Player) e.getDamager());
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if(isSword(hand))
            {
                xp(a.getPlayer(), 13.26 * e.getDamage());
            }
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            if(isSword(i.getInventory().getItemInMainHand()))
            {
                xp(i, 4.28);
            }
        }
    }
}
