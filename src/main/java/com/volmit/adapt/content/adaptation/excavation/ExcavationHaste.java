package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ExcavationHaste extends SimpleAdaptation<ExcavationHaste.Config> {
    public ExcavationHaste() {
        super("excavation-haste");
        registerConfiguration(ExcavationHaste.Config.class);
        setDisplayName("Hasty Excavator");
        setDescription("This will speed up the excavation process, with HASTE!");
        setIcon(Material.GOLDEN_PICKAXE);
        setInterval(10101);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @EventHandler
    public void on(BlockDamageEvent e)  {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        Player p = e.getPlayer();
        if (!e.getBlock().getDrops(e.getItemInHand()).isEmpty()) {
            boolean has = false;
            for (PotionEffectType type : PotionEffectType.values()) {
                if (type.equals(PotionEffectType.FAST_DIGGING)) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 4, getLevel(p), false, false, false));
            }
        }
    }



    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "Gain Haste while excavating");
        v.addLore(C.GREEN + "" +  (level) +C.GRAY+ "x Levels of haste when you start mining ANY block with the right tool" );
        v.addLore(C.ITALIC + "If you already have a haste buff active this will not apply!" );
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
