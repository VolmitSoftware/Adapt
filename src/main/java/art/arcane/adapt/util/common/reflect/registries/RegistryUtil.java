package art.arcane.adapt.util.reflect.registries;

import art.arcane.adapt.util.cache.AtomicCache;
import lombok.NonNull;
import manifold.rt.api.util.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@SuppressWarnings("unchecked")
public class RegistryUtil {
    private static final AtomicCache<RegistryLookup> registryLookup = new AtomicCache<>();
    private static final Map<Class<?>, Map<NamespacedKey, Keyed>> KEYED_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Map<NamespacedKey, Object>> ENUM_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Registry<Keyed>> REGISTRY = new HashMap<>();

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @NonNull String... keys) {
        return findOptional(typeClass, keys).orElseThrow(notFound(typeClass, keys));
    }

    @NonNull
    public static <T> Optional<T> findOptional(@NonNull Class<T> typeClass, @NonNull String... keys) {
        return findOptional(typeClass, defaultLookup(), keys);
    }

    @Nullable
    public static <T> T findNullable(@NonNull Class<T> typeClass, @NonNull String... keys) {
        return findOptional(typeClass, defaultLookup(), keys).orElse(null);
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull String... keys) {
        return findOptional(typeClass, lookup, keys).orElseThrow(notFound(typeClass, keys));
    }

    @NonNull
    public static <T> Optional<T> findOptional(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull String... keys) {
        return findOptional(typeClass, lookup, Arrays.stream(keys).map(NamespacedKey::minecraft).toArray(NamespacedKey[]::new));
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        return findOptional(typeClass, keys).orElseThrow(notFound(typeClass, keys));
    }

    @NonNull
    public static <T> Optional<T> findOptional(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        return findOptional(typeClass, defaultLookup(), keys);
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull NamespacedKey... keys) {
        return findOptional(typeClass, lookup, keys).orElseThrow(notFound(typeClass, keys));
    }

    @NonNull
    public static <T> Optional<T> findOptional(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull NamespacedKey... keys) {
        if (keys.length == 0) throw new IllegalArgumentException("Need at least one key");
        Registry<Keyed> registry = null;
        if (Keyed.class.isAssignableFrom(typeClass)) {
            registry = getRegistry(typeClass.asSubclass(Keyed.class));
        }
        if (registry == null) {
            registry = REGISTRY.computeIfAbsent(typeClass, t -> Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .filter(field -> ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(t))
                    .map(field -> {
                        try {
                            return (Registry<Keyed>) field.get(null);
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }

        if (registry != null) {
            for (NamespacedKey key : keys) {
                Keyed value = registry.get(key);
                if (value != null)
                    return Optional.of((T) value);
            }
        }

        if (lookup != null)
            return lookup.find(typeClass, keys);
        return Optional.empty();
    }

    @NonNull
    public static <T> Optional<T> findByField(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        var values = KEYED_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getKeyedValues);
        for (NamespacedKey key : keys) {
            var value = values.get(key);
            if (value != null)
                return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @NonNull
    public static <T> Optional<T> findByEnum(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        var values = ENUM_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getEnumValues);
        for (NamespacedKey key : keys) {
            var value = values.get(key);
            if (value != null)
                return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @NonNull
    public static <T> Lookup<T> defaultLookup() {
        return Lookup.combine(RegistryUtil::findByField, RegistryUtil::findByEnum);
    }

    private static Map<NamespacedKey, Keyed> getKeyedValues(@NonNull Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> Keyed.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return (Keyed) field.get(null);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .map(keyed -> {
                    if (keyed == null) return null;
                    try {
                        return Pair.make(keyed.getKey(), keyed);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private static Map<NamespacedKey, Object> getEnumValues(@NonNull Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> typeClass.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return Map.entry(NamespacedKey.minecraft(field.getName().toLowerCase()), field.get(null));
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    @FunctionalInterface
    public interface Lookup<T> {
        @NonNull
        Optional<T> find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys);

        static <T> Lookup<T> combine(@NonNull Lookup<T>... lookups) {
            if (lookups.length == 0) throw new IllegalArgumentException("Need at least one lookup");
            return (typeClass, keys) -> {
                Optional<T> opt = Optional.empty();
                for (Lookup<T> lookup : lookups) {
                    opt = opt.or(() -> lookup.find(typeClass, keys));
                }
                return opt;
            };
        }
    }

    @Nullable
    private static <T extends Keyed> Registry<T> getRegistry(@NotNull Class<T> type) {
        RegistryLookup lookup = registryLookup.aquire(() -> {
            RegistryLookup bukkit;
            try {
                bukkit = Bukkit::getRegistry;
            } catch (Throwable ignored) {
                bukkit = null;
            }
            return new DefaultRegistryLookup(bukkit);
        });
        return lookup.find(type);
    }

    private interface RegistryLookup {
        @Nullable
        <T extends Keyed> Registry<T> find(@NonNull Class<T> type);
    }

    private static class DefaultRegistryLookup implements RegistryLookup {
        private final RegistryLookup bukkit;
        private final Map<Type, Object> registries;

        private DefaultRegistryLookup(RegistryLookup bukkit) {
            this.bukkit = bukkit;
            registries = Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .map(field -> {
                        var type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        try {
                            return Map.entry(type, field.get(null));
                        } catch (Throwable e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        }

        @Nullable
        @Override
        public <T extends Keyed> Registry<T> find(@NonNull Class<T> type) {
            if (bukkit == null) return (Registry<T>) registries.get(type);
            try {
                return bukkit.find(type);
            } catch (Throwable e) {
                return (Registry<T>) registries.get(type);
            }
        }
    }

    public static <T> Supplier<IllegalArgumentException> notFound(Class<?> type, T... keys) {
        return () -> new IllegalArgumentException("No " + type.getSimpleName() + " found for keys: " + Arrays.deepToString(keys));
    }
}
