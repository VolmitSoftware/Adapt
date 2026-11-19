package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.AbstractArrowAccess;
import art.arcane.adapt.mod.platform.mixin.ArrowAccess;
import art.arcane.adapt.mod.platform.mixin.ProjectileAccess;
import art.arcane.adapt.mod.platform.mixin.SpectralArrowAccess;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Set;
import java.util.UUID;

public class ModProjectileRef extends ModEntityRef implements ProjectileRef {
    public ModProjectileRef(Projectile projectile) {
        super(projectile);
    }

    public Projectile projectile() {
        return (Projectile) handle();
    }

    @Override
    public EntityRef shooter() {
        Entity owner = projectile().getOwner();
        return owner == null ? null : ModRefs.entity(owner);
    }

    @Override
    public UUID shooterId() {
        return ((ProjectileAccess) projectile()).adapt$owner();
    }

    @Override
    public void setShooter(EntityRef shooter) {
        projectile().setOwner(shooter == null ? null : ModRefs.entity(shooter));
    }

    @Override
    public ItemRef item() {
        Projectile current = projectile();
        if (current instanceof AbstractArrow arrow) {
            return ModRefs.item(((AbstractArrowAccess) arrow).adapt$pickupItem());
        }
        if (current instanceof ItemSupplier supplier) {
            return ModRefs.item(supplier.getItem().copy());
        }
        return ModRefs.item(ItemStack.EMPTY);
    }

    @Override
    public void setItem(ItemRef item) {
        if (projectile() instanceof ThrowableItemProjectile throwable) {
            throwable.setItem(ModRefs.item(item).copy());
        }
    }

    @Override
    public double damage() {
        return projectile() instanceof AbstractArrow arrow ? ((AbstractArrowAccess) arrow).adapt$baseDamage() : Double.NaN;
    }

    @Override
    public void setDamage(double damage) {
        if (projectile() instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(damage);
        }
    }

    @Override
    public boolean isCritical() {
        return projectile() instanceof AbstractArrow arrow && arrow.isCritArrow();
    }

    @Override
    public void setCritical(boolean critical) {
        if (projectile() instanceof AbstractArrow arrow) {
            arrow.setCritArrow(critical);
        }
    }

    @Override
    public boolean canPickup() {
        return projectile() instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED;
    }

    @Override
    public void setCanPickup(boolean pickup) {
        if (projectile() instanceof AbstractArrow arrow) {
            arrow.pickup = pickup ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.DISALLOWED;
        }
    }

    @Override
    public ArrowPickup pickupRule() {
        if (!(projectile() instanceof AbstractArrow arrow)) {
            return ArrowPickup.DISALLOWED;
        }
        return switch (arrow.pickup) {
            case ALLOWED -> ArrowPickup.ALLOWED;
            case CREATIVE_ONLY -> ArrowPickup.CREATIVE_ONLY;
            case DISALLOWED -> ArrowPickup.DISALLOWED;
        };
    }

    @Override
    public void setPickupRule(ArrowPickup rule) {
        if (projectile() instanceof AbstractArrow arrow) {
            arrow.pickup = switch (rule) {
                case ALLOWED -> AbstractArrow.Pickup.ALLOWED;
                case CREATIVE_ONLY -> AbstractArrow.Pickup.CREATIVE_ONLY;
                case DISALLOWED -> AbstractArrow.Pickup.DISALLOWED;
            };
        }
    }

    @Override
    public int pierceLevel() {
        return projectile() instanceof AbstractArrow arrow ? arrow.getPierceLevel() : 0;
    }

    @Override
    public void setPierceLevel(int level) {
        if (projectile() instanceof AbstractArrow arrow) {
            ((AbstractArrowAccess) arrow).adapt$setPierceLevel((byte) Math.max(0, Math.min(127, level)));
        }
    }

    @Override
    public boolean isInBlock() {
        return projectile() instanceof AbstractArrow arrow && ((AbstractArrowAccess) arrow).adapt$isInGround();
    }

    @Override
    public boolean shotFromCrossbow() {
        if (!(projectile() instanceof AbstractArrow arrow)) {
            return false;
        }
        return arrow.shotFromCrossbow();
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        Projectile current = projectile();
        current.setYRot(yaw % 360.0F);
        current.setXRot(Mth.clamp(pitch, -90.0F, 90.0F) % 360.0F);
        current.yRotO = current.getYRot();
        current.xRotO = current.getXRot();
    }

    @Override
    public PotionContents arrowPotion() {
        if (!(projectile() instanceof Arrow arrow)) {
            return PotionContents.EMPTY;
        }
        return ModRefs.item(((AbstractArrowAccess) arrow).adapt$pickupItem()).potionContents()
            .map((PotionContents contents) -> new PotionContents(contents.potion(), null, contents.customEffects(), null))
            .orElse(PotionContents.EMPTY);
    }

    @Override
    public void setArrowPotion(PotionContents contents) {
        if (!(projectile() instanceof Arrow arrow)) {
            return;
        }
        Set<MobEffectInstance> effects = ((ArrowAccess) arrow).adapt$effects();
        effects.clear();
        for (EffectInstance effect : contents.customEffects()) {
            Holder<MobEffect> type = ModConversions.effect(effect.type());
            if (type != null) {
                int duration = effect.isInfinite() ? MobEffectInstance.INFINITE_DURATION : effect.durationTicks();
                effects.add(new MobEffectInstance(type.value(), duration, effect.amplifier(), effect.ambient(), effect.particles(),
                    effect.icon()));
            }
        }
        Holder<Potion> potion = contents.potion() == null ? null
            : ModContentAliases.holder(BuiltInRegistries.POTION, RegistryKind.POTION, contents.potion());
        ((ArrowAccess) arrow).adapt$setPotion(potion == null ? Potions.EMPTY : potion.value());
        ((ArrowAccess) arrow).adapt$updateColor();
    }

    @Override
    public int glowingTicks() {
        return projectile() instanceof SpectralArrow spectral ? ((SpectralArrowAccess) spectral).adapt$duration() : 0;
    }

    @Override
    public void setGlowingTicks(int ticks) {
        if (projectile() instanceof SpectralArrow spectral) {
            ((SpectralArrowAccess) spectral).adapt$setDuration(ticks);
        }
    }

    @Override
    public ProjectileSnapshot snapshot() {
        return ModProjectileSnapshot.capture(projectile());
    }
}
