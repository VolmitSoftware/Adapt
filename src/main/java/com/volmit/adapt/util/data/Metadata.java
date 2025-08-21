package com.volmit.adapt.util.data;

import com.volmit.adapt.Adapt;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.Metadatable;

import static java.util.UUID.randomUUID;

public class Metadata {
    private static final String UUID = randomUUID().toString();
    public static final Metadata VEIN_MINED = new Metadata("vein-mined", false);

    private final String key;
    private final boolean defaultValue;

    private Metadata(String prefix, boolean defaultValue) {
        this.key = UUID + prefix;
        this.defaultValue = defaultValue;
    }

    public boolean has(Metadatable metadatable) {
        return metadatable.hasMetadata(key);
    }

    public boolean get(Metadatable metadatable) {
        return metadatable.hasMetadata(key) ? metadatable.getMetadata(key).get(0).asBoolean() : defaultValue;
    }

    public void set(Metadatable metadatable, boolean value) {
        if (value != defaultValue) metadatable.setMetadata(key, new FixedMetadataValue(Adapt.instance, value));
        else metadatable.removeMetadata(key, Adapt.instance);
    }

    public void add(Metadatable metadatable) {
        set(metadatable, !defaultValue);
    }

    public void remove(Metadatable metadatable) {
        set(metadatable, defaultValue);
    }
}
