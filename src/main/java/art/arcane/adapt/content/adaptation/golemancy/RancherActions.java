package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractCow;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import java.util.ArrayList;
import java.util.EnumMap;
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

final class RancherActions implements Listener {
  static final int MAX_EXTRA_LOOT_ROLLS = 8;

  private static final double HALF_BLOCK = 0.5D;
  private static final int WOOL_ROLLS = 3;
  private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
  private static final List<Material> BUCKET_ONLY = List.of(Material.BUCKET);
  private static final List<Material> BOWL_FIRST = List.of(Material.BOWL, Material.BUCKET);

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

  List<Animals> herd(World world, NodeKey key, NodeRecord record) {
    int[] center = NodeArea.center(key);
    Location middle = new Location(world, center[0] + HALF_BLOCK, center[1] + HALF_BLOCK, center[2] + HALF_BLOCK);
    List<Animals> found = PaperCompat.nearbyEntitiesByType(Animals.class, middle, record.radiusX() + HALF_BLOCK,
        record.radiusY() + HALF_BLOCK, record.radiusZ() + HALF_BLOCK);
    List<EntityType> tended = core.species();
    found.removeIf(animal -> !animal.isValid() || animal.isDead() || !tends(animal, key, record, tended));
    return found;
  }

  boolean tends(Animals animal, NodeKey key, NodeRecord record) {
    return tends(animal, key, record, core.species());
  }

  RancherRules.Beast beast(Animals animal, Location from, UUID owner, boolean cullFacts) {
    Location at = animal.getLocation();
    boolean adult = animal.isAdult();
    boolean inLove = animal.isLoveMode();
    boolean shearable = animal instanceof Sheep sheep && adult && !sheep.isSheared();
    return new RancherRules.Beast(animal.getUniqueId(), animal.getType(), at.getX(), at.getY(), at.getZ(), at.distanceSquared(from), adult,
        adult && !inLove && animal.canBreed(), inLove, inLove && animal.getBreedCause() == null, shearable, milk(animal),
        cullFacts && guarded(animal), untouchable(owner, animal));
  }

  Stock stock(GolemContext golem) {
    return new Stock(golem);
  }

  boolean takeFood(GolemContext golem, Animals animal) {
    return take(golem, item -> animal.isBreedItem(item.getType()));
  }

  Optional<Material> takeContainer(GolemContext golem, RancherRules.Milk milk) {
    List<Material> order = switch (milk) {
      case NONE -> List.of();
      case BUCKET -> BUCKET_ONLY;
      case BUCKET_OR_BOWL -> BOWL_FIRST;
    };
    for (Material container : order) {
      if (take(golem, item -> item.getType() == container)) {
        return Optional.of(container);
      }
    }
    return Optional.empty();
  }

  void feed(GolemContext golem, Animals animal, boolean husbandry) {
    animal.setBreedCause(null);
    animal.setLoveModeTicks(RancherRules.LOVE_TICKS);
    GolemRancherCore.markFedTick(animal);
    core.markFed(animal.getUniqueId(), golem.nowMillis(), husbandry);
  }

  void shear(GolemContext golem, Sheep sheep) {
    sheep.setSheared(true);
    int amount = 1 + hooks.random().get().nextInt(WOOL_ROLLS);
    stow(golem, List.of(hooks.items().apply(wool(sheep.getColor()), amount)), sheep.getLocation());
  }

  void fill(GolemContext golem, Animals animal, Material container) {
    Material filled = container == Material.BOWL ? Material.MUSHROOM_STEW : Material.MILK_BUCKET;
    stow(golem, List.of(hooks.items().apply(filled, 1)), animal.getLocation());
  }

  Strike cull(GolemContext golem, Animals victim, int extraRolls) {
    double before = victim.getHealth();
    UUID id = victim.getUniqueId();
    Harvest harvest = new Harvest(extraRolls);
    culls.put(id, harvest);
    try {
      victim.damage(before + victim.getAbsorptionAmount(), golem.golem());
    } finally {
      culls.remove(id, harvest);
    }
    if (victim.isDead()) {
      stow(golem, harvest.collected(), victim.getLocation());
      return Strike.KILLED;
    }
    return victim.getHealth() < before ? Strike.HURT : Strike.REFUSED;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    Harvest harvest = culls.get(victim.getUniqueId());
    if (harvest == null) {
      return;
    }
    e.setDroppedExp(0);
    List<ItemStack> drops = e.getDrops();
    List<ItemStack> taken = new ArrayList<>(drops);
    if (harvest.extraRolls() > 0 && victim instanceof Lootable lootable && lootable.getLootTable() != null
        && hooks.mobLoot().test(victim.getWorld())) {
      LootTable table = lootable.getLootTable();
      for (int roll = 0; roll < harvest.extraRolls(); roll++) {
        taken.addAll(table.populateLoot(hooks.random().get(), new LootContext.Builder(victim.getLocation()).lootedEntity(victim).build()));
      }
    }
    drops.clear();
    harvest.collected().addAll(taken);
  }

  private boolean take(GolemContext golem, Predicate<ItemStack> matches) {
    return golem.take(matches, 1) > 0 || spend(GolemancySupport.homeChest(golem).orElse(null), matches);
  }

  private static boolean tends(Animals animal, NodeKey key, NodeRecord record, List<EntityType> tended) {
    if (!RancherRules.tends(record, tended, animal.getType())) {
      return false;
    }
    Location at = animal.getLocation();
    return NodeArea.covers(key, record, at.getX(), at.getY(), at.getZ());
  }

