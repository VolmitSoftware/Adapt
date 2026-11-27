package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRegistries;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.GeneratedStructure;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class BukkitWorldVersion {
  private BukkitWorldVersion() {
  }

  static List<GeneratedStructure> structuresAt(Chunk chunk) {
    Collection<org.bukkit.generator.structure.GeneratedStructure> found = chunk.getStructures();
    List<GeneratedStructure> structures = new ArrayList<>(found.size());
    for (org.bukkit.generator.structure.GeneratedStructure structure : found) {
      BoundingBox box = structure.getBoundingBox();
      structures.add(new GeneratedStructure(
          BukkitConversions.id(BukkitRegistries.keyOrThrow(Structure.class, structure.getStructure())),
          BukkitConversions.id(structure.getStructure().getStructureType().getKey()),
          new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())));
    }
    return structures;
  }

  static StructureType structureType(Id type) {
    Registry<StructureType> registry = BukkitRegistries.registry(StructureType.class);
    return registry == null ? null : registry.get(BukkitConversions.key(type));
  }

  /** An item entity that is not yet in the world. */
  static Item createItem(Location location, ItemStack stack) {
    World world = location.getWorld();
    Item created = world.createEntity(location, Item.class);
    created.setItemStack(stack);
    return created;
  }
}
