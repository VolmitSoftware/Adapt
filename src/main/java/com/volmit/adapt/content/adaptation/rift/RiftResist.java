package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;


public class RiftResist extends SimpleAdaptation<RiftResist.Config> {
    public RiftResist() {
        super("rift-resist");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("RiftResistance.Description"));
        setDisplayName(Adapt.dLocalize("RiftResistance.Name"));
        setIcon(Material.SCULK_VEIN);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9288);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + Adapt.dLocalize("RiftResistance.Lore1"));
        v.addLore(C.UNDERLINE + Adapt.dLocalize("RiftResistance.Lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (e.getAction() == Action.RIGHT_CLICK_AIR) {
            switch (hand.getType()) {
                case ENDER_EYE, ENDER_PEARL -> {
                    xp(e.getPlayer(), 3);
                    riftResistStackAdd(p, 80, 10);
                }
            }
        }

    }

    static void riftResistStackAdd(Player p, int duration, int amplifier) {
        if (p.getLocation().getWorld() == null) {
            return;
        }
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.24f);
        p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 1000f, 0.01f);
        p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1000f, 0.01f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, duration, amplifier, true, false, false));
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
        int baseCost = 3;
        double costFactor = 1;
        int maxLevel = 1;
        int initialCost = 5;
    }
}