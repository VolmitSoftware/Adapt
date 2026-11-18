package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Animal state kept per entity id. Peaceful animal types and breed items are registered by tests. */
public final class FakeAnimals implements AnimalOps {
  private final Set<Id> peacefulTypes = ConcurrentHashMap.newKeySet();
  private final Map<Id, Set<Id>> breedItems = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> ages = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> love = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> loveCauses = new ConcurrentHashMap<>();
  private final Set<UUID> sheared = ConcurrentHashMap.newKeySet();
  private final Map<UUID, DyeColor> wool = new ConcurrentHashMap<>();
  private final Map<UUID, List<ItemRef>> loot = new ConcurrentHashMap<>();
  private final Set<UUID> raiders = ConcurrentHashMap.newKeySet();
  private volatile boolean mobLoot = true;
  private final Set<UUID> sitting = ConcurrentHashMap.newKeySet();
  private final Set<UUID> angry = ConcurrentHashMap.newKeySet();
  private final Set<UUID> shivering = ConcurrentHashMap.newKeySet();
  private final List<UUID> headShakes = new CopyOnWriteArrayList<>();
  private final Set<UUID> aggressiveMobs = ConcurrentHashMap.newKeySet();
  private final Map<UUID, LivingRef> anger = new ConcurrentHashMap<>();
  private final Map<UUID, DyeColor> collars = new ConcurrentHashMap<>();

  public FakeAnimals peaceful(Id type, Id... breedWith) {
    peacefulTypes.add(type);
    breedItems.put(type, Set.of(breedWith));
    return this;
  }

  public void setWool(EntityRef entity, DyeColor color) {
    wool.put(entity.uuid(), color);
  }

  public void setLoot(EntityRef entity, List<ItemRef> drops) {
    loot.put(entity.uuid(), List.copyOf(drops));
  }

  public void setLoveCause(EntityRef entity, UUID cause) {
    loveCauses.put(entity.uuid(), cause);
  }

  public void setRaider(EntityRef entity, boolean raiding) {
    if (raiding) {
      raiders.add(entity.uuid());
    } else {
      raiders.remove(entity.uuid());
    }
  }

  public void setMobLoot(boolean enabled) {
    mobLoot = enabled;
  }

  public int loveTicks(EntityRef entity) {
    return love.getOrDefault(entity.uuid(), 0);
  }

  @Override
  public boolean isPeacefulAnimalType(Id entityType) {
    return peacefulTypes.contains(entityType);
  }

  @Override
  public boolean isAdult(EntityRef entity) {
    return age(entity) >= 0;
  }

  @Override
  public int age(EntityRef entity) {
    return ages.getOrDefault(entity.uuid(), 0);
  }

  @Override
  public void setAge(EntityRef entity, int age) {
    ages.put(entity.uuid(), age);
  }

  @Override
  public boolean canBreed(EntityRef entity) {
    return age(entity) == 0;
  }

  @Override
  public boolean isInLove(EntityRef entity) {
    return loveTicks(entity) > 0;
  }

  @Override
  public UUID loveCause(EntityRef entity) {
    return loveCauses.get(entity.uuid());
  }

  @Override
  public void setInLove(EntityRef entity, int ticks) {
    loveCauses.remove(entity.uuid());
    love.put(entity.uuid(), ticks);
  }

  @Override
  public boolean isBreedItem(EntityRef entity, Id item) {
    return breedItems.getOrDefault(entity.type(), Set.of()).contains(item);
  }

  @Override
  public boolean isSheared(EntityRef entity) {
    return sheared.contains(entity.uuid());
  }

  @Override
  public void setSheared(EntityRef entity, boolean value) {
    if (value) {
      sheared.add(entity.uuid());
    } else {
      sheared.remove(entity.uuid());
    }
  }

  @Override
  public DyeColor woolColor(EntityRef entity) {
    return entity.type().equals(Id.minecraft("sheep")) ? wool.getOrDefault(entity.uuid(), DyeColor.WHITE) : null;
  }

  @Override
  public boolean hasStewEffect(EntityRef entity) {
    return false;
  }

  @Override
  public boolean isLeashed(EntityRef entity) {
    return false;
  }

  @Override
  public boolean isTamed(EntityRef entity) {
    return entity != null && entity.tameOwner() != null;
  }

  @Override
  public boolean carriesChest(EntityRef entity) {
    return false;
  }

  @Override
  public List<ItemRef> rollLoot(EntityRef entity, Random random) {
    return loot.getOrDefault(entity.uuid(), List.of());
  }

  @Override
  public List<ItemRef> rollLoot(EntityRef entity, PlayerRef killer, float luck, Random random) {
    return loot.getOrDefault(entity.uuid(), List.of());
  }

  @Override
  public boolean mobLoot(WorldRef world) {
    return mobLoot;
  }

  @Override
  public boolean inActiveRaid(EntityRef entity) {
    return raiders.contains(entity.uuid());
  }

  public void setCollar(EntityRef entity, DyeColor color) {
    collars.put(entity.uuid(), color);
  }

  public boolean isAngry(EntityRef entity) {
    return angry.contains(entity.uuid());
  }

  public boolean isShivering(EntityRef entity) {
    return shivering.contains(entity.uuid());
  }

  @Override
  public boolean tame(EntityRef entity, PlayerRef owner) {
    if (!(entity instanceof FakeEntity fake) || owner == null || fake.tameOwner() != null) {
      return false;
    }
    fake.setTameOwner(owner.uuid());
    return true;
  }

  @Override
  public boolean isSitting(EntityRef entity) {
    return sitting.contains(entity.uuid());
  }

  @Override
  public void setSitting(EntityRef entity, boolean sit) {
    if (sit) {
      sitting.add(entity.uuid());
    } else {
      sitting.remove(entity.uuid());
    }
  }

  @Override
  public void setAngry(EntityRef entity, boolean anger) {
    if (anger) {
      angry.add(entity.uuid());
    } else {
      angry.remove(entity.uuid());
    }
  }

  @Override
  public DyeColor collarColor(EntityRef entity) {
    return collars.get(entity.uuid());
  }

  @Override
  public void setShivering(EntityRef entity, boolean shiver) {
    if (shiver) {
      shivering.add(entity.uuid());
    } else {
      shivering.remove(entity.uuid());
    }
  }

  @Override
  public void shakeHead(EntityRef entity) {
    headShakes.add(entity.uuid());
  }

  @Override
  public void setAggressive(EntityRef entity, boolean aggressive) {
    if (aggressive) {
      aggressiveMobs.add(entity.uuid());
    } else {
      aggressiveMobs.remove(entity.uuid());
    }
  }

  public boolean isAggressive(EntityRef entity) {
    return aggressiveMobs.contains(entity.uuid());
  }

  @Override
  public LivingRef angryAt(EntityRef warden) {
    return anger.get(warden.uuid());
  }

  @Override
  public void clearAnger(EntityRef warden, EntityRef target) {
    LivingRef current = anger.get(warden.uuid());
    if (current != null && current.uuid().equals(target.uuid())) {
      anger.remove(warden.uuid());
    }
  }

  /** Makes a warden angry at {@code target}. */
  public void anger(EntityRef warden, LivingRef target) {
    anger.put(warden.uuid(), target);
  }

  /** Entities that shook their head, in call order. */
  public List<UUID> headShakes() {
    return headShakes;
  }
}
