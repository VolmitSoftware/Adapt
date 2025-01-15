package com.volmit.adapt.api.version.v1_20_4;

import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record AttributeImpl(AttributeInstance instance) implements IAttribute {

    private static Modifier wrap(AttributeModifier modifier) {
        return new Modifier(modifier.getUniqueId(), null, modifier.getAmount(), modifier.getOperation());
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
        instance.addModifier(new AttributeModifier(uuid, key.getNamespace() + "-" + key.getKey(), amount, operation));
    }

    @Override
    public void removeModifier(UUID uuid, NamespacedKey key) {
        instance.getModifiers()
                .stream()
                .filter(filter(uuid, key))
                .forEach(instance::removeModifier);
    }

    @Override
    public KList<Modifier> getModifier(UUID uuid, NamespacedKey key) {
        return instance.getModifiers()
                .stream()
                .filter(filter(uuid, key))
                .map(AttributeImpl::wrap)
                .collect(Collectors.toCollection(KList::new));
    }

    private Predicate<AttributeModifier> filter(UUID uuid, NamespacedKey key) {
        String name = key.getNamespace() + "-" + key.getKey();
        return m -> m.getUniqueId().equals(uuid) || m.getName().equals(name);
    }
}
