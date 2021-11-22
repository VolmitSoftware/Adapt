package com.volmit.adapt.util;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import org.bukkit.ChatColor;

public class DirtyString
{
    private static final Gson gson = new Gson();

    public static String write(Object data)
    {
        return write(gson.toJson(data));
    }

    public static <T> T fromJson(String data, Class<T> t)
    {
        return gson.fromJson(read(data), t);
    }

    public static boolean has(String data)
    {
        if(!HiddenStringUtils.hasHiddenString(data))
        {
            Adapt.info("Not has in " + data.replaceAll("\\Q"+ ChatColor.COLOR_CHAR+"\\E", "&"));        }
        return HiddenStringUtils.hasHiddenString(data);
    }

    public static String write(String data)
    {
        return HiddenStringUtils.encodeString(data);
    }

    public static String read(String data)
    {
        return HiddenStringUtils.extractHiddenString(data);
    }
}

