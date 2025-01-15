package com.volmit.adapt.api.version.v1_21_2;

import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.UUID;
import java.util.stream.Collectors;

public record AttributeImpl(AttributeInstance instance) implements IAttribute {

    private static Modifier wrap(AttributeModifier modifier) {
        return new Modifier(null, modifier.getKey(), modifier.getAmount(), modifier.getOperation());
    }

    @Override
    public double getValue() {
        return instance.getValue();
    }

    @Override
    public double getDefaultValue() {
        return instance.getDefaultValue();
    }

    @Override
    public double getBaseValue() {
        return instance.getBaseValue();
    }

    @Override
    @SuppressWarnings("all")
    public void addModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        instance.addModifier(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));
    }

    @Override
    public void removeModifier(UUID uuid, NamespacedKey key) {
        instance.getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(key))
                .forEach(instance::removeModifier);
    }

    @Override
    public KList<Modifier> getModifier(UUID uuid, NamespacedKey key) {
        return instance.getModifiers()
                .stream()
                .filter(m -> m.getKey().equals(key))
                .map(AttributeImpl::wrap)
                .collect(Collectors.toCollection(KList::new));
    }
}
