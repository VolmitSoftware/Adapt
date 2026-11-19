package art.arcane.adapt.util.common.compat;

import art.arcane.adapt.util.common.format.C;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;

/** The parts of {@link PaperCompat} bound to the Paper API, for Paper 1.21.1. There are no copper golems. */
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
      Material.SEAGRASS,
      Material.TALL_SEAGRASS,
      Material.WARPED_ROOTS,
      Material.CRIMSON_ROOTS,
      Material.NETHER_SPROUTS
  );

  private PaperCompatVersion() {
  }

  static boolean replaceableFallback(Material material) {
    return REPLACEABLE_FALLBACK.contains(material);
  }

  static int weatheringStage(Entity entity) {
    return 0;
  }

  static boolean copperWaxed(Entity entity) {
    return false;
  }

  static void restoreCopper(Entity entity, int stage, boolean waxed) {
  }

  static boolean hasBerries(CaveVinesPlant vines) {
    return vines.isBerries();
  }

  static TriState visualFire(Entity entity) {
    return entity.isVisualFire() ? TriState.TRUE : TriState.NOT_SET;
  }

  static void setVisualFire(Entity entity, TriState state) {
    entity.setVisualFire(state == TriState.TRUE);
  }

  @SuppressWarnings("deprecation")
  static String itemName(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta != null && meta.hasDisplayName()) {
      return C.stripColor(meta.getDisplayName());
    }
    return C.stripColor(item.getI18NDisplayName());
  }

  static void openMenu(Player player, InventoryType type, String title) {
    MenuType.Typed<?> menu;
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
}
