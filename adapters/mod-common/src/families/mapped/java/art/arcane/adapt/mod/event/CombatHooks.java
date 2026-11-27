package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.mixin.LivingEntityAccess;
import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.entity.ModLastDamage;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.BlockedDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.EntityTameEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Damage, healing, effects, targeting, taming and breeding. Called from the mixins in {@code mod.mixin}. */
public final class CombatHooks {
    private static final Map<LivingEntity, Float> BYPASSING = new IdentityHashMap<>();
    private static boolean critical;
    private static int sweeping;
    private static EntityRegainHealthEvent.Reason healReason = EntityRegainHealthEvent.Reason.CUSTOM;
    private static EntityTargetEvent.Reason targetReason;
    private static final CauseStack<EntityPotionEffectEvent.Cause> EFFECT_CAUSES = new CauseStack<>();
    private static int pendingBreedExperience = -1;
    private static boolean clearingEffects;

    /** Returned by {@link #brainTargeting} when the current target must stay. */
    public static final Object KEEP_TARGET = new Object();

    private CombatHooks() {
    }

    public static void attacking(boolean criticalHit) {
        critical = criticalHit;
    }

    public static void attacked() {
        critical = false;
    }

    public static void sweepStarted() {
        sweeping++;
    }

    public static void sweepEnded() {
        sweeping = Math.max(0, sweeping - 1);
    }

