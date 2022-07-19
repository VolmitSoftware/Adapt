package com.volmit.adapt.content.item;

import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

@AllArgsConstructor
@Data
public class OmniTool implements DataItem<OmniTool.Data> {
    public static OmniTool io = new OmniTool();

    @Override
    public Material getMaterial() {
        return Material.DISC_FRAGMENT_5;
    }

    @Override
    public Class<Data> getType() {
        return OmniTool.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        Random r = new Random();
        int i = r.nextInt(99999);
        lore.add(C.UNDERLINE + "OMNITOOL-305");
        lore.add(C.LIGHT_PURPLE + "Shift-Right Click " + C.GRAY + "to open the tool!");
        lore.add(C.GRAY + "All tools put in here will be stored within the tool!");
        lore.add(C.ITALIC + "An Astral Utility Container, Model# " + i);
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName("T.O.O.L");

    }

    public static List<String> getItems(ItemStack stack) {
        if (io.getData(stack) != null) {
            return io.getData(stack).getItems();
        }

        return null;
    }

    public static void setData(ItemStack item, List<String> t) {
        io.setData(item, new OmniTool.Data(t));
    }

    public static ItemStack withData(List<String> t) {
        return io.withData(new OmniTool.Data(t));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private List<String> items;

        public static OmniTool.Data at(List<String> l) {
            return new OmniTool.Data(l);
        }
    }
}
