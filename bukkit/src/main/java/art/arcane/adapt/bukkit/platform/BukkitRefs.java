package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.entity.BukkitEntityRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitExperienceOrbRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitInventoryRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitItemEntityRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitLivingRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitMobRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitPlayerInventoryRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitPlayerRef;
import art.arcane.adapt.bukkit.platform.entity.BukkitProjectileRef;
import art.arcane.adapt.bukkit.platform.item.BukkitItemRef;
import art.arcane.adapt.bukkit.platform.world.BukkitBlockRef;
import art.arcane.adapt.bukkit.platform.world.BukkitBlockStateRef;
import art.arcane.adapt.bukkit.platform.world.BukkitWorldRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps Bukkit objects into SPI handles and unwraps them again. Player handles are cached per UUID
 * for the session and dropped after quit; world handles are cached per world UID; other entity
 * handles are cached per native entity (identity, soft values) and dropped when the entity leaves
 * its world, so hot paths reuse one wrapper per entity. Block,
 * item and location wrappers are plain value wrappers. Wrapping null returns null, except items,
 * which wrap to an empty ref.
 */
public final class BukkitRefs {
  private static final Map<UUID, BukkitPlayerRef> PLAYERS = new ConcurrentHashMap<>();
  private static final Map<UUID, BukkitWorldRef> WORLDS = new ConcurrentHashMap<>();
  private static final Cache<Entity, EntityRef> ENTITIES = Caffeine.newBuilder().weakKeys().softValues().build();

  private BukkitRefs() {
  }

  public static PlayerRef player(Player player) {
    if (player == null) {
      return null;
    }
    UUID id = player.getUniqueId();
    BukkitPlayerRef ref = PLAYERS.get(id);
    if (ref == null) {
      BukkitPlayerRef created = new BukkitPlayerRef(player);
      ref = PLAYERS.putIfAbsent(id, created);
      if (ref == null) {
        return created;
      }
    }
    if (ref.handle() != player) {
      ref.rebind(player);
    }
    return ref;
  }

  public static EntityRef entity(Entity entity) {
    if (entity == null) {
      return null;
    }
    if (entity instanceof Player player) {
      return player(player);
    }
    EntityRef cached = ENTITIES.getIfPresent(entity);
    if (cached != null) {
      return cached;
    }
    EntityRef created = create(entity);
    EntityRef raced = ENTITIES.asMap().putIfAbsent(entity, created);
    return raced == null ? created : raced;
  }

  public static LivingRef living(LivingEntity entity) {
    return (LivingRef) entity(entity);
  }

  public static MobRef mob(Mob mob) {
    return (MobRef) entity(mob);
  }

  public static ProjectileRef projectile(Projectile projectile) {
    return (ProjectileRef) entity(projectile);
  }

  public static ItemEntityRef itemEntity(Item item) {
    return (ItemEntityRef) entity(item);
  }

  public static WorldRef world(World world) {
    if (world == null) {
      return null;
    }
    UUID id = world.getUID();
    BukkitWorldRef ref = WORLDS.get(id);
    if (ref == null || ref.world() != world) {
      ref = new BukkitWorldRef(world);
      WORLDS.put(id, ref);
    }
    return ref;
  }

  public static BlockRef block(Block block) {
    return block == null ? null : new BukkitBlockRef(block);
  }

  public static BlockStateRef blockState(BlockData data) {
    return data == null ? null : new BukkitBlockStateRef(data.clone());
  }

  /**
   * Wraps the stack itself, not a copy. Edits reach the game only where Bukkit handed out the live stack (slot
   * mirrors do, some event getters return copies), so callers store changed items back with a slot or event setter.
   */
  public static ItemRef item(ItemStack stack) {
    return new BukkitItemRef(stack == null ? new ItemStack(Material.AIR) : stack);
  }

  public static ItemRef emptyItem() {
    return new BukkitItemRef(new ItemStack(Material.AIR));
  }

  public static InventoryRef inventory(Inventory inventory) {
    if (inventory == null) {
      return null;
    }
    return inventory instanceof PlayerInventory player ? new BukkitPlayerInventoryRef(player) : new BukkitInventoryRef(inventory);
  }

  public static Loc loc(Location location) {
    if (location == null || location.getWorld() == null) {
      return null;
    }
    return new Loc(world(location.getWorld()), location.getX(), location.getY(), location.getZ(),
        location.getYaw(), location.getPitch());
  }

  public static Id id(NamespacedKey key) {
    return BukkitConversions.id(key);
  }

  public static Id id(Material material) {
    return BukkitResolve.id(material);
  }

  public static Id id(EntityType type) {
    return BukkitResolve.id(type);
  }

