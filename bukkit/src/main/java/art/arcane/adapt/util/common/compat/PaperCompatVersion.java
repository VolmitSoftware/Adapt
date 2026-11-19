package art.arcane.adapt.util.common.compat;

import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import io.papermc.paper.world.WeatheringCopperState;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

/** The parts of {@link PaperCompat} bound to the 26.x Paper API. */
final class PaperCompatVersion {
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
  private static volatile boolean weatheringSupported = true;
  private static volatile boolean berriesSupported = true;
  private static volatile boolean itemNameSupported = true;

  private PaperCompatVersion() {
  }

  static boolean replaceableFallback(Material material) {
    return REPLACEABLE_FALLBACK.contains(material);
  }

  static int weatheringStage(Entity entity) {
    if (!(entity instanceof CopperGolem golem)) {
      return 0;
    }
    if (!weatheringSupported) {
      return SpigotWeathering.stage(golem);
    }
    try {
      return switch (golem.getWeatheringState()) {
        case UNAFFECTED -> 0;
        case EXPOSED -> 1;
        case WEATHERED -> 2;
        case OXIDIZED -> 3;
      };
    } catch (NoClassDefFoundError | NoSuchMethodError absent) {
      weatheringSupported = false;
      return SpigotWeathering.stage(golem);
    }
  }

  static boolean copperWaxed(Entity entity) {
    if (!weatheringSupported) {
      return false;
    }
    try {
      return entity instanceof CopperGolem golem && golem.getOxidizing() instanceof CopperGolem.Oxidizing.Waxed;
    } catch (NoClassDefFoundError | NoSuchMethodError absent) {
      weatheringSupported = false;
      return false;
    }
  }

  static void restoreCopper(Entity entity, int stage, boolean waxed) {
    if (!(entity instanceof CopperGolem golem)) {
      return;
    }
    int bounded = Math.max(0, Math.min(3, stage));
    if (!weatheringSupported) {
      SpigotWeathering.restore(golem, bounded);
      return;
    }
    try {
      golem.setWeatheringState(switch (bounded) {
        case 0 -> WeatheringCopperState.UNAFFECTED;
        case 1 -> WeatheringCopperState.EXPOSED;
        case 2 -> WeatheringCopperState.WEATHERED;
        default -> WeatheringCopperState.OXIDIZED;
      });
      if (waxed) {
        golem.setOxidizing(CopperGolem.Oxidizing.waxed());
      }
    } catch (NoClassDefFoundError | NoSuchMethodError absent) {
      weatheringSupported = false;
      SpigotWeathering.restore(golem, bounded);
    }
  }

  static boolean hasBerries(CaveVinesPlant vines) {
    if (berriesSupported) {
      try {
        return vines.hasBerries();
      } catch (NoSuchMethodError absent) {
        berriesSupported = false;
      }
    }
    return SpigotBerries.read(vines);
  }

  static TriState visualFire(Entity entity) {
    try {
      return entity.getVisualFire();
    } catch (NoSuchMethodError absent) {
      return entity.isVisualFire() ? TriState.TRUE : TriState.NOT_SET;
    }
  }

  static void setVisualFire(Entity entity, TriState state) {
    try {
      entity.setVisualFire(state);
    } catch (NoSuchMethodError absent) {
      entity.setVisualFire(state == TriState.TRUE);
    }
  }

  static String itemName(ItemStack item) {
    if (itemNameSupported) {
      try {
        return C.stripColor(LegacyComponentSerializer.legacySection().serialize(item.effectiveName()));
      } catch (NoSuchMethodError absent) {
        itemNameSupported = false;
      }
    }
    return Form.capitalizeWords(item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
  }

  static void openMenu(Player player, InventoryType type, String title) {
    MenuType.Typed<?, ?> menu;
    if (type == InventoryType.GRINDSTONE) {
      menu = MenuType.GRINDSTONE;
    } else if (type == InventoryType.ANVIL) {
      menu = MenuType.ANVIL;
    } else if (type == InventoryType.STONECUTTER) {
      menu = MenuType.STONECUTTER;
    } else if (type == InventoryType.CARTOGRAPHY) {
      menu = MenuType.CARTOGRAPHY_TABLE;
    } else if (type == InventoryType.LOOM) {
      menu = MenuType.LOOM;
    } else {
      throw new IllegalArgumentException("No menu for " + type);
    }
    player.openInventory(menu.create(player, title));
  }

  private static final class SpigotBerries {
    private static volatile boolean berriesResolved;
    private static volatile Method berries;

    private SpigotBerries() {
    }

    private static boolean read(CaveVinesPlant vines) {
      Method read = berriesReader();
      if (read == null) {
        return false;
      }
      try {
        return Boolean.TRUE.equals(read.invoke(vines));
      } catch (ReflectiveOperationException | RuntimeException failure) {
        return false;
      }
    }

    private static Method berriesReader() {
      if (!berriesResolved) {
        try {
          berries = CaveVinesPlant.class.getMethod("isBerries");
        } catch (NoSuchMethodException missing) {
          berries = null;
        }
        berriesResolved = true;
      }
      return berries;
    }
  }

  private static final class SpigotWeathering {
    private static final String[] STAGE_NAMES = {"UNAFFECTED", "EXPOSED", "WEATHERED", "OXIDIZED"};
    private static volatile boolean resolved;
    private static volatile Method getter;
    private static volatile Method setter;

    private SpigotWeathering() {
    }

    private static int stage(CopperGolem golem) {
      Method read = getter(golem);
      if (read == null) {
        return 0;
      }
      try {
        Object state = read.invoke(golem);
        return state instanceof Enum<?> value ? Math.max(0, indexOf(value.name())) : 0;
      } catch (ReflectiveOperationException | RuntimeException failure) {
        return 0;
      }
    }

    private static void restore(CopperGolem golem, int stage) {
      Method read = getter(golem);
      Method write = setter;
      if (read == null || write == null) {
        return;
      }
      try {
        Class<?> states = read.getReturnType();
        for (Object constant : states.getEnumConstants()) {
          if (constant instanceof Enum<?> value && value.name().equals(STAGE_NAMES[stage])) {
            write.invoke(golem, constant);
            return;
          }
        }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
      }
    }

    private static Method getter(CopperGolem golem) {
      if (!resolved) {
        try {
          Method read = golem.getClass().getMethod("getWeatherState");
          getter = read;
          setter = golem.getClass().getMethod("setWeatherState", read.getReturnType());
        } catch (ReflectiveOperationException | RuntimeException absent) {
          getter = null;
          setter = null;
        }
        resolved = true;
      }
      return getter;
    }

    private static int indexOf(String name) {
      for (int index = 0; index < STAGE_NAMES.length; index++) {
        if (STAGE_NAMES[index].equals(name)) {
          return index;
        }
      }
      return 0;
    }
  }
}
