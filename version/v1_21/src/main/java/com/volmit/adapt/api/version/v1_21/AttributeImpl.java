package com.volmit.adapt.api.version.v1_21;

import com.volmit.adapt.api.version.IAttribute;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.UUID;

public record AttributeImpl(AttributeInstance instance) implements IAttribute {

    @Override
    @SuppressWarnings("all")
    public void addAttributeModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        instance.getModifiers().add(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));
    }

    @Override
    public boolean hasAttributeModifier(UUID uuid, NamespacedKey key) {
        return instance.getModifiers()
                .stream()
                .anyMatch(m -> m.getKey().equals(key));
    }

    @Override
    public void removeAttributeModifier(UUID uuid, NamespacedKey key) {
        instance.getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(key))
                .forEach(instance::removeModifier);
    }
}
