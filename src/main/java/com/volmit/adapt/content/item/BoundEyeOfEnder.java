package com.volmit.adapt.content.item;

import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundEyeOfEnder implements DataItem<BoundEyeOfEnder.Data> {
    public static BoundEyeOfEnder io = new BoundEyeOfEnder();

    @Override
    public Material getMaterial() {
        return Material.ENDER_EYE;
    }

    @Override
    public Class<Data> getType() {
        return BoundEyeOfEnder.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.WHITE + "Ocular Anchor");
        lore.add(C.LIGHT_PURPLE + "Right Click " + C.GRAY + "to consume and teleport");
        lore.add(C.LIGHT_PURPLE + "Shift + Left Click " + C.GRAY + "to bind/unbind");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        meta.setDisplayName("Ocular Anchor");
    }

    public static Location getLocation(ItemStack stack) {
        if(io.getData(stack) != null) {
            return io.getData(stack).getLocation();
        }

        return null;
    }

    public static ItemStack setData(ItemStack item, Location t) {
        return io.setData(item, new Data(t));
    }

    public static ItemStack withData(Location t) {
        return io.withData(new Data(t));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private Location location;

        public static BoundEyeOfEnder.Data at(Location l) {
            return new BoundEyeOfEnder.Data(l);
        }
    }
}