    /**
     * Before vanilla's damage pipeline, after the same invulnerability and cooldown checks Paper runs
     * first. Returns the base damage to use, or a negative value when a handler cancelled. A hit that
     * vanilla's cooldown would reject once the shield took its share posts nothing.
     */
    public static float damaging(LivingEntity entity, ServerLevel level, DamageSource source, float amount) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        boolean byEntity = direct != null || causing != null;
        boolean listening = ModEvents.listening(eventType(entity, source, byEntity));
        if (entity.isInvulnerableTo(source) || entity.isDeadOrDying()) {
            return amount;
        }
        if (source.is(DamageTypeTags.IS_FIRE) && entity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return amount;
        }
        float base = Math.max(0.0F, amount);
        Shield shield = entity.isDamageSourceBlocked(source) ? Shield.BLOCKING : Shield.NONE;
        boolean cooling = ModCompat.damageCooldown(entity) > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        float lastHurt = ((LivingEntityAccess) entity).adapt$lastHurt();
        if (cooling && scaled(entity, source, base - shield.blocked(base)) <= lastHurt) {
            return amount;
        }
        float reduction = cooling ? lastHurt : 0.0F;
        FinalDamage finalDamage = (double value) -> finalDamage(entity, level, source, (float) value, reduction, shield);
        DamageCause cause = BlockDamage.cause(entity, source, sweeping > 0);
        Entity damager = direct != null ? direct : causing;
        if (!listening) {
            ModLastDamage.record(entity, cause, damager, finalDamage.of(base));
            return amount;
        }
        BlockedDamage blockedDamage = (double value) -> shield.blocked((float) value);
        EntityDamageEvent event = post(entity, source, cause, base, finalDamage, blockedDamage);
        if (event.isCancelled()) {
            return -1.0F;
        }
        ModLastDamage.record(entity, cause, damager, event.finalDamage());
        if (!event.ignoreReductions()) {
            return (float) event.damage();
        }
        bypassStarting(entity);
        return (float) ReductionBypass.base(event.damage(), scaled(entity, source, 1.0F), reduction);
    }

    /**
     * True while {@code entity} takes a hit that ignores reductions: armour, enchantment and effect protection and
     * shield blocking return the damage unchanged, as Paper does when every damage modifier is zeroed.
     */
    public static boolean bypassing(LivingEntity entity) {
        return BYPASSING.containsKey(entity);
    }

    /** End of the hit: absorption hidden for the bypassing hit comes back untouched. */
    public static void damaged(LivingEntity entity) {
        Float absorption = BYPASSING.remove(entity);
        if (absorption != null && absorption > 0.0F) {
            entity.setAbsorptionAmount(absorption);
        }
    }

    private static void bypassStarting(LivingEntity entity) {
        damaged(entity);
        BYPASSING.put(entity, entity.getAbsorptionAmount());
        entity.setAbsorptionAmount(0.0F);
    }

    /**
     * Damage to an entity that is not a living one, or an armour stand: item frames, paintings, boats,
     * minecarts, end crystals and dropped items. Returns the damage to use, or a negative value when a
     * handler cancelled.
     */
    public static float damagingObject(Entity entity, ServerLevel level, DamageSource source, float amount) {
        Entity damager = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        boolean listening = ModEvents.listening(eventType(entity, source, damager != null));
        if (!listening && !(entity instanceof LivingEntity)) {
            return amount;
        }
        if (entity.isRemoved() || entity.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer()
            || entity.fireImmune() && source.is(DamageTypeTags.IS_FIRE)) {
            return amount;
        }
        DamageCause cause = BlockDamage.cause(entity, source, sweeping > 0);
        if (!listening) {
            ModLastDamage.record((LivingEntity) entity, cause, damager, Math.max(0.0F, amount));
            return amount;
        }
        EntityDamageEvent event = post(entity, source, cause, Math.max(0.0F, amount), FinalDamage.IDENTITY, BlockedDamage.NONE);
        if (event.isCancelled()) {
            return -1.0F;
        }
        if (entity instanceof LivingEntity living) {
            ModLastDamage.record(living, cause, damager, event.finalDamage());
        }
        return (float) event.damage();
    }

    private static Class<? extends EntityDamageEvent> eventType(Entity entity, DamageSource source, boolean byEntity) {
        if (byEntity) {
            return EntityDamageByEntityEvent.class;
        }
        return BlockDamage.byBlock(entity, source) ? EntityDamageByBlockEvent.class : EntityDamageEvent.class;
    }

    private static EntityDamageEvent post(Entity entity, DamageSource source, DamageCause cause, float base, FinalDamage finalDamage,
                                          BlockedDamage blockedDamage) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        EntityRef target = ModRefs.entity(entity);
        Id type = PearlLanding.isPearlDamage(entity, source) ? Id.minecraft("ender_pearl") : ModEventMaps.damageType(source);
        EntityDamageEvent event;
        if (direct != null || causing != null) {
            Entity damager = direct != null ? direct : causing;
            boolean crit = critical && damager instanceof Player
                || damager instanceof AbstractArrow arrow && arrow.isCritArrow();
            event = new EntityDamageByEntityEvent(target, ModRefs.entity(damager), causing == null ? null : ModRefs.entity(causing),
                cause, type, base, crit, finalDamage, blockedDamage);
        } else if (BlockDamage.byBlock(entity, source)) {
            event = new EntityDamageByBlockEvent(target, BlockDamage.damager(entity), cause, type, base, finalDamage, blockedDamage);
        } else {
            event = new EntityDamageEvent(target, cause, type, base, finalDamage, blockedDamage);
        }
        Vec3 position = source.sourcePositionRaw();
        event.setSource(position == null || !(entity.level() instanceof ServerLevel level) ? null : ModRefs.loc(level, position, 0.0F, 0.0F),
            base, (LivingRef victim, double amount) -> {
                if (victim.unwrap() instanceof LivingEntity living && living.level() instanceof ServerLevel) {
                    living.hurt(source, (float) amount);
                }
            });
        return ModEvents.post(event);
    }

    private static float scaled(LivingEntity entity, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FREEZING) && entity.getType().builtInRegistryHolder().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
            damage *= 5.0F;
        }
        if (source.is(DamageTypeTags.DAMAGES_HELMET) && !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            damage *= 0.75F;
        }
        return damage;
    }

    static double finalDamage(LivingEntity entity, ServerLevel level, DamageSource source, float base, float reduction, Shield shield) {
        float damage = scaled(entity, source, base - shield.blocked(base));
        damage = Math.max(0.0F, damage - reduction);
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            damage = CombatRules.getDamageAfterAbsorb(entity, damage, source, entity.getArmorValue(),
                (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            MobEffectInstance resistance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int absorb = 25 - (resistance.getAmplifier() + 1) * 5;
                damage = Math.max(damage * absorb / 25.0F, 0.0F);
            }
            if (damage > 0.0F && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                float protection = EnchantmentHelper.getDamageProtection(level, entity, source);
                if (protection > 0.0F) {
                    damage = CombatRules.getDamageAfterMagicAbsorb(damage, protection);
                }
            }
        }
        return Math.max(damage - entity.getAbsorptionAmount(), 0.0F);
    }

    public static void healReason(EntityRegainHealthEvent.Reason reason) {
        healReason = reason == null ? EntityRegainHealthEvent.Reason.CUSTOM : reason;
    }

    public static EntityRegainHealthEvent.Reason healReason() {
        return healReason;
    }

    /** Returns the amount to heal, or a negative value when cancelled. */
    public static float healing(LivingEntity entity, float amount) {
        if (!ModEvents.server(entity) || entity.getHealth() <= 0.0F || !ModEvents.listening(EntityRegainHealthEvent.class)) {
            return amount;
        }
        EntityRegainHealthEvent event = ModEvents.post(new EntityRegainHealthEvent(ModRefs.entity(entity), amount, healReason));
        if (event.isCancelled()) {
            return -1.0F;
        }
        return (float) event.amount();
    }

    /** Opens a cause for the effects vanilla changes until the matching {@link #effectCauseEnd()}. */
    public static void effectCause(EntityPotionEffectEvent.Cause cause, boolean weak) {
        EFFECT_CAUSES.push(cause, weak);
    }

    public static void effectCauseEnd() {
        EFFECT_CAUSES.pop();
    }

    /** Runs an effect change Adapt starts itself under a cause, as Paper's API changes carry PLUGIN. */
    public static <T> T withEffectCause(EntityPotionEffectEvent.Cause cause, Supplier<T> action) {
        return EFFECT_CAUSES.with(cause, action);
    }

    private static EntityPotionEffectEvent.Cause effectCause(EntityPotionEffectEvent.Cause fallback) {
        EntityPotionEffectEvent.Cause cause = EFFECT_CAUSES.current();
        return cause == null ? fallback : cause;
    }

    /** True when a handler cancelled the effect being added or changed. Covers forced adds too. */
    public static boolean effectAdding(LivingEntity entity, MobEffectInstance effect, Entity source) {
        if (!ModEvents.server(entity) || !ModEvents.listening(EntityPotionEffectEvent.class) || !entity.canBeAffected(effect)) {
            return false;
        }
        MobEffectInstance old = entity.getEffect(effect.getEffect());
        EntityPotionEffectEvent.Action action = old == null ? EntityPotionEffectEvent.Action.ADDED : EntityPotionEffectEvent.Action.CHANGED;
        EntityPotionEffectEvent.Cause cause = effectCause(source != null && source != entity
            ? EntityPotionEffectEvent.Cause.ATTACK : EntityPotionEffectEvent.Cause.UNKNOWN);
        EntityPotionEffectEvent event = new EntityPotionEffectEvent(ModRefs.living(entity), effectId(effect.getEffect()),
            old == null ? null : effect(old), effect(effect), action, cause);
        return ModEvents.post(event).isCancelled();
    }

    /** True when a handler cancelled the removal. */
    public static boolean effectRemoving(LivingEntity entity, Holder<MobEffect> effect) {
        if (clearingEffects || !ModEvents.server(entity) || !ModEvents.listening(EntityPotionEffectEvent.class)) {
            return false;
        }
        MobEffectInstance old = entity.getEffect(effect);
        if (old == null) {
            return false;
        }
        EntityPotionEffectEvent event = new EntityPotionEffectEvent(ModRefs.living(entity), effectId(effect), effect(old), null,
            EntityPotionEffectEvent.Action.REMOVED, effectCause(EntityPotionEffectEvent.Cause.UNKNOWN));
        return ModEvents.post(event).isCancelled();
    }

    /** An effect ran out. True when a handler cancelled and the effect stays for another tick. */
    public static boolean effectExpiring(LivingEntity entity, MobEffectInstance effect) {
        if (!ModEvents.listening(EntityPotionEffectEvent.class)) {
            return false;
        }
        EntityPotionEffectEvent event = new EntityPotionEffectEvent(ModRefs.living(entity), effectId(effect.getEffect()), effect(effect), null,
            EntityPotionEffectEvent.Action.REMOVED, EntityPotionEffectEvent.Cause.EXPIRATION);
        return ModEvents.post(event).isCancelled();
    }

    /**
     * Posts one CLEARED event per active effect. Returns null to let vanilla clear everything, or the
     * result of removing only the effects no handler kept.
     */
    public static Boolean effectsClearing(LivingEntity entity) {
        if (clearingEffects || !ModEvents.server(entity) || !ModEvents.listening(EntityPotionEffectEvent.class)) {
            return null;
        }
        Collection<MobEffectInstance> active = entity.getActiveEffects();
        if (active.isEmpty()) {
            return null;
        }
        EntityPotionEffectEvent.Cause cause = effectCause(EntityPotionEffectEvent.Cause.UNKNOWN);
        List<Holder<MobEffect>> removable = new ArrayList<>();
        boolean kept = false;
        for (MobEffectInstance effect : List.copyOf(active)) {
            EntityPotionEffectEvent event = new EntityPotionEffectEvent(ModRefs.living(entity), effectId(effect.getEffect()), effect(effect), null,
                EntityPotionEffectEvent.Action.CLEARED, cause);
            if (ModEvents.post(event).isCancelled()) {
                kept = true;
            } else {
                removable.add(effect.getEffect());
            }
        }
        if (!kept) {
            return null;
        }
        boolean removed = false;
        clearingEffects = true;
        try {
            for (Holder<MobEffect> effect : removable) {
                removed |= entity.removeEffect(effect);
            }
        } finally {
            clearingEffects = false;
        }
        return removed;
    }

    static Id effectId(Holder<MobEffect> effect) {
        Id id = ModContentAliases.modern(RegistryKind.EFFECT, effect);
        return id == null ? new Id("minecraft", "unknown") : id;
    }

    static EffectInstance effect(MobEffectInstance effect) {
        int duration = effect.isInfiniteDuration() ? EffectInstance.INFINITE : effect.getDuration();
        return EffectInstance.of(effectId(effect.getEffect()), duration, Math.max(0, effect.getAmplifier())).withAmbient(effect.isAmbient()).withParticles(effect.isVisible()).withIcon(effect.showIcon());
    }

    public static void targetReason(EntityTargetEvent.Reason reason) {
        targetReason = reason;
    }

    public static EntityTargetEvent.Reason targetReason() {
        return targetReason;
    }

    /** Returns the target to set; the mob's current target when a handler cancelled. */
    public static LivingEntity targeting(Mob mob, LivingEntity target) {
        if (!ModEvents.server(mob)) {
            return target;
        }
        LivingEntity current = mob.getTarget();
        if (current == target || !ModEvents.listening(EntityTargetEvent.class)) {
            return target;
        }
        EntityTargetEvent.Reason reason = targetReason;
        if (target == null) {
            reason = current != null && !current.isAlive() ? EntityTargetEvent.Reason.TARGET_DIED : EntityTargetEvent.Reason.FORGOT_TARGET;
        } else if (reason == null) {
            reason = EntityTargetEvent.Reason.UNKNOWN;
        }
        EntityTargetEvent event = ModEvents.post(new EntityTargetEvent(ModRefs.entity(mob), target == null ? null : ModRefs.living(target), reason));
        if (event.isCancelled()) {
            return current;
        }
        LivingRef chosen = event.target();
        return chosen == null ? null : ModRefs.living(chosen);
    }

    /**
     * A brain-driven mob's attack target memory is about to change (piglins, hoglins, wardens,
     * axolotls, frogs, breezes and the rest of the brain mobs). Returns the value to store, or
     * {@link #KEEP_TARGET} when a handler cancelled and the current target stays.
     */
    public static Object brainTargeting(LivingEntity owner, Object current, Object value) {
        if (current == value || !ModEvents.server(owner) || !ModEvents.listening(EntityTargetEvent.class)) {
            return value;
        }
        LivingEntity target = value instanceof LivingEntity living ? living : null;
        LivingEntity old = current instanceof LivingEntity living ? living : null;
        EntityTargetEvent.Reason reason;
        if (target == null) {
            reason = old != null && !old.isAlive() ? EntityTargetEvent.Reason.TARGET_DIED : EntityTargetEvent.Reason.FORGOT_TARGET;
        } else if (targetReason != null) {
            reason = targetReason;
        } else if (owner.getLastHurtByMob() == target && owner.getLastHurtByMobTimestamp() == owner.tickCount) {
            reason = EntityTargetEvent.Reason.TARGET_ATTACKED_ENTITY;
        } else {
            reason = target instanceof Player ? EntityTargetEvent.Reason.CLOSEST_PLAYER : EntityTargetEvent.Reason.CLOSEST_ENTITY;
        }
        EntityTargetEvent event = ModEvents.post(new EntityTargetEvent(ModRefs.entity(owner), target == null ? null : ModRefs.living(target), reason));
        if (event.isCancelled()) {
            return KEEP_TARGET;
        }
        LivingRef chosen = event.target();
        return chosen == null ? null : ModRefs.living(chosen);
    }

    /** True when a handler cancelled the birth. Remembers the experience for the orb vanilla spawns next. */
    public static boolean breeding(Animal mother, Animal father, AgeableMob child) {
        pendingBreedExperience = -1;
        if (!ModEvents.server(mother) || !ModEvents.listening(EntityBreedEvent.class)) {
            return false;
        }
        ServerPlayer cause = mother.getLoveCause() != null ? mother.getLoveCause() : father.getLoveCause();
        int experience = mother.getRandom().nextInt(7) + 1;
        EntityBreedEvent event = new EntityBreedEvent(ModRefs.living(child), ModRefs.living(mother), ModRefs.living(father),
            cause == null ? null : ModRefs.living(cause), null, experience);
        if (ModEvents.post(event).isCancelled()) {
            return true;
        }
        pendingBreedExperience = event.experience();
        return false;
    }

    /** Experience for the breeding orb: the value handlers chose, or vanilla's when no event ran. */
    public static int breedExperience(int vanilla) {
        int pending = pendingBreedExperience;
        pendingBreedExperience = -1;
        return pending < 0 ? vanilla : pending;
    }

    /** True when a handler cancelled the taming. */
    public static boolean taming(LivingEntity animal, Player owner) {
        if (!(owner instanceof ServerPlayer player) || !ModEvents.server(animal) || !ModEvents.listening(EntityTameEvent.class)) {
            return false;
        }
        PlayerRef ownerRef = ModRefs.player(player);
        return ModEvents.post(new EntityTameEvent(ModRefs.living(animal), ownerRef)).isCancelled();
    }

    public static void removing(Entity entity, Entity.RemovalReason reason) {
        if (entity.isRemoved() || !ModEvents.server(entity) || !ModEvents.listening(EntityRemoveEvent.class)) {
            return;
        }
        ModEvents.post(new EntityRemoveEvent(ModRefs.entity(entity), ModEventMaps.removal(reason)));
    }

    /** Whether a raised shield takes the whole hit, as vanilla's {@code isDamageSourceBlocked} decides on this version. */
    private enum Shield {
        NONE,
        BLOCKING;

        float blocked(float damage) {
            return this == BLOCKING && damage > 0.0F ? damage : 0.0F;
        }
    }
}
