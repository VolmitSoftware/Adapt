package com.volmit.adapt.api.version;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;

import java.util.UUID;

public interface IAttribute {

    default void setAttributeModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        removeAttributeModifier(uuid, key);
        addAttributeModifier(uuid, key, amount, operation);
    }

    void addAttributeModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation);

    boolean hasAttributeModifier(UUID uuid, NamespacedKey key);

    void removeAttributeModifier(UUID uuid, NamespacedKey key);
}
