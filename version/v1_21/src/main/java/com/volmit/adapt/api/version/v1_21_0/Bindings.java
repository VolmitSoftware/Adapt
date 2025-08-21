package com.volmit.adapt.api.version.v1_21_0;

import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.api.version.IBindings;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
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

        meta.setBasePotionType(builder.getBaseType());
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
