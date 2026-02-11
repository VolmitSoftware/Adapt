package com.volmit.adapt.api.version;

import com.volmit.adapt.api.potion.PotionBuilder;
import com.volmit.adapt.util.CustomModel;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RuntimeBindings implements IBindings {
    private static final Method SET_ITEM_MODEL_METHOD = findMethod(ItemMeta.class, "setItemModel", NamespacedKey.class);
    private static final Method SET_BASE_POTION_TYPE_METHOD = findMethod(PotionMeta.class, "setBasePotionType", PotionType.class);
    private static final List<EntityType> INVALID_DAMAGEABLE_ENTITIES = detectInvalidDamageableEntities();

    @Override
    public void applyModel(CustomModel model, ItemMeta meta) {
        NamespacedKey modelKey = model.modelKey();
        if (modelKey != null && !CustomModel.EMPTY_KEY.equals(modelKey) && SET_ITEM_MODEL_METHOD != null) {
            try {
                SET_ITEM_MODEL_METHOD.invoke(meta, modelKey);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fallback is custom model data for older API variants.
            }
        }

        meta.setCustomModelData(model.model());
    }

    @Override
    public IAttribute getAttribute(Attributable attributable, Attribute modifier) {
        return Optional.ofNullable(attributable.getAttribute(modifier))
                .map(RuntimeAttribute::new)
                .orElse(null);
    }

    @Override
    public ItemStack buildPotion(PotionBuilder builder) {
        ItemStack stack = IBindings.super.buildPotion(builder);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        if (meta == null || builder.getBaseType() == null || SET_BASE_POTION_TYPE_METHOD == null) {
            return stack;
        }

        try {
            SET_BASE_POTION_TYPE_METHOD.invoke(meta, builder.getBaseType());
            stack.setItemMeta(meta);
        } catch (ReflectiveOperationException ignored) {
            // Older APIs may not expose base potion type mutators.
        }

        return stack;
    }

    @Override
    @Unmodifiable
    public List<EntityType> getInvalidDamageableEntities() {
        return INVALID_DAMAGEABLE_ENTITIES;
    }

    private static List<EntityType> detectInvalidDamageableEntities() {
        Set<EntityType> entities = new LinkedHashSet<>();

        addIfPresent(entities,
                "ARMOR_STAND",
                "ITEM_FRAME",
                "GLOW_ITEM_FRAME",
                "PAINTING",
                "LEASH_HITCH",
                "LEASH_KNOT",
                "EVOKER_FANGS",
                "MARKER",
                "BOAT",
                "CHEST_BOAT",
                "MINECART"
        );
        addByPrefix(entities, "MINECART_");
        addBySuffix(entities, "_MINECART");
        addBySuffix(entities, "_BOAT");
        addBySuffix(entities, "_CHEST_BOAT");
        addBySuffix(entities, "_RAFT");
        addBySuffix(entities, "_CHEST_RAFT");

        return List.copyOf(entities);
    }

    private static void addIfPresent(Set<EntityType> entities, String... names) {
        for (String name : names) {
            try {
                entities.add(EntityType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Entity was renamed/removed in this API version.
            }
        }
    }

    private static void addByPrefix(Set<EntityType> entities, String prefix) {
        for (EntityType entity : EntityType.values()) {
            if (entity.name().startsWith(prefix)) {
                entities.add(entity);
            }
        }
    }

    private static void addBySuffix(Set<EntityType> entities, String suffix) {
        for (EntityType entity : EntityType.values()) {
            if (entity.name().endsWith(suffix)) {
                entities.add(entity);
            }
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
