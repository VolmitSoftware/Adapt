package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class BukkitAnimalOps implements AnimalOps {
  @Override
  public boolean isPeacefulAnimalType(Id entityType) {
    EntityType type = entityType == null ? null : BukkitRefs.entityType(entityType);
    if (type == null) {
      return false;
    }
    Class<? extends Entity> entityClass = type.getEntityClass();
    return entityClass != null && Animals.class.isAssignableFrom(entityClass) && !Enemy.class.isAssignableFrom(entityClass);
  }

  @Override
  public boolean isAdult(EntityRef entity) {
    return handle(entity) instanceof Ageable ageable && ageable.isAdult();
  }

  @Override
  public int age(EntityRef entity) {
    return handle(entity) instanceof Ageable ageable ? ageable.getAge() : 0;
  }

  @Override
  public void setAge(EntityRef entity, int age) {
    if (handle(entity) instanceof Ageable ageable) {
      ageable.setAge(age);
    }
  }

  @Override
  public boolean canBreed(EntityRef entity) {
    return handle(entity) instanceof Breedable breedable && breedable.canBreed();
  }

  @Override
  public boolean isInLove(EntityRef entity) {
    return handle(entity) instanceof Animals animal && animal.isLoveMode();
  }

  @Override
  public UUID loveCause(EntityRef entity) {
    return handle(entity) instanceof Animals animal ? animal.getBreedCause() : null;
  }

  @Override
  public void setInLove(EntityRef entity, int ticks) {
    if (handle(entity) instanceof Animals animal) {
      animal.setBreedCause(null);
      animal.setLoveModeTicks(ticks);
    }
  }

  @Override
  public boolean isBreedItem(EntityRef entity, Id item) {
    if (!(handle(entity) instanceof Animals animal) || item == null) {
      return false;
    }
    Material material = BukkitRefs.material(item);
    return material != null && animal.isBreedItem(material);
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
    org.bukkit.DyeColor color = sheep.getColor();
    return color == null ? null : DyeColor.valueOf(color.name());
  }

  @Override
  public boolean hasStewEffect(EntityRef entity) {
    return handle(entity) instanceof MushroomCow mooshroom && mooshroom.hasEffectsForNextStew();
  }

  @Override
  public boolean isLeashed(EntityRef entity) {
    return handle(entity) instanceof LivingEntity living && living.isLeashed();
  }

  @Override
  public boolean isTamed(EntityRef entity) {
    return handle(entity) instanceof Tameable tameable && tameable.isTamed();
  }

  @Override
  public boolean carriesChest(EntityRef entity) {
    return handle(entity) instanceof ChestedHorse horse && horse.isCarryingChest();
  }

  @Override
  public List<ItemRef> rollLoot(EntityRef entity, Random random) {
    Entity handle = handle(entity);
    if (!(handle instanceof Lootable lootable)) {
      return List.of();
    }
    LootTable table = lootable.getLootTable();
    if (table == null) {
      return List.of();
    }
    List<ItemRef> rolled = new ArrayList<>();
    for (ItemStack stack : table.populateLoot(random, new LootContext.Builder(handle.getLocation()).lootedEntity(handle).build())) {
      rolled.add(BukkitRefs.item(stack));
    }
    return rolled;
  }

  @Override
  public List<ItemRef> rollLoot(EntityRef entity, PlayerRef killer, float luck, Random random) {
    Entity handle = handle(entity);
    if (!(handle instanceof Lootable lootable)) {
      return List.of();
    }
    LootTable table = lootable.getLootTable();
    if (table == null) {
      return List.of();
    }
    Player player = killer == null ? null : BukkitRefs.player(killer);
    LootContext context = new LootContext.Builder(handle.getLocation())
        .lootedEntity(handle)
        .killer(player)
        .luck(luck)
        .build();
    List<ItemRef> rolled = new ArrayList<>();
    for (ItemStack stack : table.populateLoot(random, context)) {
      rolled.add(BukkitRefs.item(stack));
    }
    return rolled;
  }

  @Override
  public boolean mobLoot(WorldRef world) {
    World handle = world == null ? null : BukkitRefs.world(world);
    return handle != null && PaperCompat.mobDrops(handle);
  }

  @Override
  public boolean inActiveRaid(EntityRef entity) {
    if (!(handle(entity) instanceof Raider raider)) {
      return false;
    }
    Raid raid = raider.getRaid();
    return raid != null && raid.getStatus() == Raid.RaidStatus.ONGOING;
  }

  @Override
  public boolean tame(EntityRef entity, PlayerRef owner) {
    Player player = owner == null ? null : BukkitRefs.player(owner);
    if (!(handle(entity) instanceof Tameable tameable) || !(tameable instanceof LivingEntity living)
        || player == null || tameable.isTamed()) {
      return false;
    }
    EntityTameEvent event = new EntityTameEvent(living, player);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      return false;
    }
    tameable.setTamed(true);
    tameable.setOwner(player);
    return true;
  }

  @Override
  public boolean isSitting(EntityRef entity) {
    return handle(entity) instanceof Sittable sittable && sittable.isSitting();
  }

  @Override
  public void setSitting(EntityRef entity, boolean sitting) {
    if (handle(entity) instanceof Sittable sittable) {
      sittable.setSitting(sitting);
    }
  }

  @Override
  public void setAngry(EntityRef entity, boolean angry) {
    if (handle(entity) instanceof Wolf wolf) {
      wolf.setAngry(angry);
    }
  }

  @Override
  public DyeColor collarColor(EntityRef entity) {
    org.bukkit.DyeColor color = switch (handle(entity)) {
      case Wolf wolf -> wolf.getCollarColor();
      case Cat cat -> cat.getCollarColor();
      case null, default -> null;
    };
    return color == null ? null : DyeColor.valueOf(color.name());
  }

  @Override
  public void setShivering(EntityRef entity, boolean shivering) {
    if (handle(entity) instanceof Strider strider) {
      strider.setShivering(shivering);
    }
  }

  @Override
  public void shakeHead(EntityRef entity) {
    if (handle(entity) instanceof Villager villager) {
      villager.shakeHead();
    }
  }

  @Override
  public void setAggressive(EntityRef entity, boolean aggressive) {
    if (handle(entity) instanceof Mob mob) {
      PaperCompat.setAggressive(mob, aggressive);
    }
  }

  @Override
  public LivingRef angryAt(EntityRef warden) {
    if (!(handle(warden) instanceof Warden handle)) {
      return null;
    }
    LivingEntity target = handle.getEntityAngryAt();
    return target == null ? null : BukkitRefs.living(target);
  }

  @Override
  public void clearAnger(EntityRef warden, EntityRef target) {
    Entity angered = handle(target);
    if (handle(warden) instanceof Warden handle && angered != null) {
      handle.clearAnger(angered);
    }
  }

  private static Entity handle(EntityRef entity) {
    return entity == null ? null : BukkitRefs.entity(entity);
  }
}
