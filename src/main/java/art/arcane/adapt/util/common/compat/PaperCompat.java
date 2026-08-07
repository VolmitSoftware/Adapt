/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.util.common.compat;

import com.destroystokyo.paper.entity.Pathfinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Capability probes + Spigot fallbacks for Paper-only API. Probes flip once on
 * the first NoSuchMethodError and stay on the fallback path afterward.
 */
public final class PaperCompat {
  // Mirrors the vanilla #minecraft:replaceable block tag; Spigot exposes no probe for it.
  private static final Set<Material> REPLACEABLE_FALLBACK = Set.of(
      Material.AIR,
      Material.CAVE_AIR,
      Material.VOID_AIR,
      Material.STRUCTURE_VOID,
      Material.LIGHT,
      Material.WATER,
      Material.LAVA,
      Material.BUBBLE_COLUMN,
      Material.FIRE,
      Material.SOUL_FIRE,
      Material.SNOW,
      Material.VINE,
      Material.GLOW_LICHEN,
      Material.HANGING_ROOTS,
      Material.SHORT_GRASS,
      Material.TALL_GRASS,
      Material.FERN,
      Material.LARGE_FERN,
      Material.DEAD_BUSH,
      Material.BUSH,
      Material.SHORT_DRY_GRASS,
      Material.TALL_DRY_GRASS,
      Material.SEAGRASS,
      Material.TALL_SEAGRASS,
      Material.WARPED_ROOTS,
      Material.CRIMSON_ROOTS,
      Material.NETHER_SPROUTS
  );
  private static final Map<String, Boolean> CLASS_PRESENCE = new ConcurrentHashMap<>();
  private static final String PATHFINDER_CLASS = "com.destroystokyo.paper.entity.Pathfinder";
  private static volatile boolean pathfindingSupported = true;
  private static volatile boolean blockReplaceableSupported = true;
  private static volatile boolean teleportAsyncSupported = true;
  private static volatile boolean teleportAsyncCauseSupported = true;
  private static volatile boolean transientModifierSupported = true;
  private static volatile boolean damageCriticalSupported = true;
  // EntityPotionEffectEvent.getSource() is 26.2+; resolved reflectively so the 26.1.2 compile baseline builds.
  private static final MethodHandle POTION_SOURCE_HANDLE = resolvePotionSourceHandle();
  private static volatile boolean trackedPlayersSupported = true;

  private PaperCompat() {
  }

  public static boolean hasClass(String name) {
    return CLASS_PRESENCE.computeIfAbsent(name, key -> {
      try {
        Class.forName(key);
        return true;
      } catch (ClassNotFoundException | NoClassDefFoundError absent) {
        return false;
      }
    });
  }

  public static CompletableFuture<Boolean> teleportAsync(Entity entity, Location destination) {
    if (teleportAsyncSupported) {
      try {
        return entity.teleportAsync(destination);
      } catch (NoSuchMethodError absent) {
        teleportAsyncSupported = false;
      }
    }

    return CompletableFuture.completedFuture(entity.teleport(destination));
  }

  public static CompletableFuture<Boolean> teleportAsync(Entity entity, Location destination, PlayerTeleportEvent.TeleportCause cause) {
    if (teleportAsyncCauseSupported) {
      try {
        return entity.teleportAsync(destination, cause);
      } catch (NoSuchMethodError absent) {
        teleportAsyncCauseSupported = false;
      }
    }

    return CompletableFuture.completedFuture(entity.teleport(destination, cause));
  }

  public static void addTransientModifier(AttributeInstance instance, AttributeModifier modifier) {
    if (transientModifierSupported) {
      try {
        instance.addTransientModifier(modifier);
        return;
      } catch (NoSuchMethodError absent) {
        transientModifierSupported = false;
      }
    }

    // Persistent fallback; Adapt namespaces are swept on join/death/quit.
    instance.addModifier(modifier);
  }

  public static List<LivingEntity> nearbyLivingEntities(Location center, double radius) {
    return nearbyLivingEntities(center, radius, radius, radius);
  }

  public static List<LivingEntity> nearbyLivingEntities(Location center, double x, double y, double z) {
    return nearbyEntitiesByType(LivingEntity.class, center, x, y, z);
  }

