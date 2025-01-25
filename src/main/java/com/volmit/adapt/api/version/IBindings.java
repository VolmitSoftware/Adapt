package com.volmit.adapt.api.version;

import com.volmit.adapt.api.potion.PotionBuilder;
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
        ItemStack stack = new ItemStack(builder.getType().getMaterial());
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        assert meta != null;
        builder.getEffects().forEach(e -> meta.addCustomEffect(e, true));
        if (builder.getColor() != null)
            meta.setColor(builder.getColor());
        if (builder.getName() != null)
            meta.setDisplayName("§r" + builder.getName());
        stack.setItemMeta(meta);
        return stack;
    }

    @Unmodifiable
    List<EntityType> getInvalidDamageableEntities();
}
