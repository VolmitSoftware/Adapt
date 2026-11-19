package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.MushroomCowAccess;
import art.arcane.adapt.mod.platform.mixin.VillagerAccess;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class ModAnimalOps implements AnimalOps {
    @Override
    public boolean isPeacefulAnimalType(Id entityType) {
        if (entityType == null) {
            return false;
        }
        ResourceLocation location = ModContentAliases.location(BuiltInRegistries.ENTITY_TYPE, RegistryKind.ENTITY_TYPE, entityType);
        Optional<EntityType<?>> type = location == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(location);
        if (type.isEmpty()) {
            return false;
        }
        Class<? extends Entity> base = type.get().getBaseClass();
        return Animal.class.isAssignableFrom(base) && !Enemy.class.isAssignableFrom(base);
    }

    @Override
    public boolean isAdult(EntityRef entity) {
        return handle(entity) instanceof AgeableMob ageable && !ageable.isBaby();
    }

    @Override
    public int age(EntityRef entity) {
        return handle(entity) instanceof AgeableMob ageable ? ageable.getAge() : 0;
    }

    @Override
    public void setAge(EntityRef entity, int age) {
        if (handle(entity) instanceof AgeableMob ageable) {
            ageable.setAge(age);
        }
    }

    @Override
    public boolean canBreed(EntityRef entity) {
        return handle(entity) instanceof AgeableMob ageable && ageable.getAge() == 0;
    }

    @Override
    public boolean isInLove(EntityRef entity) {
        return handle(entity) instanceof Animal animal && animal.isInLove();
    }

    @Override
    public UUID loveCause(EntityRef entity) {
        if (!(handle(entity) instanceof Animal animal)) {
            return null;
        }
        ServerPlayer cause = animal.getLoveCause();
        return cause == null ? null : cause.getUUID();
    }

    @Override
    public void setInLove(EntityRef entity, int ticks) {
        if (handle(entity) instanceof Animal animal) {
            animal.resetLove();
            animal.setInLoveTime(ticks);
        }
    }

    @Override
    public boolean isBreedItem(EntityRef entity, Id item) {
        if (!(handle(entity) instanceof Animal animal) || item == null) {
            return false;
        }
        ResourceLocation location = ModContentAliases.location(BuiltInRegistries.ITEM, RegistryKind.ITEM, item);
        Optional<Item> found = location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
        return found.isPresent() && found.get() != Items.AIR && animal.isFood(new ItemStack(found.get()));
    }

    @Override
    public boolean isSheared(EntityRef entity) {
        return handle(entity) instanceof Sheep sheep && sheep.isSheared();
    }

    @Override
    public void setSheared(EntityRef entity, boolean sheared) {
        if (handle(entity) instanceof Sheep sheep) {
            sheep.setSheared(sheared);
        }
    }

    @Override
    public DyeColor woolColor(EntityRef entity) {
        if (!(handle(entity) instanceof Sheep sheep)) {
            return null;
        }
        net.minecraft.world.item.DyeColor color = sheep.getColor();
        return color == null ? null : DyeColor.values()[color.ordinal()];
    }

    @Override
    public boolean hasStewEffect(EntityRef entity) {
        if (!(handle(entity) instanceof MushroomCow mooshroom)) {
            return false;
        }
        return ((MushroomCowAccess) mooshroom).adapt$stewEffects() != null;
    }

    @Override
    public boolean isLeashed(EntityRef entity) {
        return handle(entity) instanceof Mob mob && mob.isLeashed();
    }

    @Override
    public boolean isTamed(EntityRef entity) {
        Entity handle = handle(entity);
        return (handle instanceof TamableAnimal tamable && tamable.isTame())
            || (handle instanceof AbstractHorse horse && horse.isTamed());
    }

    @Override
    public boolean carriesChest(EntityRef entity) {
        return handle(entity) instanceof AbstractChestedHorse horse && horse.hasChest();
    }

    @Override
    public List<ItemRef> rollLoot(EntityRef entity, Random random) {
        Entity handle = handle(entity);
        if (handle == null || !(handle.level() instanceof ServerLevel level)) {
            return List.of();
        }
        LootTable table = level.getServer().getLootData().getLootTable(lootTable(handle));
        DamageSource source = handle instanceof LivingEntity living && living.getLastDamageSource() != null
            ? living.getLastDamageSource()
            : level.damageSources().generic();
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, handle)
            .withParameter(LootContextParams.ORIGIN, handle.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .create(LootContextParamSets.ENTITY);
        List<ItemRef> rolled = new ArrayList<>();
        for (ItemStack stack : table.getRandomItems(params, random.nextLong())) {
            rolled.add(ModRefs.item(stack));
        }
        return rolled;
    }

    @Override
    public List<ItemRef> rollLoot(EntityRef entity, PlayerRef killer, float luck, Random random) {
        Entity handle = handle(entity);
        if (handle == null || !(handle.level() instanceof ServerLevel level)) {
            return List.of();
        }
        LootTable table = level.getServer().getLootData().getLootTable(lootTable(handle));
        ServerPlayer player = killer == null ? null : ModRefs.player(killer);
        LootParams.Builder builder = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, handle)
            .withParameter(LootContextParams.ORIGIN, handle.position())
            .withLuck(luck);
        if (player != null) {
            builder.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.KILLER_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        } else {
            builder.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
        }
        List<ItemRef> rolled = new ArrayList<>();
        for (ItemStack stack : table.getRandomItems(builder.create(LootContextParamSets.ENTITY), random.nextLong())) {
            rolled.add(ModRefs.item(stack));
        }
        return rolled;
    }

    @Override
    public boolean mobLoot(WorldRef world) {
        ServerLevel level = world == null ? null : ModRefs.world(world);
        return level != null && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
    }

    @Override
    public boolean inActiveRaid(EntityRef entity) {
        if (!(handle(entity) instanceof Raider raider)) {
            return false;
        }
        Raid raid = raider.getCurrentRaid();
        return raid != null && !raid.isStopped() && !raid.isOver();
    }

    @Override
    public boolean tame(EntityRef entity, PlayerRef owner) {
        ServerPlayer player = owner == null ? null : ModRefs.player(owner);
        if (player == null) {
            return false;
        }
        Entity handle = handle(entity);
        if (handle instanceof TamableAnimal tamable) {
            if (tamable.isTame()) {
                return false;
            }
            tamable.tame(player);
            return tamable.isTame();
        }
        if (handle instanceof AbstractHorse horse) {
            return !horse.isTamed() && horse.tameWithName(player);
        }
        return false;
    }

    @Override
    public boolean isSitting(EntityRef entity) {
        return switch (handle(entity)) {
            case TamableAnimal tamable -> tamable.isInSittingPose();
            case Fox fox -> fox.isSitting();
            case Panda panda -> panda.isSitting();
            case Camel camel -> camel.isCamelSitting();
            case null, default -> false;
        };
    }

    @Override
    public void setSitting(EntityRef entity, boolean sitting) {
        switch (handle(entity)) {
            case TamableAnimal tamable -> {
                tamable.setInSittingPose(sitting);
                tamable.setOrderedToSit(sitting);
            }
            case Fox fox -> fox.setSitting(sitting);
            case Panda panda -> panda.sit(sitting);
            case Camel camel -> {
                if (sitting) {
                    camel.sitDown();
                } else {
                    camel.standUp();
                }
            }
            case null, default -> {
            }
        }
    }

    @Override
    public void setAngry(EntityRef entity, boolean angry) {
        if (handle(entity) instanceof Wolf wolf) {
            if (angry) {
                wolf.startPersistentAngerTimer();
            } else {
                wolf.stopBeingAngry();
            }
        }
    }

    @Override
    public DyeColor collarColor(EntityRef entity) {
        net.minecraft.world.item.DyeColor color = switch (handle(entity)) {
            case Wolf wolf -> wolf.getCollarColor();
            case Cat cat -> cat.getCollarColor();
            case null, default -> null;
        };
        return color == null ? null : DyeColor.values()[color.ordinal()];
    }

    @Override
    public void setShivering(EntityRef entity, boolean shivering) {
        if (handle(entity) instanceof Strider strider) {
            strider.setSuffocating(shivering);
        }
    }

    @Override
    public void shakeHead(EntityRef entity) {
        if (handle(entity) instanceof VillagerAccess villager) {
            villager.adapt$setUnhappy();
        }
    }

    @Override
    public void setAggressive(EntityRef entity, boolean aggressive) {
        if (handle(entity) instanceof Mob mob) {
            mob.setAggressive(aggressive);
        }
    }

    @Override
    public LivingRef angryAt(EntityRef warden) {
        if (!(handle(warden) instanceof Warden handle)) {
            return null;
        }
        return handle.getEntityAngryAt().map(ModRefs::living).orElse(null);
    }

    @Override
    public void clearAnger(EntityRef warden, EntityRef target) {
        Entity angered = handle(target);
        if (handle(warden) instanceof Warden handle && angered != null) {
            handle.clearAnger(angered);
        }
    }

    private static ResourceLocation lootTable(Entity entity) {
        return entity instanceof LivingEntity living ? living.getLootTable() : entity.getType().getDefaultLootTable();
    }

    private static Entity handle(EntityRef entity) {
        return entity == null ? null : ModRefs.entity(entity);
    }
}
