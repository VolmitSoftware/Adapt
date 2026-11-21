package art.arcane.adapt.util.common.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.function.Consumer;

/** The parts of {@link PaperCompat} bound to Paper API added after 1.20.1. */
final class PaperCompat120Version {
  private static volatile boolean enchantWithLevelsSupported = true;
  private static volatile boolean createEntitySupported = true;

  private PaperCompat120Version() {
  }

  static boolean itemArrayBytes() {
    return true;
  }

  static byte[] serializeItemArray(ItemStack[] items) {
    return ItemStack.serializeItemsAsBytes(items);
  }

  static ItemStack[] deserializeItemArray(byte[] payload) {
    return ItemStack.deserializeItemsFromBytes(payload);
  }

  @SuppressWarnings("deprecation")
  static ItemStack enchantWithLevels(ItemStack item, int levels, Random random) {
    if (enchantWithLevelsSupported) {
      try {
        return Bukkit.getItemFactory().enchantWithLevels(item, levels, true, random);
      } catch (NoSuchMethodError absent) {
        enchantWithLevelsSupported = false;
      }
    }
    return Bukkit.getItemFactory().enchantItem(item, levels, true);
  }

  static int experienceOrbCount(ExperienceOrb orb) {
    try {
      return orb.getCount();
    } catch (NoSuchMethodError absent) {
      return 1;
    }
  }

  static void setAggressive(Mob mob, boolean aggressive) {
    try {
      mob.setAggressive(aggressive);
    } catch (NoSuchMethodError ignored) {
    }
  }

  static boolean isInvisible(Entity entity) {
    try {
      return entity.isInvisible();
    } catch (NoSuchMethodError absent) {
      return false;
    }
  }

  static void setInvisible(Entity entity, boolean invisible) {
    try {
      entity.setInvisible(invisible);
    } catch (NoSuchMethodError ignored) {
    }
  }

  static boolean hasNoPhysics(Entity entity) {
    try {
      return entity.hasNoPhysics();
    } catch (NoSuchMethodError absent) {
      return false;
    }
  }

  static void setNoPhysics(Entity entity, boolean noPhysics) {
    try {
      entity.setNoPhysics(noPhysics);
    } catch (NoSuchMethodError ignored) {
    }
  }

  static void setArrowShooter(AbstractArrow arrow, Player shooter) {
    try {
      arrow.setShooter(shooter, false);
    } catch (NoSuchMethodError absent) {
      arrow.setShooter(shooter);
    }
  }

  static void setArrowItem(AbstractArrow arrow, ItemStack item) {
    if (item == null) {
      return;
    }

    try {
      arrow.setItemStack(item);
    } catch (NoSuchMethodError ignored) {
    }
  }

  static <T extends Entity> T spawnHydrated(World world, Location location, Class<T> type, Consumer<T> hydrator) {
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