  private static RancherRules.Milk milk(Animals animal) {
    if (animal instanceof MushroomCow mooshroom) {
      return mooshroom.hasEffectsForNextStew() ? RancherRules.Milk.BUCKET : RancherRules.Milk.BUCKET_OR_BOWL;
    }
    return animal instanceof AbstractCow || animal instanceof Goat ? RancherRules.Milk.BUCKET : RancherRules.Milk.NONE;
  }

  private static boolean guarded(Animals animal) {
    return !animal.isAdult()
        || PaperCompat.customName(animal) != null
        || animal.isLeashed()
        || (animal instanceof Tameable tameable && tameable.isTamed())
        || !animal.getPassengers().isEmpty()
        || animal.isInsideVehicle()
        || equipped(animal);
  }

  private static boolean equipped(Animals animal) {
    EntityEquipment equipment = animal.getEquipment();
    if (equipment != null) {
      for (EquipmentSlot slot : SLOTS) {
        if (present(equipment.getItem(slot))) {
          return true;
        }
      }
    }
    return animal instanceof ChestedHorse horse && horse.isCarryingChest();
  }

  private static boolean untouchable(UUID owner, Animals animal) {
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner, animal);
    return candidate == null || candidate.protectedFriendly();
  }

  private static boolean present(ItemStack item) {
    return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
  }

  private static boolean spend(Inventory chest, Predicate<ItemStack> matches) {
    if (chest == null) {
      return false;
    }
    ItemStack[] contents = chest.getStorageContents();
    for (int slot = 0; slot < contents.length; slot++) {
      ItemStack listed = contents[slot];
      if (!present(listed) || !matches.test(listed)) {
        continue;
      }
      ItemStack held = chest.getItem(slot);
      if (!present(held) || !matches.test(held)) {
        continue;
      }
      if (held.getAmount() == 1) {
        chest.setItem(slot, null);
        return true;
      }
      ItemStack rest = held.clone();
      rest.setAmount(held.getAmount() - 1);
      chest.setItem(slot, rest);
      return true;
    }
    return false;
  }

  private static void stow(GolemContext golem, List<ItemStack> drops, Location at) {
    World world = at.getWorld();
    for (ItemStack drop : drops) {
      if (!present(drop)) {
        continue;
      }
      golem.carry(drop).ifPresent(left -> {
        if (world != null) {
          world.dropItemNaturally(at, left);
        }
      });
    }
  }

  private static Material wool(DyeColor color) {
    return switch (color == null ? DyeColor.WHITE : color) {
      case WHITE -> Material.WHITE_WOOL;
      case ORANGE -> Material.ORANGE_WOOL;
      case MAGENTA -> Material.MAGENTA_WOOL;
      case LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
      case YELLOW -> Material.YELLOW_WOOL;
      case LIME -> Material.LIME_WOOL;
      case PINK -> Material.PINK_WOOL;
      case GRAY -> Material.GRAY_WOOL;
      case LIGHT_GRAY -> Material.LIGHT_GRAY_WOOL;
      case CYAN -> Material.CYAN_WOOL;
      case PURPLE -> Material.PURPLE_WOOL;
      case BLUE -> Material.BLUE_WOOL;
      case BROWN -> Material.BROWN_WOOL;
      case GREEN -> Material.GREEN_WOOL;
      case RED -> Material.RED_WOOL;
      case BLACK -> Material.BLACK_WOOL;
    };
  }

  enum Strike {
    KILLED,
    HURT,
    REFUSED
  }

  record Hooks(BiFunction<Material, Integer, ItemStack> items, Supplier<Random> random, Predicate<World> mobLoot) {
    Hooks {
      Objects.requireNonNull(items);
      Objects.requireNonNull(random);
      Objects.requireNonNull(mobLoot);
    }

    static Hooks server() {
      return new Hooks(ItemStack::new, ThreadLocalRandom::current, PaperCompat::mobDrops);
    }
  }

  private record Harvest(int extraRolls, List<ItemStack> collected) {
    private Harvest(int extraRolls) {
      this(extraRolls, new ArrayList<>());
    }
  }

  static final class Stock implements RancherRules.Supplies {
    private final GolemContext golem;
    private final Map<EntityType, Animals> samples = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Integer> food = new EnumMap<>(EntityType.class);
    private List<ItemStack> held;
    private int buckets = -1;
    private int bowls = -1;

    private Stock(GolemContext golem) {
      this.golem = golem;
    }

    void sample(Animals animal) {
      samples.putIfAbsent(animal.getType(), animal);
    }

    @Override
    public int food(EntityType species) {
      Animals sample = samples.get(species);
      if (sample == null) {
        return 0;
      }
      return food.computeIfAbsent(species, ignored -> count(item -> sample.isBreedItem(item.getType())));
    }

    @Override
    public int buckets() {
      if (buckets < 0) {
        buckets = count(item -> item.getType() == Material.BUCKET);
      }
      return buckets;
    }

    @Override
    public int bowls() {
      if (bowls < 0) {
        bowls = count(item -> item.getType() == Material.BOWL);
      }
      return bowls;
    }

    private int count(Predicate<ItemStack> matches) {
      int total = 0;
      for (ItemStack item : held()) {
        if (matches.test(item)) {
          total += item.getAmount();
        }
      }
      return total;
    }

    private List<ItemStack> held() {
      if (held != null) {
        return held;
      }
      List<ItemStack> found = new ArrayList<>(golem.carriedItems());
      GolemancySupport.homeChest(golem).ifPresent(chest -> {
        for (ItemStack item : chest.getStorageContents()) {
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
