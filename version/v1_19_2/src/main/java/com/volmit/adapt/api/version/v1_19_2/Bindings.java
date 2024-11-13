package com.volmit.adapt.api.version.v1_19_2;

import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.api.version.IBindings;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.jetbrains.annotations.Unmodifiable;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class Bindings implements IBindings {
    private final Set<Consumer<Player>> mountListeners = new HashSet<>();
    private final Set<Consumer<Player>> dismountListeners = new HashSet<>();

    @Override
    public IAttribute getAttribute(Attributable attributable, Attribute modifier) {
        return Optional.ofNullable(attributable.getAttribute(modifier))
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

        meta.setBasePotionData(new PotionData(builder.getBaseType(), builder.isExtended(), builder.isUpgraded()));
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    @Unmodifiable
    public List<EntityType> getInvalidDamageableEntities() {
        return List.of(
                EntityType.ARMOR_STAND,
                EntityType.BOAT,
                EntityType.ITEM_FRAME,
                EntityType.MINECART,
                EntityType.MINECART_CHEST,
                EntityType.MINECART_COMMAND,
                EntityType.MINECART_FURNACE,
                EntityType.MINECART_HOPPER,
                EntityType.MINECART_MOB_SPAWNER,
                EntityType.MINECART_TNT,
                EntityType.PAINTING,
                EntityType.CHEST_BOAT,
                EntityType.LEASH_HITCH,
                EntityType.EVOKER_FANGS,
                EntityType.MARKER
        );
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
