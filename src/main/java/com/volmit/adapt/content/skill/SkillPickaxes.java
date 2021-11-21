package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.PickaxesChisel;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillPickaxes extends SimpleSkill {
    public SkillPickaxes() {
        super("pickaxes", "\u26CF");
        setColor(C.GOLD);
        setBarColor(BarColor.YELLOW);
        setInterval(2750);
        setIcon(Material.NETHERITE_PICKAXE);
        registerAdaptation(new PickaxesChisel());
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getDamager() instanceof Player)
        {
            AdaptPlayer a = getPlayer((Player) e.getDamager());
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if(isPickaxe(hand))
            {
                xp(a.getPlayer(), 13.26 * e.getDamage());
            }
        }
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

    public double getValue(Material type) {
        double value = super.getValue(type) * 0.125;
        value += Math.min(9, type.getHardness());
        value += Math.min(10, type.getBlastResistance());
        switch(type)
        {
            case COAL_ORE:
                value += 101;
                break;
            case IRON_ORE:
                value += 251;
                break;
            case GOLD_ORE:
                value += 651;
                break;
            case LAPIS_ORE:
                value += 851;
                break;
            case DIAMOND_ORE:
                value += 1451;
                break;
            case EMERALD_ORE:
                value += 780;
                break;
            case NETHER_GOLD_ORE:
                value += 781;
                break;
            case NETHER_QUARTZ_ORE:
                value += 111;
                break;
            case REDSTONE_ORE:
                value += 281;
                break;
            case DEEPSLATE_COAL_ORE:
                value += 201;
                break;
            case DEEPSLATE_IRON_ORE:
                value += 351;
                break;
            case DEEPSLATE_GOLD_ORE:
                value += 751;
                break;
            case DEEPSLATE_LAPIS_ORE:
                value += 951;
                break;
            case DEEPSLATE_DIAMOND_ORE:
                value += 1551;
                break;
            case DEEPSLATE_EMERALD_ORE:
                value += 880;
                break;
            case DEEPSLATE_REDSTONE_ORE:
                value += 381;
                break;
        }

        return value * 0.48;
    }

    @Override
    public void onTick() {

    }
}
