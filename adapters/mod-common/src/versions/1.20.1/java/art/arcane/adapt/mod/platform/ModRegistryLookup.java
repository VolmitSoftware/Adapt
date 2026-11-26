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
import net.minecraft.resources.ResourceLocation;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry lookups through the server's registry access, which covers built-in and data-driven registries. Ids this
 * version registers under an older name are translated both ways ({@link ModContentAliases}).
 */
public final class ModRegistryLookup implements RegistryLookup {
    @Override
    public boolean exists(RegistryKind kind, Id id) {
        return ModContentAliases.location(registry(kind), kind, id) != null;
    }

    @Override
    public Set<Id> ids(RegistryKind kind) {
        Registry<?> registry = registry(kind);
        if (registry == null) {
            return Set.of();
        }
        Set<Id> out = new LinkedHashSet<>();
        for (ResourceLocation key : registry.keySet()) {
            out.add(ModContentAliases.modern(kind, key));
        }
        return Set.copyOf(out);
    }

    @Override
    public boolean isTagged(RegistryKind kind, Id tag, Id value) {
        Registry<?> registry = registry(kind);
        return registry != null && tagged(registry, kind, tag, value);
    }

    @Override
    public Set<Id> tagValues(RegistryKind kind, Id tag) {
        Registry<?> registry = registry(kind);
        return registry == null ? Set.of() : values(registry, kind, tag);
    }

    @Override
    public int enchantmentMaxLevel(Id enchantment) {
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        return holder == null ? 0 : holder.value().getMaxLevel();
    }

    @Override
    public String particleDataType(Id particle) {
        Holder<ParticleType<?>> type = ModContentAliases.holder(BuiltInRegistries.PARTICLE_TYPE, RegistryKind.PARTICLE, particle);
        return type == null || type.value() instanceof SimpleParticleType ? null : type.value().getClass().getSimpleName();
    }

    @Override
    public EffectCategory effectCategory(Id effect) {
        Holder<MobEffect> type = ModConversions.effect(effect);
        if (type == null) {
            return null;
        }
        return switch (type.value().getCategory()) {
            case BENEFICIAL -> EffectCategory.BENEFICIAL;
            case HARMFUL -> EffectCategory.HARMFUL;
            case NEUTRAL -> EffectCategory.NEUTRAL;
        };
    }

    @Override
    public boolean isInstantEffect(Id effect) {
        Holder<MobEffect> type = ModConversions.effect(effect);
        return type != null && Compat.isInstantaneous(type.value());
    }

    @Override
    public List<EffectInstance> potionEffects(Id potion) {
        Holder<Potion> type = ModContentAliases.holder(BuiltInRegistries.POTION, RegistryKind.POTION, potion);
        if (type == null) {
            return List.of();
        }
        List<EffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : type.value().getEffects()) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            Id id = key == null ? null : ModContentAliases.modern(RegistryKind.EFFECT, key);
            if (id != null) {
                effects.add(new EffectInstance(id, effect.isInfiniteDuration() ? EffectInstance.INFINITE : effect.getDuration(),
                    effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            }
        }
        return effects;
    }

    @Override
    public List<EffectModifier> effectModifiers(Id effect, int amplifier) {
        Holder<MobEffect> type = ModConversions.effect(effect);
        if (type == null) {
            return List.of();
        }
        List<EffectModifier> modifiers = new ArrayList<>();
        MobEffect value = type.value();
        for (Map.Entry<Attribute, AttributeModifier> entry : value.getAttributeModifiers().entrySet()) {
            modifiers.add(new EffectModifier(entry.getKey().getDescriptionId(), operation(entry.getValue().getOperation()),
                value.getAttributeModifierValue(amplifier, entry.getValue())));
        }
        return modifiers;
    }

    private static AttributeOperation operation(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADDITION -> AttributeOperation.ADD_VALUE;
            case MULTIPLY_BASE -> AttributeOperation.ADD_MULTIPLIED_BASE;
            case MULTIPLY_TOTAL -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private static <T> boolean tagged(Registry<T> registry, RegistryKind kind, Id tag, Id value) {
        Holder<T> holder = ModContentAliases.holder(registry, kind, value);
        return holder != null && holder.is(TagKey.create(registry.key(), ModConversions.identifier(tag)));
    }

    private static <T> Set<Id> values(Registry<T> registry, RegistryKind kind, Id tag) {
        Optional<HolderSet.Named<T>> set = registry.getTag(TagKey.create(registry.key(), ModConversions.identifier(tag)));
        if (set.isEmpty()) {
            return Set.of();
        }
        Set<Id> out = new LinkedHashSet<>();
        for (Holder<T> holder : set.get()) {
            Id id = ModContentAliases.modern(kind, holder);
            if (id != null) {
                out.add(id);
            }
        }
        return Set.copyOf(out);
    }

    private static Registry<?> registry(RegistryKind kind) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return null;
        }
        return switch (kind) {
            case ITEM -> server.registryAccess().registryOrThrow(Registries.ITEM);
            case BLOCK -> server.registryAccess().registryOrThrow(Registries.BLOCK);
            case ENTITY_TYPE -> server.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
            case SOUND -> server.registryAccess().registryOrThrow(Registries.SOUND_EVENT);
            case PARTICLE -> server.registryAccess().registryOrThrow(Registries.PARTICLE_TYPE);
            case EFFECT -> server.registryAccess().registryOrThrow(Registries.MOB_EFFECT);
            case ENCHANTMENT -> server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            case ATTRIBUTE -> server.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
            case BIOME -> server.registryAccess().registryOrThrow(Registries.BIOME);
            case DAMAGE_TYPE -> server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            case POTION -> server.registryAccess().registryOrThrow(Registries.POTION);
        };
    }
}
