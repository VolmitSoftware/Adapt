package com.volmit.adapt.content.item;

import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundDevourCandle implements DataItem<BoundDevourCandle.Data> {
    public static BoundDevourCandle io = new BoundDevourCandle();

    @Override
    public Material getMaterial() {
        return Material.BLACK_CANDLE;
    }

    @Override
    public Class<Data> getType() {
        return BoundDevourCandle.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.LIGHT_PURPLE + "Shift Click " + C.GRAY + "to bind/unbind a container");
        lore.add(C.LIGHT_PURPLE + "Right Click " + C.GRAY + "to activate");
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName("Devouring Candle");
    }

    public static Block getBlock(ItemStack stack) {
        if(io.getData(stack) != null) {
            return io.getData(stack).getBlock();
        }

        return null;
    }


    public static void setData(ItemStack item, Block t) {
        io.setData(item, new Data(t));
    }

    public static ItemStack withData(Block t) {
        return io.withData(new Data(t));
    }

    @lombok.Data
    public static class Data {
        public Data(Block block){
            this.block = block;
        }
        private Block block;
        private long d = RNG.r.lmax();

        public static BoundDevourCandle.Data at(Block l) {
            return new BoundDevourCandle.Data(l);
        }
    }

}
