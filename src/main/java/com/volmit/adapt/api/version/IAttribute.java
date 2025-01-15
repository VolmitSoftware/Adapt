package com.volmit.adapt.api.version;

import com.volmit.adapt.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;

import java.util.Optional;
import java.util.UUID;

public interface IAttribute {

    double getValue();

    double getDefaultValue();

    double getBaseValue();

    default void setModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        removeModifier(uuid, key);
        addModifier(uuid, key, amount, operation);
    }

    void addModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation);

    void removeModifier(UUID uuid, NamespacedKey key);

    KList<Modifier> getModifier(UUID uuid, NamespacedKey key);

    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    class Modifier {
        private final UUID uuid;
        private final NamespacedKey key;
        @Getter
        private final double amount;
        @Getter
        private final AttributeModifier.Operation operation;

        public Optional<UUID> getUUID() {
            return Optional.ofNullable(uuid);
        }

        public Optional<NamespacedKey> getKey() {
            return Optional.ofNullable(key);
        }
    }
}
