package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.EnchantingQuickEnchant;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class SkillEnchanting extends SimpleSkill {
    public SkillEnchanting() {
        super("enchanting", "\u269C");
        setColor(C.LIGHT_PURPLE);
        setDescription("Very few can bind magic to reality");
        setInterval(3700);
        setIcon(Material.KNOWLEDGE_BOOK);
        registerAdaptation(new EnchantingQuickEnchant());
    }

    @EventHandler
    public void on(EnchantItemEvent e) {
        xp(e.getEnchanter(), 680 * e.getEnchantsToAdd().values().stream().mapToInt((i) -> i).sum());
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.items", 1);
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.power", e.getEnchantsToAdd().values().stream().mapToInt(i -> i).sum());
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.levels.spent", e.getExpLevelCost());
    }

    @Override
    public void onTick() {

    }
}
