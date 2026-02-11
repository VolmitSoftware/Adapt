package com.volmit.adapt.api.version;

import com.volmit.adapt.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Collectors;

public record RuntimeAttribute(AttributeInstance instance) implements IAttribute {
    private static final Method GET_KEY_METHOD = findMethod("getKey");
    private static final Method GET_UUID_METHOD = findMethod("getUniqueId");
    private static final Method GET_NAME_METHOD = findMethod("getName");

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
    public void setBaseValue(double baseValue) {
        instance.setBaseValue(baseValue);
    }

    @Override
    public void addModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        instance.addModifier(createModifier(uuid, key, amount, operation));
    }

    @Override
    public boolean hasModifier(UUID uuid, NamespacedKey key) {
        return instance.getModifiers()
                .stream()
                .anyMatch(modifier -> matches(modifier, uuid, key));
    }

    @Override
    public void removeModifier(UUID uuid, NamespacedKey key) {
        instance.getModifiers()
                .stream()
                .filter(modifier -> matches(modifier, uuid, key))
                .toList()
                .forEach(instance::removeModifier);
    }

    @Override
    public KList<Modifier> getModifier(UUID uuid, NamespacedKey key) {
        return instance.getModifiers()
                .stream()
                .filter(modifier -> matches(modifier, uuid, key))
                .map(RuntimeAttribute::wrap)
                .collect(Collectors.toCollection(KList::new));
    }

    private static AttributeModifier createModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        for (Constructor<?> constructor : AttributeModifier.class.getConstructors()) {
            AttributeModifier modifier = tryCreateKeyed(constructor, key, amount, operation);
            if (modifier != null) {
                return modifier;
            }
        }

        String legacyName = key.getNamespace() + "-" + key.getKey();
        for (Constructor<?> constructor : AttributeModifier.class.getConstructors()) {
            AttributeModifier modifier = tryCreateLegacy(constructor, uuid, legacyName, amount, operation);
            if (modifier != null) {
                return modifier;
            }
        }

        throw new IllegalStateException("No compatible AttributeModifier constructor found");
    }

    private static AttributeModifier tryCreateKeyed(Constructor<?> constructor, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        Class<?>[] params = constructor.getParameterTypes();
        if (params.length < 3 || params.length > 4 || params[0] != NamespacedKey.class || params[1] != double.class || params[2] != AttributeModifier.Operation.class) {
            return null;
        }

        Object[] args = new Object[params.length];
        args[0] = key;
        args[1] = amount;
        args[2] = operation;
        if (params.length == 4) {
            Object slot = resolveEnum(params[3]);
            if (slot == null) {
                return null;
            }
            args[3] = slot;
        }

        try {
            return (AttributeModifier) constructor.newInstance(args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static AttributeModifier tryCreateLegacy(Constructor<?> constructor, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        Class<?>[] params = constructor.getParameterTypes();
        if (params.length < 4 || params.length > 5 || params[0] != UUID.class || params[1] != String.class || params[2] != double.class || params[3] != AttributeModifier.Operation.class) {
            return null;
        }

        Object[] args = new Object[params.length];
        args[0] = uuid;
        args[1] = name;
        args[2] = amount;
        args[3] = operation;
        if (params.length == 5) {
            Object slot = resolveEnum(params[4]);
            if (slot == null) {
                return null;
            }
            args[4] = slot;
        }

        try {
            return (AttributeModifier) constructor.newInstance(args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean matches(AttributeModifier modifier, UUID uuid, NamespacedKey key) {
        NamespacedKey modifierKey = readKey(modifier);
        if (modifierKey != null && modifierKey.equals(key)) {
            return true;
        }

        UUID modifierUuid = readUuid(modifier);
        if (modifierUuid != null && modifierUuid.equals(uuid)) {
            return true;
        }

        String modifierName = readName(modifier);
        return modifierName != null && modifierName.equals(key.getNamespace() + "-" + key.getKey());
    }

    private static Modifier wrap(AttributeModifier modifier) {
        return new Modifier(readUuid(modifier), readKey(modifier), modifier.getAmount(), modifier.getOperation());
    }

    private static NamespacedKey readKey(AttributeModifier modifier) {
        if (GET_KEY_METHOD == null) {
            return null;
        }

        try {
            return (NamespacedKey) GET_KEY_METHOD.invoke(modifier);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static UUID readUuid(AttributeModifier modifier) {
        if (GET_UUID_METHOD == null) {
            return null;
        }

        try {
            return (UUID) GET_UUID_METHOD.invoke(modifier);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String readName(AttributeModifier modifier) {
        if (GET_NAME_METHOD == null) {
            return null;
        }

        try {
            return (String) GET_NAME_METHOD.invoke(modifier);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findMethod(String methodName) {
        try {
            return AttributeModifier.class.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object resolveEnum(Class<?> type) {
        if (!type.isEnum()) {
            return null;
        }

        Object any = enumConstant(type, "ANY");
        if (any != null) {
            return any;
        }

        Object hand = enumConstant(type, "HAND");
        if (hand != null) {
            return hand;
        }

        Object[] constants = type.getEnumConstants();
        return constants == null || constants.length == 0 ? null : constants[0];
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        try {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