  public static Id id(Keyed keyed) {
    return BukkitResolve.id(keyed);
  }

  public static Player player(PlayerRef player) {
    return player == null ? null : (Player) player.unwrap();
  }

  public static Entity entity(EntityRef entity) {
    return entity == null ? null : (Entity) entity.unwrap();
  }

  public static LivingEntity living(LivingRef entity) {
    return entity == null ? null : (LivingEntity) entity.unwrap();
  }

  public static Mob mob(MobRef mob) {
    return mob == null ? null : (Mob) mob.unwrap();
  }

  /** Native world, or null when the handle is stale (world unloaded). */
  public static World world(WorldRef world) {
    if (world == null) {
      return null;
    }
    if (world instanceof BukkitWorldRef ref) {
      return ref.isLoaded() ? ref.world() : null;
    }
    return Bukkit.getWorld(world.uid());
  }

  public static Block block(BlockRef block) {
    if (block == null) {
      return null;
    }
    if (block instanceof BukkitBlockRef ref) {
      return ref.block();
    }
    World world = world(block.world());
    return world == null ? null : world.getBlockAt(block.x(), block.y(), block.z());
  }

  public static BlockData blockData(BlockStateRef state) {
    if (state == null) {
      return null;
    }
    if (state instanceof BukkitBlockStateRef ref) {
      return ref.data();
    }
    return Bukkit.createBlockData(state.asString());
  }

  /** Native stack behind the ref (not a copy); an AIR stack for null. */
  public static ItemStack item(ItemRef item) {
    if (item == null) {
      return new ItemStack(Material.AIR);
    }
    return (ItemStack) item.unwrap();
  }

  public static Inventory inventory(InventoryRef inventory) {
    return inventory instanceof BukkitInventoryRef ref ? ref.inventory() : null;
  }

  /** Native location, or null when the world is unloaded. */
  public static Location location(Loc loc) {
    if (loc == null) {
      return null;
    }
    World world = world(loc.world());
    return world == null ? null : new Location(world, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
  }

  public static NamespacedKey key(Id id) {
    return BukkitConversions.key(id);
  }

  public static Material material(Id id) {
    return BukkitResolve.material(id);
  }

  public static EntityType entityType(Id id) {
    return BukkitResolve.entityType(id);
  }

  public static Sound sound(Id id) {
    return BukkitResolve.sound(id);
  }

  public static Attribute attribute(Id id) {
    return BukkitResolve.attribute(id);
  }

  public static PotionEffectType effect(Id id) {
    return BukkitResolve.effect(id);
  }

  public static Enchantment enchantment(Id id) {
    return BukkitResolve.enchantment(id);
  }

  public static Biome biome(Id id) {
    return BukkitResolve.biome(id);
  }

  /** Cached player handle, or null when the player is not tracked. Any thread. */
  public static PlayerRef cachedPlayer(UUID id) {
    return id == null ? null : PLAYERS.get(id);
  }

  /** Online player handles from the server's player list, rebinding the cache. */
  public static Collection<PlayerRef> players(Collection<? extends Player> online) {
    if (online.isEmpty()) {
      return List.of();
    }
    PlayerRef[] refs = new PlayerRef[online.size()];
    int index = 0;
    for (Player player : online) {
      refs[index++] = player(player);
    }
    return List.of(refs);
  }

  /** Drops the cached handle once its player is gone. Called a tick after quit. */
  static void forget(UUID id) {
    BukkitPlayerRef ref = PLAYERS.get(id);
    if (ref != null && !ref.isOnline()) {
      PLAYERS.remove(id, ref);
    }
  }

  /** Drops the cached handle of an entity that left the world. */
  static void forget(Entity entity) {
    ENTITIES.invalidate(entity);
  }

  static void clear() {
    PLAYERS.clear();
    WORLDS.clear();
    ENTITIES.invalidateAll();
  }

  private static EntityRef create(Entity entity) {
    if (entity instanceof Display display) {
      BukkitDisplayFactory factory = BukkitHosts.displays();
      return factory == null ? new BukkitEntityRef(display) : factory.wrap(display);
    }
    if (entity instanceof Mob mob) {
      return new BukkitMobRef(mob);
    }
    if (entity instanceof LivingEntity living) {
      return new BukkitLivingRef(living);
    }
    if (entity instanceof Projectile projectile) {
      return new BukkitProjectileRef(projectile);
    }
    if (entity instanceof Item item) {
      return new BukkitItemEntityRef(item);
    }
    if (entity instanceof ExperienceOrb orb) {
      return new BukkitExperienceOrbRef(orb);
    }
    return new BukkitEntityRef(entity);
  }
}
