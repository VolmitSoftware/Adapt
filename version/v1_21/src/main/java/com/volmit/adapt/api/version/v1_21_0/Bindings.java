package com.volmit.adapt.api.version.v1_21_0;

import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.api.version.IBindings;
import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class Bindings implements IBindings {
    private final Set<Consumer<Player>> mountListeners = new HashSet<>();
    private final Set<Consumer<Player>> dismountListeners = new HashSet<>();

    @Override
    public IAttribute getAttribute(Player player, Attribute modifier) {
        return Optional.ofNullable(player.getAttribute(modifier))
                .map(AttributeImpl::new)
                .orElse(null);
    }

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

        PotionType type = builder.getBaseType();
        if (type == null) {
            meta.setBasePotionType(null);
        } else if (builder.isExtended()) {
            meta.setBasePotionType(Reflect.getEnum(PotionType.class, "LONG_"+type.name()).orElse(type));
        } else if (builder.isUpgraded()) {
            meta.setBasePotionType(Reflect.getEnum(PotionType.class, "STRONG_"+type.name()).orElse(type));
        } else {
            meta.setBasePotionType(type);
        }
        
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
