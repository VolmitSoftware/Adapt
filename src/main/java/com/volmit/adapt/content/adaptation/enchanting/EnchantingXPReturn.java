package com.volmit.adapt.content.adaptation.enchanting;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class EnchantingXPReturn extends SimpleAdaptation<EnchantingXPReturn.Config> {

    public EnchantingXPReturn() {
        super("enchanting-xp-return");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Enchanting", "EnchantingXpReturn", "Description"));
        setDisplayName(Adapt.dLocalize("Enchanting", "EnchantingXpReturn", "Name"));
        setIcon(Material.EXPERIENCE_BOTTLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Enchanting", "EnchantingXpReturn", "Lore1"));
        v.addLore(C.GREEN + "" + getConfig().xpReturn * (level * level) + Adapt.dLocalize("Enchanting", "EnchantingXpReturn", "Lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EnchantItemEvent e) {
        int level = getLevel(e.getEnchanter());
        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }
        xp(p, 5);
        p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(getConfig().xpReturn * (level * level));

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
        public int xpReturn = 2;
        boolean enabled = true;
        int baseCost = 1;
        int maxLevel = 7;
        int initialCost = 2;
        double costFactor = 1.97;
    }
}
