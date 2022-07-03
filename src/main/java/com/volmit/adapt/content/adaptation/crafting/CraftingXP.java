package com.volmit.adapt.content.adaptation.crafting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;


public class CraftingXP extends SimpleAdaptation<CraftingXP.Config> {
    public CraftingXP() {
        super("crafting-xp");
        registerConfiguration(CraftingXP.Config.class);
        setDisplayName("Crafting XP");
        setDescription("Gain passive XP when crafting");
        setIcon(Material.EXPERIENCE_BOTTLE);
        setInterval(10101);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @EventHandler
    public void on(CraftItemEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (hasAdaptation(p) && !e.getRecipe().equals(Material.AIR)) {
            p.giveExp(e.getInventory().getResult().getAmount() * getLevel(p));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "Gain XP when crafting");
    }

    @Override
    public void onTick() {
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 3;
        double costFactor = 0.3;
        int maxLevel = 7;
    }
}
