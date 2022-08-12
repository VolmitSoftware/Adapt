package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ExcavationHaste extends SimpleAdaptation<ExcavationHaste.Config> {
    public ExcavationHaste() {
        super("excavation-haste");
        registerConfiguration(ExcavationHaste.Config.class);
        setDisplayName(Adapt.dLocalize("Excavation", "ExcavationHaste", "Name"));
        setDescription(Adapt.dLocalize("Excavation", "ExcavationHaste", "Description"));
        setIcon(Material.GOLDEN_PICKAXE);
        setInterval(10101);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Excavation", "ExcavationHaste", "Lore1"));
        v.addLore(C.GREEN + "" + (level) + C.GRAY + Adapt.dLocalize("Excavation", "ExcavationHaste", "Lore2"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(BlockDamageEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        Player p = e.getPlayer();
        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 8, getLevel(p), true, false, true));


    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
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
