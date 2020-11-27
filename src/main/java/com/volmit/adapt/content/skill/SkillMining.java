package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillMining extends SimpleSkill {
    public SkillMining() {
        super("axes");
        setColor(C.YELLOW);
        setBarColor(BarColor.YELLOW);
        setInterval(2750);
    }

    @EventHandler
    public void on(BlockBreakEvent e)
    {
        if(isPickaxe(e.getPlayer().getInventory().getItemInMainHand()))
        {
            double v = getValue(e.getBlock().getType());

            xp(e.getPlayer(), v);
        }
    }

    private double getValue(Material type) {
        double value = 0;
        value += Math.min(9, type.getHardness());
        value += Math.min(10, type.getBlastResistance());
        switch(type)
        {
            case COAL_ORE:
                value += 10;
                break;
            case IRON_ORE:
                value += 25;
                break;
            case GOLD_ORE:
                value += 65;
                break;
            case LAPIS_ORE:
                value += 85;
                break;
            case DIAMOND_ORE:
                value += 145;
                break;
            case EMERALD_ORE:
                value += 167;
                break;
            case NETHER_GOLD_ORE:
                value += 78;
                break;
            case NETHER_QUARTZ_ORE:
                value += 11;
                break;
            case REDSTONE_ORE:
                value += 28;
                break;
        }

        return value;
    }

    @Override
    public void onTick() {

    }
}
