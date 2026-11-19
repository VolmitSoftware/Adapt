package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.AreaEffectCloudApplyEvent;
import art.arcane.adapt.platform.event.entity.LingeringPotionSplashEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Splash potions, lingering potion clouds and the clouds' application rounds. */
public final class PotionHooks {
    private PotionHooks() {
    }

    /**
     * The entities a splash potion is about to affect. Posts the splash with this version's intensity
     * per entity (full for the entity hit directly, else by distance to the potion); returns an empty
     * list when a handler cancelled, else the candidates unchanged.
     */
    public static List<LivingEntity> splashing(ThrownPotion potion, ItemStack potionItem, Entity directHit, List<LivingEntity> candidates) {
        if (!ModEvents.listening(PotionSplashEvent.class)) {
            return candidates;
        }
        Map<LivingRef, Double> affected = new LinkedHashMap<>();
        for (LivingEntity entity : candidates) {
            if (!entity.isAffectedByPotions()) {
                continue;
            }
            double distance = potion.distanceToSqr(entity);
            if (distance < 16.0) {
                affected.put(ModRefs.living(entity), entity == directHit ? 1.0 : 1.0 - Math.sqrt(distance) / 4.0);
            }
        }
        PotionContents contents = potionItem.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        List<EffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : contents.getAllEffects()) {
            effects.add(effect(effect));
        }
        PotionSplashEvent event = new PotionSplashEvent((ProjectileRef) ModRefs.entity(potion), ModRefs.item(potionItem.copy()), effects, affected);
        return ModEvents.post(event).isCancelled() ? List.of() : candidates;
    }

    /** True when the lingering cloud must not be added. */
    public static boolean lingering(ThrownPotion potion, ItemStack potionItem, Entity cloud) {
        if (!ModEvents.listening(LingeringPotionSplashEvent.class)) {
            return false;
        }
        LingeringPotionSplashEvent event = new LingeringPotionSplashEvent((ProjectileRef) ModRefs.entity(potion),
            ModRefs.item(potionItem.copy()), ModRefs.entity(cloud));
        return ModEvents.post(event).isCancelled() || cloud.isRemoved();
    }

    /**
     * The entities one application round of a cloud is about to consider. Posts the round with the
     * entities vanilla would affect; returns an empty list when a handler cancelled.
     */
    public static List<LivingEntity> cloudApplying(AreaEffectCloud cloud, PotionContents contents, Map<Entity, Integer> victims,
                                                   List<LivingEntity> candidates) {
        if (candidates.isEmpty() || !ModEvents.listening(AreaEffectCloudApplyEvent.class)) {
            return candidates;
        }
        List<MobEffectInstance> all = new ArrayList<>();
        contents.forEachEffect(all::add);
        float radius = cloud.getRadius();
        List<LivingRef> affected = new ArrayList<>();
        for (LivingEntity entity : candidates) {
            if (victims.containsKey(entity) || !entity.isAffectedByPotions() || all.stream().noneMatch(entity::canBeAffected)) {
                continue;
            }
            double xd = entity.getX() - cloud.getX();
            double zd = entity.getZ() - cloud.getZ();
            if (xd * xd + zd * zd <= radius * radius) {
                affected.add(ModRefs.living(entity));
            }
        }
        if (affected.isEmpty()) {
            return candidates;
        }
        Id base = contents.potion().map((Holder<Potion> potion) -> ModContentAliases.modern(RegistryKind.POTION, potion)).orElse(null);
        List<EffectInstance> custom = new ArrayList<>();
        for (MobEffectInstance effect : contents.customEffects()) {
            custom.add(effect(effect));
        }
        LivingEntity owner = cloud.getOwner();
        AreaEffectCloudApplyEvent event = new AreaEffectCloudApplyEvent(ModRefs.entity(cloud), owner == null ? null : ModRefs.entity(owner),
            base, custom, affected);
        return ModEvents.post(event).isCancelled() ? List.of() : candidates;
    }

    private static EffectInstance effect(MobEffectInstance effect) {
        Id found = ModContentAliases.modern(RegistryKind.EFFECT, effect.getEffect());
        Id id = found == null ? new Id("minecraft", "unknown") : found;
        int duration = effect.isInfiniteDuration() ? EffectInstance.INFINITE : effect.getDuration();
        return new EffectInstance(id, duration, Math.max(0, effect.getAmplifier()), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }
}
