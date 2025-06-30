package com.volmit.adapt.api.version.v1_20_5;

import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.api.version.IBindings;
import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

public class Bindings implements IBindings {

    @Override
    public IAttribute getAttribute(Attributable attributable, Attribute modifier) {
        return Optional.ofNullable(attributable.getAttribute(modifier))
                .map(AttributeImpl::new)
                .orElse(null);
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

    @Override
    @Unmodifiable
    public List<EntityType> getInvalidDamageableEntities() {
        return List.of(
                EntityType.ARMOR_STAND,
                EntityType.BOAT,
                EntityType.ITEM_FRAME,
                EntityType.MINECART,
                EntityType.CHEST_MINECART,
                EntityType.COMMAND_BLOCK_MINECART,
                EntityType.FURNACE_MINECART,
                EntityType.HOPPER_MINECART,
                EntityType.SPAWNER_MINECART,
                EntityType.PAINTING,
                EntityType.CHEST_BOAT,
                EntityType.LEASH_KNOT,
                EntityType.EVOKER_FANGS,
                EntityType.MARKER
        );
    }
}
