package com.volmit.adapt.content.adaptation.hunter;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

public class HunterDropToInventory extends SimpleAdaptation<HunterDropToInventory.Config> {
    public HunterDropToInventory() {
        super("pickaxe-drop-to-inventory");
        registerConfiguration(HunterDropToInventory.Config.class);
        setDescription(Adapt.dLocalize("ww", "DropToInventory", "Description"));
        setDisplayName(Adapt.dLocalize("Hunter", "DropToInventory", "Name"));
        setIcon(Material.DIRT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Pickaxe", "DropToInventory", "Lore1"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockDropItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (ItemListings.toolSwords.contains(e.getPlayer().getInventory().getItemInMainHand().getType())) {
            List<Item> items = e.getItems().copy();
            e.getItems().clear();
            for (Item i : items) {
                p.playSound(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.05f, 0.01f);
                xp(p, 2);
                if (!p.getInventory().addItem(i.getItemStack()).isEmpty()) {
                    p.getWorld().dropItem(p.getLocation(), i.getItemStack());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDeathEvent e) {
        LivingEntity killed = e.getEntity();

        if(killed.getKiller() != null && hasAdaptation(killed.getKiller())) {
            Player p = killed.getKiller();
            if (!hasAdaptation(p)) {
                return;
            }
            if (p.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (ItemListings.toolSwords.contains(p.getInventory().getItemInMainHand().getType())) {
                xp(p, 2);
            }

        }
    }


    @Override
    public void onTick() {
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 1;
        int maxLevel = 1;
        int initialCost = 2;
        double costFactor = 1;
    }
}
