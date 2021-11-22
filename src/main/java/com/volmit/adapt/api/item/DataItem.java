package com.volmit.adapt.api.item;

import com.google.gson.Gson;
import com.volmit.adapt.util.DirtyString;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public interface DataItem<T> {
    Gson _gson = new Gson();

    Material getMaterial();

    Class<T> getType();

    void applyLore(T data, List<String> lore);

    void applyMeta(T data, ItemMeta meta);

    default ItemStack blank()
    {
        return new ItemStack(getMaterial());
    }

    default T getData(ItemStack stack)
    {
        return J.attemptResult(() -> stack != null
            && stack.getType().equals(getMaterial())
            && stack.getItemMeta() != null && stack.getItemMeta().getLore() != null
            && stack.getItemMeta().getLore().size() > 0
            && DirtyString.has(stack.getItemMeta().getLore().get(0))
            ? DirtyString.fromJson(stack.getItemMeta().getLore().get(0), getType())
            : null, null);
    }

    default void setData(ItemStack item, T t)
    {
        item.setItemMeta(withData(t).getItemMeta());
    }

    default ItemStack withData(T t)
    {
        ItemStack item = blank();
        ItemMeta meta = item.getItemMeta();
        applyMeta(t, meta);
        KList<String> lore = new KList<>();
        KList<String> ladd = new KList<>();
        lore.add(DirtyString.write(t));
        applyLore(t, ladd);
        lore.addAll(ladd);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
