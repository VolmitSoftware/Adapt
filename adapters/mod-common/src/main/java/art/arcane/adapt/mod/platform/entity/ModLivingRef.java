package art.arcane.adapt.mod.platform.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.platform.Compat;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.LivingEntityAccess;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import art.arcane.adapt.mod.platform.mixin.AttributeMapAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ModLivingRef extends ModEntityRef implements LivingRef {
    public ModLivingRef(LivingEntity entity) {
        super(entity);
    }

    public LivingEntity living() {
        return (LivingEntity) handle();
    }

    @Override
    public double health() {
        return living().getHealth();
    }

    @Override
    public void setHealth(double health) {
        living().setHealth((float) health);
    }

    @Override
    public double maxHealth() {
        return living().getMaxHealth();
    }

    @Override
    public double absorption() {
        return living().getAbsorptionAmount();
    }

    @Override
    public void setAbsorption(double absorption) {
        living().setAbsorptionAmount((float) absorption);
    }

    @Override
    public void damage(double amount, EntityRef source) {
        LivingEntity target = living();
        ServerLevel level = serverLevel();
        DamageSource damage;
        Entity attacker = source == null ? null : ModRefs.entity(source);
        if (attacker instanceof Player player) {
            damage = level.damageSources().playerAttack(player);
        } else if (attacker instanceof LivingEntity living) {
            damage = level.damageSources().mobAttack(living);
        } else {
            damage = level.damageSources().generic();
        }
        target.hurtServer(level, damage, (float) amount);
    }

    @Override
    public void damage(double amount, Id damageType) {
        LivingEntity target = living();
        ServerLevel level = serverLevel();
        DamageSource damage = level.damageSources().generic();
        if (damageType != null) {
            ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, ModRefs.identifier(damageType));
            java.util.Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE).get(key);
            if (holder.isPresent()) {
                damage = new DamageSource(holder.get());
            }
        }
        target.hurtServer(level, damage, (float) amount);
    }

    @Override
    public void damage(double amount, Id damageType, EntityRef source) {
        ServerLevel level = serverLevel();
        java.util.Optional<Holder.Reference<DamageType>> holder = damageType == null ? java.util.Optional.empty()
                : level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .get(ResourceKey.create(Registries.DAMAGE_TYPE, ModRefs.identifier(damageType)));
        if (holder.isEmpty()) {
            damage(amount, source);
            return;
        }
        Entity attacker = source == null ? null : ModRefs.entity(source);
        living().hurtServer(level, new DamageSource(holder.get(), attacker), (float) amount);
    }

    @Override
    public void heal(double amount) {
        living().heal((float) amount);
    }

    @Override
    public int noDamageTicks() {
        return Compat.damageCooldown(living());
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        Compat.setDamageCooldown(living(), ticks);
    }

    @Override
    public void setLastHurtAmount(double amount) {
        ((LivingEntityAccess) living()).adapt$setLastHurt((float) amount);
    }

    @Override
    public Loc eyeLocation() {
        LivingEntity current = living();
        Vec3 eye = current.getEyePosition();
        return new Loc(ModRefs.world(serverLevel()), eye.x, eye.y, eye.z, current.getYRot(), current.getXRot());
    }

    @Override
    public boolean isSwimming() {
        return living().isSwimming();
    }

    @Override
    public void setSwimming(boolean swimming) {
        living().setSwimming(swimming);
    }

    @Override
    public int remainingAir() {
        return living().getAirSupply();
    }

    @Override
    public void setRemainingAir(int ticks) {
        living().setAirSupply(ticks);
    }

    @Override
    public int maximumAir() {
        return living().getMaxAirSupply();
    }

    @Override
    public boolean isGliding() {
        return living().isFallFlying();
    }

    @Override
    public void setGliding(boolean gliding) {
        LivingEntity current = living();
        if (!gliding) {
            current.stopFallFlying();
        } else if (current instanceof Player player) {
            player.startFallFlying();
        }
    }

    @Override
    public boolean isClimbing() {
        return living().onClimbable();
    }

    @Override
    public boolean isHandRaised() {
        return living().isUsingItem();
    }

    @Override
    public boolean isInvisible() {
        return living().isInvisible();
    }

    @Override
    public void setInvisible(boolean invisible) {
        living().setInvisible(invisible);
    }

    @Override
    public boolean hasAi() {
        return !(living() instanceof Mob mob) || !mob.isNoAi();
    }

    @Override
    public void setAi(boolean ai) {
        if (living() instanceof Mob mob) {
            mob.setNoAi(!ai);
        }
    }

    @Override
    public PlayerRef killer() {
        LivingEntity credit = living().getKillCredit();
        return credit instanceof ServerPlayer player ? ModRefs.player(player) : null;
    }

    @Override
    public double attributeValue(Id attribute) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        return holder == null || !living().getAttributes().hasAttribute(holder) ? Double.NaN : living().getAttributes().getValue(holder);
    }

    @Override
    public double attributeBase(Id attribute) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        return holder == null || !living().getAttributes().hasAttribute(holder) ? Double.NaN : living().getAttributes().getBaseValue(holder);
    }

    @Override
    public void setAttributeBase(Id attribute, double value) {
        AttributeInstance instance = instance(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public boolean hasAttribute(Id attribute) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        return holder != null && living().getAttributes().hasAttribute(holder);
    }

    @Override
    public boolean setModifier(Id attribute, AttributeMod modifier) {
        AttributeInstance instance = instance(attribute);
        if (instance == null) {
            return false;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(ModConversions.identifier(modifier.key()), modifier.amount(), operation(modifier.operation())));
        return true;
    }

    @Override
    public boolean setTransientModifier(Id attribute, AttributeMod modifier) {
        AttributeInstance instance = instance(attribute);
        if (instance == null) {
            return false;
        }
        AttributeModifier created = new AttributeModifier(ModConversions.identifier(modifier.key()), modifier.amount(), operation(modifier.operation()));
        instance.removeModifier(created.id());
        instance.addTransientModifier(created);
        return true;
    }

    @Override
    public void removeModifier(Id attribute, Id modifierKey) {
        AttributeInstance instance = existing(living(), attribute);
        if (instance != null) {
            instance.removeModifier(ModConversions.identifier(modifierKey));
        }
    }

    @Override
    public AttributeMod modifier(Id attribute, Id modifierKey) {
        AttributeInstance instance = existing(living(), attribute);
        if (instance == null) {
            return null;
        }
        AttributeModifier modifier = instance.getModifier(ModConversions.identifier(modifierKey));
        return modifier == null ? null : new AttributeMod(modifierKey, modifier.amount(), operation(modifier.operation()));
    }

    @Override
    public boolean addEffect(EffectInstance effect) {
        Holder<MobEffect> type = ModConversions.effect(effect.type());
        if (type == null) {
            return false;
        }
        MobEffectInstance instance = new MobEffectInstance(type, effect.durationTicks(), effect.amplifier(), effect.ambient(), effect.particles(),
            effect.icon());
        CombatHooks.withEffectCause(EntityPotionEffectEvent.Cause.PLUGIN, () -> {
            living().forceAddEffect(instance, null);
            return null;
        });
        return living().getEffect(type) == instance;
    }

    @Override
    public EffectInstance effect(Id type) {
        Holder<MobEffect> holder = ModConversions.effect(type);
        if (holder == null) {
            return null;
        }
        MobEffectInstance instance = living().getEffect(holder);
        return instance == null ? null : effect(instance);
    }

    @Override
    public boolean hasEffect(Id type) {
        Holder<MobEffect> holder = ModConversions.effect(type);
        return holder != null && living().hasEffect(holder);
    }

    @Override
    public void removeEffect(Id type) {
        Holder<MobEffect> holder = ModConversions.effect(type);
        if (holder != null) {
            CombatHooks.withEffectCause(EntityPotionEffectEvent.Cause.PLUGIN, () -> living().removeEffect(holder));
        }
    }

    @Override
    public Collection<EffectInstance> effects() {
        Collection<MobEffectInstance> active = living().getActiveEffects();
        List<EffectInstance> out = new ArrayList<>(active.size());
        for (MobEffectInstance instance : active) {
            EffectInstance converted = effect(instance);
            if (converted != null) {
                out.add(converted);
            }
        }
        return out;
    }

    @Override
    public ItemRef equipment(EquipmentSlot slot) {
        return ModRefs.item(living().getItemBySlot(ModConversions.slot(slot)));
    }

    @Override
    public void setEquipment(EquipmentSlot slot, ItemRef item) {
        living().setItemSlot(ModConversions.slot(slot), ModRefs.item(item).copy());
    }

    @Override
    public List<AttributeMod> modifiers(Id attribute) {
        AttributeInstance instance = existing(living(), attribute);
        if (instance == null) {
            return List.of();
        }
        List<AttributeMod> out = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            out.add(new AttributeMod(ModConversions.id(modifier.id()), modifier.amount(), operation(modifier.operation())));
        }
        return out;
    }

    @Override
    public void setEquipmentDropChance(EquipmentSlot slot, float chance) {
        if (living() instanceof Mob mob) {
            mob.setDropChance(ModConversions.slot(slot), chance);
        }
    }

    @Override
    public void damageEquipment(EquipmentSlot slot, int amount) {
        if (amount <= 0) {
            return;
        }
        LivingEntity current = living();
        net.minecraft.world.entity.EquipmentSlot vanilla = ModConversions.slot(slot);
        ItemStack item = current.getItemBySlot(vanilla);
        if (!item.isEmpty()) {
            item.hurtAndBreak(amount, current, vanilla);
        }
    }

    @Override
    public void playEquipmentBreak(EquipmentSlot slot) {
        byte event = switch (slot) {
            case MAIN_HAND -> (byte) 47;
            case OFF_HAND -> (byte) 48;
            case HEAD -> (byte) 49;
            case CHEST -> (byte) 50;
            case LEGS -> (byte) 51;
            case FEET -> (byte) 52;
            case BODY -> (byte) 65;
            case SADDLE -> (byte) 68;
        };
        serverLevel().broadcastEntityEvent(living(), event);
    }

    @Override
    public boolean canPickupItems() {
        return living().canPickUpLoot();
    }

    @Override
    public void setCanPickupItems(boolean pickup) {
        if (living() instanceof Mob mob) {
            mob.setCanPickUpLoot(pickup);
        }
    }

    @Override
    public void setRemoveWhenFarAway(boolean remove) {
        if (!remove && living() instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    @Override
    public double regainHealth(double amount, EntityRegainHealthEvent.Reason reason) {
        LivingEntity current = living();
        if (!Double.isFinite(amount) || amount <= 0D || current.isDeadOrDying()) {
            return 0D;
        }
        double before = current.getHealth();
        if (before <= 0D || before >= current.getMaxHealth()) {
            return 0D;
        }
        CombatHooks.healReason(reason);
        try {
            current.heal((float) amount);
        } finally {
            CombatHooks.healReason(EntityRegainHealthEvent.Reason.CUSTOM);
        }
        return Math.max(0D, current.getHealth() - before);
    }

    @Override
    public LastDamage lastDamage() {
        return ModLastDamage.of(living());
    }

    @Override
    public List<BlockRef> lastTwoTargetBlocks(int maxDistance) {
        ServerLevel level = serverLevel();
        Vec3 start = living().getEyePosition();
        Vec3 direction = living().getViewVector(1.0F).normalize();
        double limit = Math.min(120, Math.max(0, maxDistance));
        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int stepX = direction.x > 0 ? 1 : direction.x < 0 ? -1 : 0;
        int stepY = direction.y > 0 ? 1 : direction.y < 0 ? -1 : 0;
        int stepZ = direction.z > 0 ? 1 : direction.z < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0D / direction.x);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0D / direction.y);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0D / direction.z);
        double nextX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? (x + 1 - start.x) : (start.x - x)) * deltaX;
        double nextY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? (y + 1 - start.y) : (start.y - y)) * deltaY;
        double nextZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? (z + 1 - start.z) : (start.z - z)) * deltaZ;
        BlockRef previous = null;
        BlockRef current = ModRefs.block(level, new BlockPos(x, y, z));
        while (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
            double travelled = Math.min(nextX, Math.min(nextY, nextZ));
            if (travelled > limit) {
                break;
            }
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                y += stepY;
                nextY += deltaY;
            } else {
                z += stepZ;
                nextZ += deltaZ;
            }
            previous = current;
            current = ModRefs.block(level, new BlockPos(x, y, z));
        }
        return previous == null ? List.of(current) : List.of(previous, current);
    }

    @Override
    public BlockRef targetBlock(double range) {
        HitResult hit = living().pick(range, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) {
            return null;
        }
        return ModRefs.block(serverLevel(), block.getBlockPos());
    }

    @Override
    public boolean hasLineOfSight(EntityRef other) {
        return living().hasLineOfSight(ModRefs.entity(other));
    }

    @Override
    public void swingHand(Hand hand) {
        Compat.swing(living(), ModConversions.hand(hand));
    }

    @Override
    public int maxNoDamageTicks() {
        return 20;
    }

    @Override
    public ItemRef activeItem() {
        return ModRefs.item(living().getUseItem().copy());
    }

    private AttributeInstance instance(Id attribute) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        return holder == null ? null : living().getAttribute(holder);
    }

    /**
     * The attribute's instance only if the entity already has one. Creating instances just to read them would make
     * loader attributes (Forge, NeoForge) syncable to vanilla clients, which cannot decode them.
     */
    public static AttributeInstance existing(LivingEntity living, Id attribute) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        return holder == null ? null : ((AttributeMapAccess) living.getAttributes()).adapt$attributes().get(holder);
    }

    private static EffectInstance effect(MobEffectInstance instance) {
        Id type = instance.getEffect().unwrapKey().map((ResourceKey<MobEffect> key) -> ModConversions.id(key.identifier())).orElse(null);
        if (type == null) {
            return null;
        }
        return new EffectInstance(type, instance.getDuration(), instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }

    private static AttributeModifier.Operation operation(AttributeOperation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private static AttributeOperation operation(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeOperation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeOperation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
        };
    }
}
