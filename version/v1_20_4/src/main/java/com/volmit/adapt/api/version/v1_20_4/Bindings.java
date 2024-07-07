package com.volmit.adapt.api.version.v1_20_4;

import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.version.IBindings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class Bindings implements IBindings {
    private final Set<Consumer<Player>> mountListeners = new HashSet<>();
    private final Set<Consumer<Player>> dismountListeners = new HashSet<>();

    @Override
    public void addEntityMountListener(Consumer<Player> consumer) {
        mountListeners.add(consumer);
    }

    @Override
    public void addEntityDismountListener(Consumer<Player> consumer) {
        dismountListeners.add(consumer);
    }

    @Override
    public ItemStack buildPotion(PotionBuilder builder) {
        ItemStack stack = IBindings.super.buildPotion(builder);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        assert meta != null;

        meta.setBasePotionData(new PotionData(builder.getBaseType(), builder.isExtended(), builder.isUpgraded()));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void on(EntityMountEvent e) {
        if (e.getEntity() instanceof Player p) {
            mountListeners.forEach(l -> l.accept(p));
        }
    }

    @EventHandler
    public void on(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p) {
            dismountListeners.forEach(l -> l.accept(p));
        }
    }
}
