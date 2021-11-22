package com.volmit.adapt.util;

import com.google.gson.Gson;

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
        return HiddenStringUtils.hasHiddenString(data);
    }

    public static String write(String data)
    {
        String c = LZString.compress(data);
        data = c.length() < data.length() ? "!" + c : data;

        return HiddenStringUtils.encodeString(data);
    }

    public static String read(String data)
    {
        return HiddenStringUtils.extractHiddenString(data.startsWith("!") ? LZString.decompress(data.substring(1)) : data);
    }
}
