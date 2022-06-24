package com.volmit.adapt.content.adaptation.enchanting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class EnchantingLapisReturn extends SimpleAdaptation<EnchantingLapisReturn.Config> {

    public EnchantingLapisReturn() {
        super("enchant-lapis");
        registerConfiguration(Config.class);
        setDescription("At the cost of 1 more level of XP, and has a chance to give you free lapis in return");
        setIcon(Material.LAPIS_LAZULI);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private int getTotalLevelCount(int level) {
        return level + (level > getConfig().maxPowerBonusLimit ? level / getConfig().maxPowerBonus1PerLevels : 0);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getTotalLevelCount(level) + C.GRAY + " Max Combined Levels");
    }

    @EventHandler
    public void on(EnchantItemEvent e) {
        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }

        int xp = e.getExpLevelCost();

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
        int baseCost = 6;
        int maxLevel = 7;
        int initialCost = 8;
        double costFactor = 1.355;
        int maxPowerBonusLimit = 4;
        int maxPowerBonus1PerLevels = 3;
    }
}
