package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Core speaks 26.x ids; this version registers some of them under older names. Lookups go through
 * {@link ContentAliases} so {@code minecraft:jump_strength} finds {@code minecraft:generic.jump_strength}, and native
 * ids are reported back under their 26.x name.
 */
public final class ModContentAliases {
    private ModContentAliases() {
    }

    /** The registered name of a 26.x id on this version, or null when the registry has neither it nor an older name. */
    public static ResourceLocation location(Registry<?> registry, RegistryKind kind, Id id) {
        if (registry == null || id == null) {
            return null;
        }
        Id resolved = ContentAliases.resolve(kind, id, (Id candidate) -> registry.containsKey(ModConversions.identifier(candidate)));
        return resolved == null ? null : ModConversions.identifier(resolved);
    }

    /** The registry entry of a 26.x id, or null. */
    public static <T> Holder<T> holder(Registry<T> registry, RegistryKind kind, Id id) {
        ResourceLocation location = location(registry, kind, id);
        if (location == null) {
            return null;
        }
        return registry.getHolder(ResourceKey.create(registry.key(), location)).orElse(null);
    }

    /** The 26.x id of a native name: the first modern id it stands for, else the name itself. */
    public static Id modern(RegistryKind kind, ResourceLocation location) {
        Id id = ModConversions.id(location);
        List<Id> modern = ContentAliases.modern(kind, id);
        return modern.isEmpty() ? id : modern.get(0);
    }

    /** The 26.x id of a registry entry, or null for a direct holder. */
    public static <T> Id modern(RegistryKind kind, Holder<T> holder) {
        return holder.unwrapKey().map((ResourceKey<T> key) -> modern(kind, key.location())).orElse(null);
    }
}
