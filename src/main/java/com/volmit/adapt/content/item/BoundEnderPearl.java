package com.volmit.adapt.content.item;

import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.libs.org.eclipse.aether.version.VersionRange;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundEnderPearl implements DataItem<BoundEnderPearl.Data>
{
    public static BoundEnderPearl io = new BoundEnderPearl();

    @Override
    public Material getMaterial() {
        return Material.ENDER_PEARL;
    }

    @Override
    public Class<Data> getType() {
        return BoundEnderPearl.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.LIGHT_PURPLE + "Right Click " + C.GRAY + "to access the bound Inventory");
        lore.add(C.LIGHT_PURPLE + "Shift + Left Click " + C.GRAY + "to unbind");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    }

    public static @Nullable Block getChest(ItemStack stack)
    {
        if (io.getData(stack) != null){
            return io.getData(stack).getChest();

        }else {
            return null;
        }
    }

    public static void setData(ItemStack item, Block t)
    {
        io.setData(item, new Data(t));
    }

    public static ItemStack withData(Block t)
    {
        return io.withData(new Data(t));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data
    {
        private Block chest;

        public static BoundEnderPearl.Data at(Block l)
        {
            return new BoundEnderPearl.Data(l);
        }
    }
}
