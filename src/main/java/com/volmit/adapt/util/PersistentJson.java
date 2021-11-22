package com.volmit.adapt.util;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.cyberpwn.spatial.container.DataContainer;

import java.util.Locale;
import java.util.Map;

public class PersistentJson
{
    private static final Gson gson = new Gson();

    public static void write(PersistentDataContainer c, String key, Object data)
    {
        c.set(new NamespacedKey(Adapt.instance, key), PersistentDataType.STRING, gson.toJson(data));
    }

    private static <T> T fromJSON(PersistentDataContainer c, String key, Class<T> type)
    {
        String s = c.get(new NamespacedKey(Adapt.instance, key), PersistentDataType.STRING);

        if(s == null)
        {
            return gson.fromJson(s, type);
        }

        return null;
    }
}
