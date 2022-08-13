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
import org.bukkit.inventory.ItemStack;

public class EnchantingLapisReturn extends SimpleAdaptation<EnchantingLapisReturn.Config> {

    public EnchantingLapisReturn() {
        super("enchanting-lapis-return");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Enchanting", "LapisReturn", "Description"));
        setDisplayName(Adapt.dLocalize("Enchanting", "LapisReturn", "Name"));
        setIcon(Material.LAPIS_LAZULI);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Enchanting", "LapisReturn", "Lore1"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EnchantItemEvent e) {
        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }
        int xp = e.getExpLevelCost();
        xp = xp + getLevel(p); // Add a level for each enchant
        e.setExpLevelCost(xp);
        int lapis = (int) (Math.random() * 1);
        lapis = lapis + (int) (Math.random() * (getLevel(p) + 1));
        p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.LAPIS_LAZULI, lapis));
        xp(p, 15);


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
        int baseCost = 1;
        int maxLevel = 7;
        int initialCost = 2;
        double costFactor = 1.25;
    }
}
