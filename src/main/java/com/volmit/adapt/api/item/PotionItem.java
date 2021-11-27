package com.volmit.adapt.api.item;

import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public abstract class PotionItem implements DataItem<PotionItem.Data> {
    @Override
    public Class<Data> getType() {
        return Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        lore.add(C.GREEN + "Grants " + data.getType().getName() + " " + Form.toRoman(data.getPower() + 1));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {

    }

    @lombok.Data
    @NoArgsConstructor
    public static class Data {
        private PotionEffectType type;
        private int power;
    }
}
