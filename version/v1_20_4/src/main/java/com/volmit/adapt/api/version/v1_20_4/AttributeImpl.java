package com.volmit.adapt.api.version.v1_20_4;

import com.volmit.adapt.api.version.IAttribute;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.util.UUID;
import java.util.function.Predicate;

public record AttributeImpl(AttributeInstance instance) implements IAttribute {

    @Override
    @SuppressWarnings("all")
    public void addAttributeModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        instance.getModifiers().add(new AttributeModifier(uuid, key.getNamespace() + "-" + key.getKey(), amount, operation));
    }

    @Override
    public boolean hasAttributeModifier(UUID uuid, NamespacedKey key) {
        return instance.getModifiers()
                .stream()
                .anyMatch(filter(uuid, key));
    }

    @Override
    public void removeAttributeModifier(UUID uuid, NamespacedKey key) {
        instance.getModifiers()
                .stream()
                .filter(filter(uuid, key))
                .forEach(instance::removeModifier);
    }

    private Predicate<AttributeModifier> filter(UUID uuid, NamespacedKey key) {
        String name = key.getNamespace() + "-" + key.getKey();
        return m -> m.getUniqueId().equals(uuid) || m.getName().equals(name);
    }
}
