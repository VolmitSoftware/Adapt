package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class SkillEnchanting extends SimpleSkill {
    public SkillEnchanting() {
        super("enchanting");
        setColor(C.LIGHT_PURPLE);
        setBarColor(BarColor.PURPLE);
        setInterval(3700);
        setIcon(Material.KNOWLEDGE_BOOK);
    }

    @EventHandler
    public void on(EnchantItemEvent e)
    {
        xp(e.getEnchanter(), 680 * e.getEnchantsToAdd().values().stream().mapToInt((i) -> i).sum());
    }

    @Override
    public void onTick() {

    }
}
