package com.volmit.adapt.api.version;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.api.potion.PotionBuilder.Type;
import com.volmit.adapt.util.CustomModel;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface IBindings extends Listener {

    default void applyModel(CustomModel model, ItemMeta meta) {
        meta.setCustomModelData(model.model());
    }

    IAttribute getAttribute(Attributable attributable, Attribute modifier);

    default ItemStack buildPotion(PotionBuilder builder) {
        ItemStack stack = builder.getBaseItem();
        if (stack == null) stack = new ItemStack(builder.getType().getMaterial());
        else if (stack.getType() != builder.getType().getMaterial()) stack.setType(builder.getType().getMaterial());
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        assert meta != null;
        meta.clearCustomEffects();
        builder.getEffects().forEach(e -> meta.addCustomEffect(e, true));
        if (builder.getColor() != null)
            meta.setColor(builder.getColor());
        stack.setItemMeta(meta);

        Adapt.platform.editItem(stack)
                .lore(builder.getLore())
                .customName(builder.getName())
                .build();
        return stack;
    }

    default PotionBuilder editPotion(ItemStack stack) {
        Type type = null;
        for (final var val : Type.values()) {
            if (val.getMaterial() == stack.getType()) {
                type = val;
                break;
            }
        }

        if (type == null) {
            throw new IllegalArgumentException("Invalid potion type!");
        }
        final var editor = Adapt.platform.editItem(stack);
        final var builder = PotionBuilder.of(type)
                .setBaseItem(stack);

        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        assert meta != null;
        builder.setBaseType(meta.getBasePotionType())
                .setLore(editor.lore())
                .setColor(meta.getColor())
                .setName(editor.customName());
        for (var effect : meta.getCustomEffects()) {
            builder.addEffect(effect);
        }

        return builder;
    }

    @Unmodifiable
    List<EntityType> getInvalidDamageableEntities();
}
