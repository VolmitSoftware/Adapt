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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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
  private static final Map<String, Boolean> CLASS_PRESENCE = new ConcurrentHashMap<>();
  private static volatile boolean teleportAsyncSupported = true;
  private static volatile boolean teleportAsyncCauseSupported = true;
  private static volatile boolean transientModifierSupported = true;
  private static volatile boolean damageCriticalSupported = true;
  private static volatile boolean potionSourceSupported = true;
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
    if (potionSourceSupported) {
      try {
        return event.getSource();
      } catch (NoSuchMethodError absent) {
        potionSourceSupported = false;
      }
    }

    return null;
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
}
