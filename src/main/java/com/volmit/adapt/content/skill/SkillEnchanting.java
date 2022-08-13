package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingLapisReturn;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingQuickEnchant;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingXPReturn;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class SkillEnchanting extends SimpleSkill<SkillEnchanting.Config> {
    public SkillEnchanting() {
        super("enchanting", Adapt.dLocalize("Skill", "Enchanting", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setDescription(Adapt.dLocalize("Skill", "Enchanting", "Description"));
        setInterval(3700);
        setIcon(Material.KNOWLEDGE_BOOK);
        registerAdaptation(new EnchantingQuickEnchant());
        registerAdaptation(new EnchantingLapisReturn());
        registerAdaptation(new EnchantingXPReturn()); //
    }

    @EventHandler
    public void on(EnchantItemEvent e) {
        xp(e.getEnchanter(), getConfig().enchantPowerXPMultiplier * e.getEnchantsToAdd().values().stream().mapToInt((i) -> i).sum());
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.items", 1);
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.power", e.getEnchantsToAdd().values().stream().mapToInt(i -> i).sum());
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.levels.spent", e.getExpLevelCost());
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double enchantPowerXPMultiplier = 250;
    }
}
