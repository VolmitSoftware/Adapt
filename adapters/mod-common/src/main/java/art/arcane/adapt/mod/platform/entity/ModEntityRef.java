package art.arcane.adapt.mod.platform.entity;

import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.ItemFrame;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.mod.platform.Compat;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.mod.platform.data.AdaptEntityData;
import art.arcane.adapt.mod.platform.data.NbtDataHolder;
import art.arcane.adapt.mod.platform.text.ComponentJson;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Entity handle. Non-player handles hold the entity they were created for and go stale with it.
 * Specialised handles (displays) extend this class for the shared entity surface.
 */
public class ModEntityRef implements EntityRef {
    private final UUID uuid;
    private final Entity entity;

    public ModEntityRef(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.uuid = entity.getUUID();
    }

    /** The current native entity. Player handles rebind across respawn. */
    public Entity handle() {
        return entity;
    }

    protected ServerLevel serverLevel() {
        return (ServerLevel) handle().level();
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public int entityId() {
        return handle().getId();
    }

    @Override
    public Id type() {
        return ModConversions.id(BuiltInRegistries.ENTITY_TYPE.getKey(handle().getType()));
    }

    @Override
    public boolean has(EntityTrait trait) {
        Entity current = handle();
        return switch (trait) {
            case MONSTER -> current instanceof Monster;
            case ENEMY -> current instanceof Enemy;
            case ANIMAL -> current instanceof Animal;
            case TAMEABLE -> current instanceof TamableAnimal || current instanceof AbstractHorse;
            case HORSE -> current instanceof AbstractHorse;
            case WATER_MOB -> current instanceof WaterAnimal;
            case GOLEM -> current instanceof AbstractGolem;
            case VILLAGER -> current instanceof AbstractVillager;
            case BOSS -> current instanceof EnderDragon || current instanceof WitherBoss;
            case UNDEAD -> current.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
            case ARMOR_STAND -> current instanceof ArmorStand;
            case DISPLAY -> current instanceof Display;
            case VEHICLE -> current instanceof VehicleEntity || current instanceof AbstractHorse || current instanceof Pig || current instanceof Strider;
            case EXPERIENCE_ORB -> current instanceof ExperienceOrb;
            case FIREBALL -> current instanceof AbstractHurtingProjectile;
            case ARROW -> current instanceof AbstractArrow;
            case FISH -> current instanceof AbstractFish;
            case ITEM_FRAME -> current instanceof ItemFrame;
            case PAINTING -> current instanceof Painting;
            case AMBIENT -> current instanceof AmbientCreature;
            case SLIME -> Compat.isSlime(current);
            case ZOMBIE -> current instanceof Zombie;
            case SKELETON -> current instanceof AbstractSkeleton;
            case SPIDER -> current instanceof Spider;
        };
    }

    @Override
    public boolean isValid() {
        Entity current = handle();
        return !current.isRemoved() && current.isAlive();
    }

    @Override
    public boolean isDead() {
        return !handle().isAlive();
    }

    @Override
    public WorldRef world() {
        return ModRefs.world(serverLevel());
    }

    @Override
    public Loc location() {
        Entity current = handle();
        return new Loc(ModRefs.world((ServerLevel) current.level()), current.getX(), current.getY(), current.getZ(), current.getYRot(), current.getXRot());
    }

    @Override
    public Vec3 velocity() {
        return ModConversions.vec(handle().getDeltaMovement());
    }

    @Override
    public void setVelocity(Vec3 velocity) {
        Entity current = handle();
        current.setDeltaMovement(ModConversions.vec(velocity));
        Compat.markVelocityChanged(current);
    }

    @Override
    public Box boundingBox() {
        return ModConversions.box(handle().getBoundingBox());
    }

    @Override
    public double height() {
        return handle().getBbHeight();
    }

    @Override
    public double width() {
        return handle().getBbWidth();
    }

    @Override
    public boolean isOnGround() {
        return handle().onGround();
    }

    @Override
    public boolean isInWater() {
        return handle().isInWater();
    }

    @Override
    public boolean isInLava() {
        return handle().isInLava();
    }

    @Override
    public float fallDistance() {
        return (float) handle().fallDistance;
    }

    @Override
    public void setFallDistance(float distance) {
        handle().fallDistance = distance;
    }

    @Override
    public int fireTicks() {
        return handle().getRemainingFireTicks();
    }

    @Override
    public void setFireTicks(int ticks) {
        handle().setRemainingFireTicks(ticks);
    }

    @Override
    public int freezeTicks() {
        return handle().getTicksFrozen();
    }

    @Override
    public void setFreezeTicks(int ticks) {
        handle().setTicksFrozen(ticks);
    }

    @Override
    public int maxFreezeTicks() {
        return handle().getTicksRequiredToFreeze();
    }

    @Override
    public boolean isFreezeTickingLocked() {
        return false;
    }

    @Override
    public int ticksLived() {
        return handle().tickCount;
    }

    @Override
    public boolean isInvulnerable() {
        return Compat.isInvulnerable(handle());
    }

    @Override
    public void setInvulnerable(boolean invulnerable) {
        Compat.setInvulnerable(handle(), invulnerable);
    }

    @Override
    public boolean isSilent() {
        return handle().isSilent();
    }

    @Override
    public void setSilent(boolean silent) {
        handle().setSilent(silent);
    }

    @Override
    public boolean hasGravity() {
        return !handle().isNoGravity();
    }

    @Override
    public void setGravity(boolean gravity) {
        handle().setNoGravity(!gravity);
    }

    @Override
    public boolean isGlowing() {
        return handle().hasGlowingTag();
    }

    @Override
    public void setGlowing(boolean glowing) {
        handle().setGlowingTag(glowing);
    }

    @Override
    public boolean isPersistent() {
        return !((AdaptEntityData) handle()).adapt$isTransient();
    }

    @Override
    public void setPersistent(boolean persistent) {
        Entity current = handle();
        ((AdaptEntityData) current).adapt$setTransient(!persistent);
    }

    @Override
    public String customName() {
        Component name = handle().getCustomName();
        return name == null ? null : ModText.legacy(name);
    }

    @Override
    public void setCustomName(String markup, boolean visible) {
        Entity current = handle();
        current.setCustomName(markup == null ? null : ModText.component(markup));
        current.setCustomNameVisible(markup != null && visible);
    }

    @Override
    public String customNameJson() {
        return ComponentJson.json(handle().getCustomName());
    }

    @Override
    public void setCustomNameJson(String json) {
        handle().setCustomName(ComponentJson.component(json));
    }

    @Override
    public EntityRef vehicle() {
        Entity vehicle = handle().getVehicle();
        return vehicle == null ? null : ModRefs.entity(vehicle);
    }

    @Override
    public List<EntityRef> passengers() {
        List<Entity> riders = handle().getPassengers();
        List<EntityRef> out = new ArrayList<>(riders.size());
        for (Entity rider : riders) {
            out.add(ModRefs.entity(rider));
        }
        return out;
    }

    @Override
    public boolean addPassenger(EntityRef passenger) {
        return ModRefs.entity(passenger).startRiding(handle(), true, true);
    }

    @Override
    public boolean eject() {
        Entity current = handle();
        if (current.getPassengers().isEmpty()) {
            return false;
        }
        current.ejectPassengers();
        return true;
    }

    @Override
    public List<EntityRef> nearbyEntities(double radiusX, double radiusY, double radiusZ) {
        Entity current = handle();
        List<Entity> found = current.level().getEntities(current, current.getBoundingBox().inflate(radiusX, radiusY, radiusZ));
        List<EntityRef> out = new ArrayList<>(found.size());
        for (Entity other : found) {
            out.add(ModRefs.entity(other));
        }
        return out;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        TeleportCause chosen = cause == null ? TeleportCause.PLUGIN : cause;
        if (server.isSameThread()) {
            return CompletableFuture.completedFuture(PlayerHooks.withTeleportCause(chosen, () -> teleportNow(destination)));
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.execute(() -> result.complete(PlayerHooks.withTeleportCause(chosen, () -> teleportNow(destination))));
        return result;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause, boolean retainPassengers) {
        return teleport(destination, cause);
    }

    private boolean teleportNow(Loc destination) {
        Entity current = handle();
        if (current.isRemoved()) {
            return false;
        }
        ServerLevel target = ModRefs.world(destination.world());
        if (target == null) {
            return false;
        }
        return current.teleportTo(target, destination.x(), destination.y(), destination.z(), Set.of(), destination.yaw(), destination.pitch(), true);
    }

    @Override
    public void remove() {
        handle().discard();
    }

    @Override
    public DataHolder data() {
        return new NbtDataHolder(new EntityStore());
    }

    @Override
    public UUID tameOwner() {
        if (handle() instanceof OwnableEntity ownable) {
            EntityReference<LivingEntity> owner = ownable.getOwnerReference();
            return owner == null ? null : owner.getUUID();
        }
        return null;
    }

    @Override
    public EntityRef responsibleEntity() {
        Entity handle = handle();
        LivingEntity source = null;
        if (handle instanceof PrimedTnt tnt) {
            source = tnt.getOwner();
        } else if (handle instanceof AreaEffectCloud cloud) {
            source = cloud.getOwner();
        } else if (handle instanceof EvokerFangs fangs) {
            source = fangs.getOwner();
        }
        return source == null ? null : ModRefs.entity(source);
    }

    @Override
    public Object unwrap() {
        return handle();
    }

    @Override
    public final boolean equals(Object other) {
        return other instanceof EntityRef that && uuid.equals(that.uuid());
    }

    @Override
    public final int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + uuid + "]";
    }

    private final class EntityStore implements NbtDataHolder.Store {
        @Override
        public CompoundTag read() {
            return ((AdaptEntityData) handle()).adapt$data();
        }

        @Override
        public void write(NbtDataHolder.Edit edit) {
            AdaptEntityData data = (AdaptEntityData) handle();
            CompoundTag tag = data.adapt$dataOrCreate();
            edit.apply(tag);
            if (tag.isEmpty()) {
                data.adapt$setData(null);
            }
        }
    }
}
