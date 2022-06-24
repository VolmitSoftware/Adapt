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
public class BoundRiftKey implements DataItem<BoundRiftKey.Data> {
    public static BoundRiftKey io = new BoundRiftKey();

    @Override
    public Material getMaterial() {
        return Material.TRIPWIRE_HOOK;
    }

    @Override
    public Class<Data> getType() {
        return BoundRiftKey.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(0, C.LIGHT_PURPLE + "You know what  " + C.GRAY + "to do");
        lore.add(1, "null");
        lore.add(2, "null");
        lore.add(3, "Astral Key");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName("Astral Key");

    }

    public static Location getLocation(ItemStack stack) {
        if(io.getData(stack) != null) {
            return io.getData(stack).getLocation();
        }

        return null;
    }


    public static void setData(ItemStack item, Location t) {
        io.setData(item, new BoundRiftKey.Data(t));
    }


    public static ItemStack withData(Location t) {
        return io.withData(new Data(t));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private Location location;
        public static BoundRiftKey.Data at(Location l) {
            return new BoundRiftKey.Data(l);
        }
    }
}
