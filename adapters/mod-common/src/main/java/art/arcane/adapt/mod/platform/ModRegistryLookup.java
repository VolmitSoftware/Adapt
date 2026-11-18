package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Registry lookups through the server's registry access, which covers built-in and data-driven registries. */
public final class ModRegistryLookup implements RegistryLookup {
    @Override
    public boolean exists(RegistryKind kind, Id id) {
        Registry<?> registry = registry(kind);
        return registry != null && registry.containsKey(ModConversions.identifier(id));
    }

    @Override
    public Set<Id> ids(RegistryKind kind) {
        Registry<?> registry = registry(kind);
        if (registry == null) {
            return Set.of();
        }
        Set<Id> out = new LinkedHashSet<>();
        for (Identifier key : registry.keySet()) {
            out.add(ModConversions.id(key));
        }
        return Set.copyOf(out);
    }

    @Override
    public boolean isTagged(RegistryKind kind, Id tag, Id value) {
        Registry<?> registry = registry(kind);
        return registry != null && tagged(registry, tag, value);
    }

    @Override
    public Set<Id> tagValues(RegistryKind kind, Id tag) {
        Registry<?> registry = registry(kind);
        return registry == null ? Set.of() : values(registry, tag);
    }

    @Override
    public int enchantmentMaxLevel(Id enchantment) {
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        return holder == null ? 0 : holder.value().getMaxLevel();
    }

    @Override
    public String particleDataType(Id particle) {
        if (particle == null) {
            return null;
        }
        Optional<Holder.Reference<ParticleType<?>>> type = BuiltInRegistries.PARTICLE_TYPE.get(ModConversions.identifier(particle));
        return type.isEmpty() || type.get().value() instanceof SimpleParticleType ? null : type.get().value().getClass().getSimpleName();
    }

    @Override
    public EffectCategory effectCategory(Id effect) {
        if (effect == null) {
            return null;
        }
        Optional<Holder.Reference<MobEffect>> type = BuiltInRegistries.MOB_EFFECT.get(ModConversions.identifier(effect));
        if (type.isEmpty()) {
            return null;
        }
        return switch (type.get().value().getCategory()) {
            case BENEFICIAL -> EffectCategory.BENEFICIAL;
            case HARMFUL -> EffectCategory.HARMFUL;
            case NEUTRAL -> EffectCategory.NEUTRAL;
        };
    }

    @Override
    public boolean isInstantEffect(Id effect) {
        if (effect == null) {
            return false;
        }
        Optional<Holder.Reference<MobEffect>> type = BuiltInRegistries.MOB_EFFECT.get(ModConversions.identifier(effect));
        return type.isPresent() && Compat.isInstantaneous(type.get().value());
    }

    @Override
    public List<EffectInstance> potionEffects(Id potion) {
        if (potion == null) {
            return List.of();
        }
        Optional<Holder.Reference<Potion>> type = BuiltInRegistries.POTION.get(ModConversions.identifier(potion));
        if (type.isEmpty()) {
            return List.of();
        }
        List<EffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : type.get().value().getEffects()) {
            Optional<ResourceKey<MobEffect>> key = effect.getEffect().unwrapKey();
            if (key.isPresent()) {
                effects.add(new EffectInstance(ModConversions.id(key.get().identifier()),
                    effect.isInfiniteDuration() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            }
        }
        return effects;
    }

    @Override
    public List<EffectModifier> effectModifiers(Id effect, int amplifier) {
        if (effect == null) {
            return List.of();
        }
        Optional<Holder.Reference<MobEffect>> type = BuiltInRegistries.MOB_EFFECT.get(ModConversions.identifier(effect));
        if (type.isEmpty()) {
            return List.of();
        }
        List<EffectModifier> modifiers = new ArrayList<>();
        type.get().value().createModifiers(amplifier, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
            Optional<ResourceKey<Attribute>> key = attribute.unwrapKey();
            if (key.isPresent()) {
                modifiers.add(new EffectModifier(ModConversions.id(key.get().identifier()), operation(modifier.operation()),
                    modifier.amount()));
            }
        });
        return modifiers;
    }

    private static AttributeOperation operation(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeOperation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeOperation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private static <T> boolean tagged(Registry<T> registry, Id tag, Id value) {
        Optional<Holder.Reference<T>> holder = registry.get(ModConversions.identifier(value));
        return holder.isPresent() && holder.get().is(TagKey.create(registry.key(), ModConversions.identifier(tag)));
    }

    private static <T> Set<Id> values(Registry<T> registry, Id tag) {
        Optional<HolderSet.Named<T>> set = registry.get(TagKey.create(registry.key(), ModConversions.identifier(tag)));
        if (set.isEmpty()) {
            return Set.of();
        }
        Set<Id> out = new LinkedHashSet<>();
        for (Holder<T> holder : set.get()) {
            holder.unwrapKey().ifPresent((ResourceKey<T> key) -> out.add(ModConversions.id(key.identifier())));
        }
        return Set.copyOf(out);
    }

    private static Registry<?> registry(RegistryKind kind) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return null;
        }
        return switch (kind) {
            case ITEM -> server.registryAccess().lookupOrThrow(Registries.ITEM);
            case BLOCK -> server.registryAccess().lookupOrThrow(Registries.BLOCK);
            case ENTITY_TYPE -> server.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
            case SOUND -> server.registryAccess().lookupOrThrow(Registries.SOUND_EVENT);
            case PARTICLE -> server.registryAccess().lookupOrThrow(Registries.PARTICLE_TYPE);
            case EFFECT -> server.registryAccess().lookupOrThrow(Registries.MOB_EFFECT);
            case ENCHANTMENT -> server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            case ATTRIBUTE -> server.registryAccess().lookupOrThrow(Registries.ATTRIBUTE);
            case BIOME -> server.registryAccess().lookupOrThrow(Registries.BIOME);
            case DAMAGE_TYPE -> server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
            case POTION -> server.registryAccess().lookupOrThrow(Registries.POTION);
        };
    }
}
