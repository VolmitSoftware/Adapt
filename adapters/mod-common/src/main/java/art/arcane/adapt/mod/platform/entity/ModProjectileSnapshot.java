package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.Set;
import java.util.function.Consumer;

/** Projectile copied through its saved NBT (without its UUID), so every vanilla and Adapt field travels along. */
final class ModProjectileSnapshot implements ProjectileSnapshot {
    private static final Set<Id> COPYABLE = Set.of(
        Id.minecraft("arrow"),
        Id.minecraft("spectral_arrow"),
        Id.minecraft("trident"),
        Id.minecraft("snowball"),
        Id.minecraft("egg"),
        Id.minecraft("ender_pearl"),
        Id.minecraft("splash_potion"),
        Id.minecraft("lingering_potion"),
        Id.minecraft("experience_bottle")
    );

    private final EntityType<?> type;
    private final Id typeId;
    private final CompoundTag data;
    private final ItemStack item;

    private ModProjectileSnapshot(EntityType<?> type, Id typeId, CompoundTag data, ItemStack item) {
        this.type = type;
        this.typeId = typeId;
        this.data = data;
        this.item = item;
    }

    static ModProjectileSnapshot capture(Projectile source) {
        Id typeId = ModConversions.id(EntityType.getKey(source.getType()));
        if (!COPYABLE.contains(typeId)) {
            return null;
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, source.registryAccess());
        source.saveWithoutId(output);
        CompoundTag data = output.buildResult();
        data.remove("UUID");
        ItemStack item = switch (source) {
            case AbstractArrow arrow -> arrow.getPickupItemStackOrigin().copy();
            case ItemSupplier supplier -> supplier.getItem().copy();
            default -> ItemStack.EMPTY;
        };
        return new ModProjectileSnapshot(source.getType(), typeId, data, item);
    }

    @Override
    public Id type() {
        return typeId;
    }

    @Override
    public ItemRef item() {
        return ModRefs.item(item.copy());
    }

    @Override
    public ProjectileRef spawn(Loc location, Vec3 velocity, PlayerRef shooter, Consumer<ProjectileRef> beforeAdd) {
        ServerLevel level = ModRefs.world(location.world());
        if (level == null) {
            return null;
        }
        Entity created = type.create(level, EntitySpawnReason.COMMAND);
        if (!(created instanceof Projectile copy)) {
            return null;
        }
        HolderLookup.Provider registries = level.registryAccess();
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, data.copy()));
        copy.snapTo(location.x(), location.y(), location.z(), copy.getYRot(), copy.getXRot());
        copy.setDeltaMovement(ModConversions.vec(velocity));
        copy.setOwner(shooter == null ? null : ModRefs.player(shooter));
        ProjectileRef ref = (ProjectileRef) ModRefs.entity(copy);
        if (beforeAdd != null) {
            beforeAdd.accept(ref);
        }
        return level.addFreshEntity(copy) ? ref : null;
    }
}