  public static List<LivingEntity> nearbyLivingEntities(Location center, double x, double y, double z, Predicate<? super LivingEntity> filter) {
    List<LivingEntity> found = nearbyEntitiesByType(LivingEntity.class, center, x, y, z);
    found.removeIf(entity -> !filter.test(entity));
    return found;
  }

  public static List<Player> nearbyPlayers(Location center, double radius) {
    return nearbyEntitiesByType(Player.class, center, radius, radius, radius);
  }

  public static <T extends Entity> List<T> nearbyEntitiesByType(Class<T> type, Location center, double radius) {
    return nearbyEntitiesByType(type, center, radius, radius, radius);
  }

  public static <T extends Entity> List<T> nearbyEntitiesByType(Class<T> type, Location center, double x, double y, double z) {
    World world = center.getWorld();
    if (world == null) {
      return new ArrayList<>();
    }

    List<T> found = new ArrayList<>();
    for (Entity entity : world.getNearbyEntities(BoundingBox.of(center, x, y, z), type::isInstance)) {
      found.add(type.cast(entity));
    }

    return found;
  }

  public static boolean hasChangedBlock(Location from, Location to) {
    if (to == null) {
      return false;
    }

    return from.getBlockX() != to.getBlockX()
        || from.getBlockY() != to.getBlockY()
        || from.getBlockZ() != to.getBlockZ()
        || from.getWorld() != to.getWorld();
  }

  /** True when a normal block placement would overwrite this block (air, fluids, vegetation). */
  public static boolean isReplaceable(Block block) {
    if (block == null) {
      return false;
    }

    if (blockReplaceableSupported) {
      try {
        return block.isReplaceable();
      } catch (NoSuchMethodError absent) {
        blockReplaceableSupported = false;
      }
    }

    return REPLACEABLE_FALLBACK.contains(block.getType());
  }

  public static boolean isCritical(EntityDamageByEntityEvent event) {
    if (damageCriticalSupported) {
      try {
        return event.isCritical();
      } catch (NoSuchMethodError absent) {
        damageCriticalSupported = false;
      }
    }

    // Vanilla crit approximation: falling attacker, airborne, unimpaired.
    return event.getDamager() instanceof Player player
        && player.getFallDistance() > 0.0F
        && !player.isOnGround()
        && !player.isInsideVehicle()
        && !player.isSwimming()
        && !player.isSprinting()
        && player.getPotionEffect(PotionEffectType.BLINDNESS) == null;
  }

  public static LivingEntity livingEntity(EntityPotionEffectEvent event) {
    // EntityEvent receiver keeps the Spigot getEntity()Entity descriptor.
    EntityEvent base = event;
    return base.getEntity() instanceof LivingEntity living ? living : null;
  }

  public static Entity potionSource(EntityPotionEffectEvent event) {
    if (POTION_SOURCE_HANDLE == null) {
      return null;
    }

    try {
      return (Entity) POTION_SOURCE_HANDLE.invokeExact(event);
    } catch (Throwable absent) {
      return null;
    }
  }

