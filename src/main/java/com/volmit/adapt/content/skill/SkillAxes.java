package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.AxesChop;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillAxes extends SimpleSkill {
    public SkillAxes() {
        super("axes");
        setColor(C.YELLOW);
        setBarColor(BarColor.YELLOW);
        setInterval(2150);
        registerAdaptation(new AxesChop());
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getDamager() instanceof Player)
        {
            AdaptPlayer a = getPlayer((Player) e.getDamager());
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if(isAxe(hand))
            {
                xp(a.getPlayer(), 13.26 * e.getDamage());
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e)
    {
        if(isAxe(e.getPlayer().getInventory().getItemInMainHand()))
        {
            double v = getValue(e.getBlock().getType());

            xp(e.getPlayer(), v);
        }
    }

    private double getValue(Material type) {
        double value = 0;
        value += Math.min(9, type.getHardness());
        value += Math.min(10, type.getBlastResistance());

        if(type.name().endsWith("_LOG") || type.name().endsWith("_WOOD"))
        {
            value += 9.67;
        }
        if(type.name().endsWith("_PLANKS"))
        {
            value += 4.67;
        }
        if(type.name().endsWith("_LEAVES"))
        {
            value += 3.11;
        }

        return value;
    }


    @Override
    public void onTick() {

    }
}
