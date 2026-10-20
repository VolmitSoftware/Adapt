package art.arcane.adapt.api.golem.plinth;

import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

interface PlinthPlatform {
  PlinthSlotItem read(World world, PlinthSpot spot);

  void write(World world, PlinthSpot spot, PlinthSlotItem item);

  Map<PlinthSpot, PlinthSlotItem> readChunk(World world, int chunkX, int chunkZ);

  boolean savePlate(World world, int x, int z);

  Material blockType(World world, int x, int y, int z);

  boolean runAt(Location location, Runnable task, int delayTicks);

  boolean runEntity(Entity entity, Runnable task, int delayTicks);

  void async(Runnable task, int delayTicks);

  boolean owns(Location location);

  boolean owns(Entity entity);

  World world(UUID id);

  ItemDisplay spawnDisplay(Location at, ItemStack item);

  void drop(Location at, ItemStack item);

  void popResult(Location at, ItemStack item);

  void give(Player player, ItemStack item);

  ItemStack takeOne(Player player);

  void saveData(Player player);

  boolean isCrook(ItemStack item);

  void tell(Player player, boolean warning, TextKey key, MessageArgument... arguments);

  void denied(Player player);

  String resultName(Infusion infusion);

  void effect(PlinthEffect effect, Location at);

  void swirl(Location center, InfusionRitual ritual);

  void fizzle(Location center, InfusionRitual ritual);

  void collide(Location center, InfusionTint tint);

  long nowMillis();
}
