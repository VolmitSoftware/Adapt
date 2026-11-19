package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.GeneratedStructure;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.entity.Item;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * World access on Paper 1.20.1: chunks do not list their structure starts, and an item entity cannot exist outside
 * the world, so a created item is dropped into it right away.
 */
final class BukkitWorldVersion {
  private BukkitWorldVersion() {
  }

  static List<GeneratedStructure> structuresAt(Chunk chunk) {
    return List.of();
  }

  static StructureType structureType(Id type) {
    return Registry.STRUCTURE_TYPE.get(BukkitConversions.key(type));
  }

  static Item createItem(Location location, ItemStack stack) {
    return location.getWorld().dropItem(location, stack);
  }
}
