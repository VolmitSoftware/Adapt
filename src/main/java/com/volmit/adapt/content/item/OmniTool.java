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

import java.util.ArrayList;
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

    public static List<ItemStack> getItems(ItemStack stack) {
        if (io.getData(stack) != null) {
            return io.getData(stack).items();
        }

        return new ArrayList<>();
    }

    public record Data(List<ItemStack> items) { }
}
