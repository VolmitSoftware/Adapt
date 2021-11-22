package com.volmit.adapt.api.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.BukkitGson;
import com.volmit.adapt.util.DirtyString;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.JSONTokener;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public interface DataItem<T> {
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
        if(stack != null
            && stack.getType().equals(getMaterial())
            && stack.getItemMeta() != null)
        {
           String r = stack.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(Adapt.instance, getType().getCanonicalName().hashCode() + ""), PersistentDataType.STRING);
           if(r != null)
           {
               return BukkitGson.gson.fromJson(r, getType());
           }
        }

        return null;
    }

    default void setData(ItemStack item, T t)
    {
        item.setItemMeta(withData(t).getItemMeta());
    }

    default ItemStack withData(T t)
    {
        ItemStack item = blank();
        ItemMeta meta = item.getItemMeta();

        if(meta == null)
        {
            return null;
        }

        applyMeta(t, meta);
        KList<String> lore = new KList<>();
        applyLore(t, lore);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(Adapt.instance, getType().getCanonicalName().hashCode() + ""), PersistentDataType.STRING, BukkitGson.gson.toJson(t));
        item.setItemMeta(meta);
        return item;
    }
}
