package com.volmit.adapt.api.version;

import com.volmit.adapt.api.potion.PotionBuilder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.function.Consumer;

public interface IBindings extends Listener {

    IAttribute getAttribute(Player player, Attribute modifier);

    void addEntityMountListener(Consumer<Player> consumer);

    void addEntityDismountListener(Consumer<Player> consumer);

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
}
