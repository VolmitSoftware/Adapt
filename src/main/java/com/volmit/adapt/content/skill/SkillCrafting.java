package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;

public class SkillCrafting extends SimpleSkill {
    public SkillCrafting() {
        super("crafting");
        setColor(C.YELLOW);
        setBarColor(BarColor.YELLOW);
        setInterval(3700);
        setIcon(Material.CRAFTING_TABLE);
    }

    @EventHandler
    public void on(CraftItemEvent e)
    {
        xp((Player)e.getWhoClicked(), 37);
    }

    @EventHandler
    public void on(FurnaceSmeltEvent e)
    {
        xp(e.getBlock().getLocation(), 120, 16, 1000);
    }

    @Override
    public void onTick() {

    }
}
