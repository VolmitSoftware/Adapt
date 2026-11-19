package art.arcane.adapt.util.common.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.function.Consumer;

/**
 * The parts of {@link PaperCompat} bound to Paper API that 1.20.1 lacks. Item arrays use the Bukkit object stream, an
 * entity is hydrated inside the spawn callback, and orb merging, aggression, physics and arrow item state are absent.
 */
final class PaperCompat120Version {
  private PaperCompat120Version() {
  }

  static boolean itemArrayBytes() {
    return false;
  }

  static byte[] serializeItemArray(ItemStack[] items) {
    throw new UnsupportedOperationException("Item arrays have no byte form on this server");
  }

  static ItemStack[] deserializeItemArray(byte[] payload) {
    throw new UnsupportedOperationException("Item arrays have no byte form on this server");
  }

  static ItemStack enchantWithLevels(ItemStack item, int levels, Random random) {
    return Bukkit.getItemFactory().enchantWithLevels(item, levels, true, random);
  }

  static int experienceOrbCount(ExperienceOrb orb) {
    return 1;
  }

  static void setAggressive(Mob mob, boolean aggressive) {
  }

  static boolean isInvisible(Entity entity) {
    return entity instanceof LivingEntity living && living.isInvisible();
  }

  static void setInvisible(Entity entity, boolean invisible) {
    if (entity instanceof LivingEntity living) {
      living.setInvisible(invisible);
    }
  }

  static boolean hasNoPhysics(Entity entity) {
    return false;
  }

  static void setNoPhysics(Entity entity, boolean noPhysics) {
  }

  static void setArrowShooter(AbstractArrow arrow, Player shooter) {
    arrow.setShooter(shooter);
  }

  static void setArrowItem(AbstractArrow arrow, ItemStack item) {
  }

  static <T extends Entity> T spawnHydrated(World world, Location location, Class<T> type, Consumer<T> hydrator) {
    return world.spawn(location, type, hydrator::accept);
  }
}
