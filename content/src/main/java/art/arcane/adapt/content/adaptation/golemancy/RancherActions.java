package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.node.NodeArea;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.minion.MinionTargeting;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class RancherActions {
  static final int MAX_EXTRA_LOOT_ROLLS = 8;

  private static final double HALF_BLOCK = 0.5D;
  private static final int WOOL_ROLLS = 3;
  private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
  static final Id SHEEP = Id.minecraft("sheep");
  static final Id GOAT = Id.minecraft("goat");
  static final Id COW = Id.minecraft("cow");
  static final Id MOOSHROOM = Id.minecraft("mooshroom");
  static final Id BUCKET = Id.minecraft("bucket");
  static final Id BOWL = Id.minecraft("bowl");
  private static final Id MILK_BUCKET = Id.minecraft("milk_bucket");
  private static final Id MUSHROOM_STEW = Id.minecraft("mushroom_stew");
  private static final List<Id> BUCKET_ONLY = List.of(BUCKET);
  private static final List<Id> BOWL_FIRST = List.of(BOWL, BUCKET);

  private final GolemRancherCore core;
  private final Hooks hooks;
  private final Map<UUID, Harvest> culls = new ConcurrentHashMap<>();

  RancherActions(GolemRancherCore core, Hooks hooks) {
    this.core = Objects.requireNonNull(core);
    this.hooks = Objects.requireNonNull(hooks);
  }

  int intervalTicks() {
    return Math.max(1, core.config().actionIntervalTicks);
  }

  RancherRules.Levels levels() {
    GolemRancherCore.Config config = core.config();
    return new RancherRules.Levels(config.shearLevel, config.milkLevel, config.cullLevel);
  }

  static Optional<MobRef> animal(EntityRef entity) {
    return entity instanceof MobRef mob && mob.has(EntityTrait.ANIMAL) ? Optional.of(mob) : Optional.empty();
  }

  boolean surveyable(WorldRef world, NodeKey key, NodeRecord record) {
    Loc middle = middle(world, key, record);
    NodeCuboid area = record.area();
    int[] center = NodeArea.center(key);
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(middle, area.spanX() * HALF_BLOCK, area.spanZ() * HALF_BLOCK)) {
      return false;
    }
    for (int chunkX = (center[0] + area.minX()) >> 4; chunkX <= (center[0] + area.maxX()) >> 4; chunkX++) {
      for (int chunkZ = (center[2] + area.minZ()) >> 4; chunkZ <= (center[2] + area.maxZ()) >> 4; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return true;
  }

  List<MobRef> herd(WorldRef world, NodeKey key, NodeRecord record) {
    NodeCuboid area = record.area();
    Loc middle = middle(world, key, record);
    Box box = Box.around(middle.position(), area.spanX() * HALF_BLOCK, area.spanY() * HALF_BLOCK, area.spanZ() * HALF_BLOCK);
    List<Id> tended = core.species();
    List<MobRef> found = new ArrayList<>();
    for (EntityRef entity : world.entitiesIn(box, candidate -> candidate instanceof MobRef && candidate.has(EntityTrait.ANIMAL))) {
      MobRef animal = (MobRef) entity;
      if (animal.isValid() && !animal.isDead() && tends(animal, key, record, tended)) {
        found.add(animal);
      }
    }
    return found;
  }

  boolean tends(MobRef animal, NodeKey key, NodeRecord record) {
    return tends(animal, key, record, core.species());
  }

  RancherRules.Beast beast(MobRef animal, Loc from, UUID owner, boolean cullFacts) {
    AnimalOps animals = animals();
    Loc at = animal.location();
    boolean adult = animals.isAdult(animal);
    boolean inLove = animals.isInLove(animal);
    boolean shearable = animal.type().equals(SHEEP) && adult && !animals.isSheared(animal);
    return new RancherRules.Beast(animal.uuid(), animal.type(), at.x(), at.y(), at.z(), at.distanceSquared(from), adult,
        adult && !inLove && animals.canBreed(animal), inLove, inLove && animals.loveCause(animal) == null, shearable, milk(animal),
        cullFacts && guarded(animal), untouchable(owner, animal));
  }

  Stock stock(GolemContext golem) {
    return new Stock(golem);
  }

  boolean takeFood(GolemContext golem, MobRef animal) {
    AnimalOps animals = animals();
    return take(golem, item -> animals.isBreedItem(animal, item.type()));
  }

  Optional<Id> takeContainer(GolemContext golem, RancherRules.Milk milk) {
    List<Id> order = switch (milk) {
      case NONE -> List.of();
      case BUCKET -> BUCKET_ONLY;
      case BUCKET_OR_BOWL -> BOWL_FIRST;
    };
    for (Id container : order) {
      if (take(golem, item -> item.type().equals(container))) {
        return Optional.of(container);
      }
    }
    return Optional.empty();
  }

  void feed(GolemContext golem, MobRef animal, boolean husbandry) {
    animals().setInLove(animal, RancherRules.LOVE_TICKS);
    GolemRancherCore.markFedTick(animal);
    core.markFed(animal.uuid(), golem.nowMillis(), husbandry);
  }

  void shear(GolemContext golem, MobRef sheep) {
    AnimalOps animals = animals();
    animals.setSheared(sheep, true);
    int amount = 1 + hooks.random().get().nextInt(WOOL_ROLLS);
    stow(golem, List.of(hooks.items().apply(wool(animals.woolColor(sheep)), amount)), sheep.location());
  }

  void fill(GolemContext golem, MobRef animal, Id container) {
    Id filled = container.equals(BOWL) ? MUSHROOM_STEW : MILK_BUCKET;
    stow(golem, List.of(hooks.items().apply(filled, 1)), animal.location());
  }

  Strike cull(GolemContext golem, MobRef victim, int extraRolls) {
    double before = victim.health();
    UUID id = victim.uuid();
    Harvest harvest = new Harvest(extraRolls);
    culls.put(id, harvest);
    try {
      victim.damage(before + victim.absorption(), golem.golem());
    } finally {
      culls.remove(id, harvest);
    }
    if (victim.isDead()) {
      stow(golem, harvest.collected(), victim.location());
      return Strike.KILLED;
    }
    return victim.health() < before ? Strike.HURT : Strike.REFUSED;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    Harvest harvest = culls.get(victim.uuid());
    if (harvest == null) {
      return;
    }
    e.setDroppedExp(0);
    List<ItemRef> drops = e.drops();
    List<ItemRef> taken = new ArrayList<>(drops);
    if (harvest.extraRolls() > 0 && hooks.mobLoot().test(victim.world())) {
      for (int roll = 0; roll < harvest.extraRolls(); roll++) {
        taken.addAll(hooks.loot().apply(victim, hooks.random().get()));
      }
    }
    drops.clear();
    harvest.collected().addAll(taken);
  }

  private boolean take(GolemContext golem, Predicate<ItemRef> matches) {
    if (golem.take(matches, 1) > 0) {
      return true;
    }
    if (!spend(GolemancySupport.homeChest(golem).orElse(null), matches)) {
      return false;
    }
    GolemancySupport.visitHomeChest(golem);
    return true;
  }

  private static boolean tends(MobRef animal, NodeKey key, NodeRecord record, List<Id> tended) {
    if (!RancherRules.tends(record, tended, animal.type())) {
      return false;
    }
    Loc at = animal.location();
    return NodeArea.covers(key, record, at.x(), at.y(), at.z());
  }

  private static Loc middle(WorldRef world, NodeKey key, NodeRecord record) {
    int[] center = NodeArea.center(key);
    NodeCuboid area = record.area();
    return new Loc(world, center[0] + (area.minX() + area.maxX() + 1) * HALF_BLOCK,
        center[1] + (area.minY() + area.maxY() + 1) * HALF_BLOCK, center[2] + (area.minZ() + area.maxZ() + 1) * HALF_BLOCK, 0F, 0F);
  }

  private static RancherRules.Milk milk(MobRef animal) {
    Id type = animal.type();
    if (type.equals(MOOSHROOM)) {
      return animals().hasStewEffect(animal) ? RancherRules.Milk.BUCKET : RancherRules.Milk.BUCKET_OR_BOWL;
    }
    return type.equals(COW) || type.equals(GOAT) ? RancherRules.Milk.BUCKET : RancherRules.Milk.NONE;
  }

  private static boolean guarded(MobRef animal) {
    AnimalOps animals = animals();
    return !animals.isAdult(animal)
        || animal.customName() != null
        || animals.isLeashed(animal)
        || animals.isTamed(animal)
        || !animal.passengers().isEmpty()
        || animal.vehicle() != null
        || equipped(animal, animals);
  }

  private static boolean equipped(MobRef animal, AnimalOps animals) {
    for (EquipmentSlot slot : SLOTS) {
      if (present(animal.equipment(slot))) {
        return true;
      }
    }
    return animals.carriesChest(animal);
  }

  private static boolean untouchable(UUID owner, MobRef animal) {
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner, animal);
    return candidate == null || candidate.protectedFriendly();
  }

  private static boolean present(ItemRef item) {
    return item != null && !item.isEmpty() && item.amount() > 0;
  }

  private static boolean spend(InventoryRef chest, Predicate<ItemRef> matches) {
    if (chest == null) {
      return false;
    }
    List<ItemRef> contents = chest.contents();
    for (int slot = 0; slot < contents.size(); slot++) {
      ItemRef listed = contents.get(slot);
      if (!present(listed) || !matches.test(listed)) {
        continue;
      }
      ItemRef held = chest.item(slot);
      if (!present(held) || !matches.test(held)) {
        continue;
      }
      if (held.amount() == 1) {
        chest.setItem(slot, AdaptApi.platform().host().items().empty());
        return true;
      }
      ItemRef rest = held.copy();
      rest.setAmount(held.amount() - 1);
      chest.setItem(slot, rest);
      return true;
    }
    return false;
  }

  private static void stow(GolemContext golem, List<ItemRef> drops, Loc at) {
    WorldRef world = at.world();
    Vec3 position = at.position();
    for (ItemRef drop : drops) {
      if (!present(drop)) {
        continue;
      }
      golem.carry(drop).ifPresent(left -> {
        if (world != null) {
          world.dropItemNaturally(position, left);
        }
      });
    }
  }

  private static AnimalOps animals() {
    return AdaptApi.platform().host().animals();
  }

  private static Id wool(DyeColor color) {
    return switch (color == null ? DyeColor.WHITE : color) {
      case WHITE -> Id.minecraft("white_wool");
      case ORANGE -> Id.minecraft("orange_wool");
      case MAGENTA -> Id.minecraft("magenta_wool");
      case LIGHT_BLUE -> Id.minecraft("light_blue_wool");
      case YELLOW -> Id.minecraft("yellow_wool");
      case LIME -> Id.minecraft("lime_wool");
      case PINK -> Id.minecraft("pink_wool");
      case GRAY -> Id.minecraft("gray_wool");
      case LIGHT_GRAY -> Id.minecraft("light_gray_wool");
      case CYAN -> Id.minecraft("cyan_wool");
      case PURPLE -> Id.minecraft("purple_wool");
      case BLUE -> Id.minecraft("blue_wool");
      case BROWN -> Id.minecraft("brown_wool");
      case GREEN -> Id.minecraft("green_wool");
      case RED -> Id.minecraft("red_wool");
      case BLACK -> Id.minecraft("black_wool");
    };
  }

  enum Strike {
    KILLED,
    HURT,
    REFUSED
  }

  record Hooks(BiFunction<Id, Integer, ItemRef> items, Supplier<Random> random, Predicate<WorldRef> mobLoot,
               BiFunction<LivingRef, Random, List<ItemRef>> loot) {
    Hooks {
      Objects.requireNonNull(items);
      Objects.requireNonNull(random);
      Objects.requireNonNull(mobLoot);
      Objects.requireNonNull(loot);
    }

    static Hooks server() {
      return new Hooks((type, amount) -> AdaptApi.platform().host().items().create(type, amount), ThreadLocalRandom::current,
          world -> animals().mobLoot(world), (victim, random) -> animals().rollLoot(victim, random));
    }
  }

  private record Harvest(int extraRolls, List<ItemRef> collected) {
    private Harvest(int extraRolls) {
      this(extraRolls, new ArrayList<>());
    }
  }

  static final class Stock implements RancherRules.Supplies {
    private final GolemContext golem;
    private final Map<Id, MobRef> samples = new HashMap<>();
    private final Map<Id, Integer> food = new HashMap<>();
    private List<ItemRef> held;
    private int buckets = -1;
    private int bowls = -1;

    private Stock(GolemContext golem) {
      this.golem = golem;
    }

    void sample(MobRef animal) {
      samples.putIfAbsent(animal.type(), animal);
    }

    @Override
    public int food(Id species) {
      MobRef sample = samples.get(species);
      if (sample == null) {
        return 0;
      }
      AnimalOps animals = animals();
      return food.computeIfAbsent(species, ignored -> count(item -> animals.isBreedItem(sample, item.type())));
    }

    @Override
    public int buckets() {
      if (buckets < 0) {
        buckets = count(item -> item.type().equals(BUCKET));
      }
      return buckets;
    }

    @Override
    public int bowls() {
      if (bowls < 0) {
        bowls = count(item -> item.type().equals(BOWL));
      }
      return bowls;
    }

    private int count(Predicate<ItemRef> matches) {
      int total = 0;
      for (ItemRef item : held()) {
        if (matches.test(item)) {
          total += item.amount();
        }
      }
      return total;
    }

    private List<ItemRef> held() {
      if (held != null) {
        return held;
      }
      List<ItemRef> found = new ArrayList<>(golem.carriedItems());
      GolemancySupport.homeChest(golem).ifPresent(chest -> {
        for (ItemRef item : chest.contents()) {
          if (present(item)) {
            found.add(item);
          }
        }
      });
      found.removeIf(item -> !present(item));
      held = found;
      return held;
    }
  }
}