  private static MethodHandle resolvePotionSourceHandle() {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(EntityPotionEffectEvent.class, "getSource", MethodType.methodType(Entity.class));
    } catch (NoSuchMethodException | IllegalAccessException absent) {
      return null;
    }
  }

  public static UUID tamedOwnerId(Tameable tameable) {
    AnimalTamer owner = tameable.getOwner();
    return owner == null ? null : owner.getUniqueId();
  }

  public static boolean hasLineOfSight(LivingEntity entity, Location target) {
    World world = entity.getWorld();
    if (target.getWorld() != world) {
      return false;
    }

    Location eye = entity.getEyeLocation();
    Vector direction = target.toVector().subtract(eye.toVector());
    double distance = direction.length();
    if (distance < 1.0e-6) {
      return true;
    }

    return world.rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true) == null;
  }

  public static Set<Player> trackedPlayers(Entity entity) {
    if (trackedPlayersSupported) {
      try {
        return entity.getTrackedPlayers();
      } catch (NoSuchMethodError absent) {
        trackedPlayersSupported = false;
      }
    }

    // Over-approximates tracking range; canSee still filters hidden viewers.
    Set<Player> viewers = new HashSet<>();
    Location center = entity.getLocation();
    for (Player player : entity.getWorld().getPlayers()) {
      if (player.getLocation().distanceSquared(center) <= 128.0D * 128.0D && player.canSee(entity)) {
        viewers.add(player);
      }
    }

    return viewers;
  }

  public static void openGrindstone(Player player) {
    try {
      player.openGrindstone(null, true);
    } catch (NoSuchMethodError absent) {
      openMenu(player, MenuType.GRINDSTONE, "Grindstone");
    }
  }

  public static void openAnvil(Player player) {
    try {
      player.openAnvil(null, true);
    } catch (NoSuchMethodError absent) {
      openMenu(player, MenuType.ANVIL, "Anvil");
    }
  }

  public static void openStonecutter(Player player) {
    try {
      player.openStonecutter(null, true);
    } catch (NoSuchMethodError absent) {
      openMenu(player, MenuType.STONECUTTER, "Stonecutter");
    }
  }

  public static void openCartographyTable(Player player) {
    try {
      player.openCartographyTable(null, true);
    } catch (NoSuchMethodError absent) {
      openMenu(player, MenuType.CARTOGRAPHY_TABLE, "Cartography Table");
    }
  }

  public static void openLoom(Player player) {
    try {
      player.openLoom(null, true);
    } catch (NoSuchMethodError absent) {
      openMenu(player, MenuType.LOOM, "Loom");
    }
  }

  private static void openMenu(Player player, MenuType.Typed<?, ?> menu, String title) {
    player.openInventory(menu.create(player, title));
  }

  public static ItemStack activeItem(LivingEntity entity) {
    try {
      return entity.getActiveItem();
    } catch (NoSuchMethodError absent) {
      return entity instanceof HumanEntity human ? human.getItemInUse() : null;
    }
  }

  public static boolean isHandRaised(LivingEntity entity) {
    try {
      return entity.isHandRaised();
    } catch (NoSuchMethodError absent) {
      return entity instanceof HumanEntity human && (human.getItemInUse() != null || human.isBlocking());
    }
  }

  public static boolean canPlayerPickup(Item item) {
    try {
      return item.canPlayerPickup();
    } catch (NoSuchMethodError absent) {
      return true;
    }
  }

  public static void setCanMobPickup(Item item, boolean canPickup) {
    try {
      item.setCanMobPickup(canPickup);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static void setAggressive(Mob mob, boolean aggressive) {
    try {
      mob.setAggressive(aggressive);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static boolean supportsPathfinding() {
    return pathfindingSupported && hasClass(PATHFINDER_CLASS);
  }

  /** False on platforms without Paper's Pathfinder, and false when no path exists. */
  public static boolean pathfindTo(Mob mob, Location target, double speed) {
    if (!supportsPathfinding()) {
      return false;
    }

    try {
      return PathfinderBridge.moveTo(mob, target, speed);
    } catch (NoSuchMethodError | NoClassDefFoundError absent) {
      pathfindingSupported = false;
      return false;
    }
  }

  public static void stopPathfinding(Mob mob) {
    if (!supportsPathfinding()) {
      return;
    }

    try {
      PathfinderBridge.stop(mob);
    } catch (NoSuchMethodError | NoClassDefFoundError absent) {
      pathfindingSupported = false;
    }
  }

  public static void setShouldBurnInDay(AbstractSkeleton skeleton, boolean burnInDay) {
    try {
      skeleton.setShouldBurnInDay(burnInDay);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static boolean hasFixedPose(Player player) {
    try {
      return player.hasFixedPose();
    } catch (NoSuchMethodError absent) {
      return false;
    }
  }

  public static void setPose(Player player, Pose pose, boolean fixed) {
    try {
      player.setPose(pose, fixed);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static boolean hasLeftShooter(Projectile projectile) {
    try {
      return projectile.hasLeftShooter();
    } catch (NoSuchMethodError absent) {
      return true;
    }
  }

  public static void setHasLeftShooter(Projectile projectile, boolean leftShooter) {
    try {
      projectile.setHasLeftShooter(leftShooter);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static boolean hasBeenShot(Projectile projectile) {
    try {
      return projectile.hasBeenShot();
    } catch (NoSuchMethodError absent) {
      return true;
    }
  }

  public static void setHasBeenShot(Projectile projectile, boolean beenShot) {
    try {
      projectile.setHasBeenShot(beenShot);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static TriState visualFire(Entity entity) {
    try {
      return entity.getVisualFire();
    } catch (NoSuchMethodError absent) {
      return entity.isVisualFire() ? TriState.TRUE : TriState.NOT_SET;
    }
  }

  public static void setVisualFire(Entity entity, TriState state) {
    try {
      entity.setVisualFire(state);
    } catch (NoSuchMethodError absent) {
      entity.setVisualFire(state == TriState.TRUE);
    }
  }

  public static boolean isInvisible(Entity entity) {
    try {
      return entity.isInvisible();
    } catch (NoSuchMethodError absent) {
      return false;
    }
  }

  public static void setInvisible(Entity entity, boolean invisible) {
    try {
      entity.setInvisible(invisible);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static boolean hasNoPhysics(Entity entity) {
    try {
      return entity.hasNoPhysics();
    } catch (NoSuchMethodError absent) {
      return false;
    }
  }

  public static void setNoPhysics(Entity entity, boolean noPhysics) {
    try {
      entity.setNoPhysics(noPhysics);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static Component customName(Entity entity) {
    try {
      return entity.customName();
    } catch (NoSuchMethodError absent) {
      String legacy = entity.getCustomName();
      return legacy == null ? null : LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
  }

  public static void customName(Entity entity, Component name) {
    try {
      entity.customName(name);
    } catch (NoSuchMethodError absent) {
      entity.setCustomName(name == null ? null : LegacyComponentSerializer.legacySection().serialize(name));
    }
  }

  public static void setArrowShooter(AbstractArrow arrow, Player shooter) {
    try {
      arrow.setShooter(shooter, false);
    } catch (NoSuchMethodError absent) {
      arrow.setShooter(shooter);
    }
  }

  public static ItemStack arrowItem(AbstractArrow arrow) {
    try {
      return arrow.getItemStack();
    } catch (NoSuchMethodError absent) {
      return null;
    }
  }

  public static void setArrowItem(AbstractArrow arrow, ItemStack item) {
    if (item == null) {
      return;
    }

    try {
      arrow.setItemStack(item);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static int arrowLifetimeTicks(AbstractArrow arrow) {
    try {
      return arrow.getLifetimeTicks();
    } catch (NoSuchMethodError absent) {
      return 0;
    }
  }

  public static void setArrowLifetimeTicks(AbstractArrow arrow, int ticks) {
    try {
      arrow.setLifetimeTicks(ticks);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static Sound arrowHitSound(AbstractArrow arrow) {
    try {
      return arrow.getHitSound();
    } catch (NoSuchMethodError absent) {
      return null;
    }
  }

  public static void setArrowHitSound(AbstractArrow arrow, Sound sound) {
    if (sound == null) {
      return;
    }

    try {
      arrow.setHitSound(sound);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static boolean tridentGlint(Trident trident) {
    try {
      return trident.hasGlint();
    } catch (NoSuchMethodError absent) {
      return !trident.getItem().getEnchantments().isEmpty();
    }
  }

  public static void setTridentGlint(Trident trident, boolean glint) {
    try {
      trident.setGlint(glint);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static int tridentLoyalty(Trident trident) {
    try {
      return trident.getLoyaltyLevel();
    } catch (NoSuchMethodError absent) {
      return trident.getItem().getEnchantmentLevel(Enchantment.LOYALTY);
    }
  }

  public static void setTridentLoyalty(Trident trident, int level) {
    try {
      trident.setLoyaltyLevel(level);
    } catch (NoSuchMethodError ignored) {
    }
  }

  public static void clearTridentDealtDamage(Trident trident) {
    try {
      trident.setHasDealtDamage(false);
    } catch (NoSuchMethodError ignored) {
    }
  }

  /** Paper's component-exact NBT item payload. */
  public static final byte ITEMS_FORMAT_NBT = 0;
  /** Bukkit object stream payload, the Spigot fallback. */
  public static final byte ITEMS_FORMAT_STREAM = 1;

  private static volatile boolean itemArrayBytesSupported = true;

  /**
   * Serializes a stack array into a format-tagged blob. Paper's NBT payload is
   * preferred because it is component exact; Spigot falls back to the Bukkit
   * object stream. The leading format byte lets a blob written on one platform
   * be recognized - and refused rather than silently emptied - on the other.
   */
  public static byte[] serializeItems(ItemStack[] items) {
    ItemStack[] source = items == null ? new ItemStack[0] : items;
    if (itemArrayBytesSupported) {
      try {
        return frameItems(ITEMS_FORMAT_NBT, ItemStack.serializeItemsAsBytes(source));
      } catch (NoSuchMethodError | NoClassDefFoundError absent) {
        itemArrayBytesSupported = false;
      }
    }

    return frameItems(ITEMS_FORMAT_STREAM, streamItems(source));
  }

  /**
   * @throws IllegalStateException when the blob was written in a format this
   *                               platform cannot read. Callers must preserve
   *                               the blob instead of treating it as empty.
   */
  public static ItemStack[] deserializeItems(byte[] framed) {
    if (framed == null || framed.length == 0) {
      return new ItemStack[0];
    }

    byte format = itemsFormat(framed);
    byte[] payload = itemsPayload(framed);
    if (format == ITEMS_FORMAT_STREAM) {
      return unstreamItems(payload);
    }
    if (format != ITEMS_FORMAT_NBT) {
      throw new IllegalStateException("Unknown item payload format " + format);
    }
    if (!itemArrayBytesSupported) {
      throw new IllegalStateException("Paper item payload cannot be read on this platform");
    }

    try {
      return ItemStack.deserializeItemsFromBytes(payload);
    } catch (NoSuchMethodError | NoClassDefFoundError absent) {
      itemArrayBytesSupported = false;
      throw new IllegalStateException("Paper item payload cannot be read on this platform", absent);
    } catch (IllegalStateException rethrow) {
      throw rethrow;
    } catch (RuntimeException malformed) {
      throw new IllegalStateException("Item payload could not be read", malformed);
    }
  }

  public static byte[] frameItems(byte format, byte[] payload) {
    byte[] source = payload == null ? new byte[0] : payload;
    byte[] framed = new byte[source.length + 1];
    framed[0] = format;
    System.arraycopy(source, 0, framed, 1, source.length);
    return framed;
  }

  public static byte itemsFormat(byte[] framed) {
    return framed == null || framed.length == 0 ? -1 : framed[0];
  }

  public static byte[] itemsPayload(byte[] framed) {
    if (framed == null || framed.length <= 1) {
      return new byte[0];
    }

    byte[] payload = new byte[framed.length - 1];
    System.arraycopy(framed, 1, payload, 0, payload.length);
    return payload;
  }

  private static byte[] streamItems(ItemStack[] items) {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         BukkitObjectOutputStream out = new BukkitObjectOutputStream(buffer)) {
      out.writeInt(items.length);
      for (ItemStack item : items) {
        out.writeObject(item);
      }
      out.flush();
      return buffer.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("Item payload could not be written", error);
    }
  }

  private static ItemStack[] unstreamItems(byte[] payload) {
    try (ByteArrayInputStream buffer = new ByteArrayInputStream(payload);
         BukkitObjectInputStream in = new BukkitObjectInputStream(buffer)) {
      int length = in.readInt();
      if (length < 0 || length > 1024) {
        throw new IllegalStateException("Item payload declares " + length + " entries");
      }
      ItemStack[] items = new ItemStack[length];
      for (int index = 0; index < length; index++) {
        items[index] = (ItemStack) in.readObject();
      }
      return items;
    } catch (IOException | ClassNotFoundException error) {
      throw new IllegalStateException("Item payload could not be read", error);
    }
  }

  private static volatile boolean createEntitySupported = true;

  /** Hydrates before the entity enters the world on both platforms. */
  public static <T extends Entity> T spawnHydrated(World world, Location location, Class<T> type, Consumer<T> hydrator) {
    if (createEntitySupported) {
      boolean created = false;
      try {
        T entity = world.createEntity(location, type);
        created = true;
        hydrator.accept(entity);
        return world.addEntity(entity);
      } catch (NoSuchMethodError absent) {
        if (created) {
          throw absent;
        }
        createEntitySupported = false;
      }
    }

    return world.spawn(location, type, hydrator::accept);
  }

  /**
   * Keeps every Pathfinder reference out of PaperCompat's own constant pool so
   * the class still verifies on Spigot, where the type does not exist. Only the
   * probe-guarded call sites above load this holder.
   */
  private static final class PathfinderBridge {
    private PathfinderBridge() {
    }

    private static boolean moveTo(Mob mob, Location target, double speed) {
      Pathfinder pathfinder = mob.getPathfinder();
      return pathfinder != null && pathfinder.moveTo(target, speed);
    }

    private static void stop(Mob mob) {
      Pathfinder pathfinder = mob.getPathfinder();
      if (pathfinder != null) {
        pathfinder.stopPathfinding();
      }
    }
  }
}
