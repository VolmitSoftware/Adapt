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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

final class FakePlinthPlatform implements PlinthPlatform {
  final Map<PlinthSpot, PlinthSlotItem> store = new HashMap<>();
  final Map<String, Material> blocks = new HashMap<>();
  final Map<UUID, World> worlds = new HashMap<>();
  final List<Runnable> scheduled = new ArrayList<>();
  final List<Integer> entityDelays = new ArrayList<>();
  final List<Runnable> asyncTasks = new ArrayList<>();
  final List<String> events;
  final List<Told> told = new ArrayList<>();
  final List<ItemDisplay> displays = new ArrayList<>();
  final Map<Player, ItemStack> held = new HashMap<>();
  final Set<ItemStack> crooks = new HashSet<>();
  boolean ownsEverything = true;
  boolean deferAsync;
  boolean refuseRunAt;
  int busySaves;
  RuntimeException saveFailure;
  int chunkReads;
  long now = 1_000L;
  private PlinthSpot swapSpot;
  private PlinthSlotItem swapItem;
  private int readsBeforeSwap;

  FakePlinthPlatform(List<String> events) {
    this.events = events;
  }

  @Override
  public PlinthSlotItem read(World world, PlinthSpot spot) {
    if (spot.equals(swapSpot) && readsBeforeSwap-- <= 0) {
      store.put(spot, swapItem);
      swapSpot = null;
    }
    return store.get(spot);
  }

  @Override
  public void write(World world, PlinthSpot spot, PlinthSlotItem item) {
    events.add("write " + spot.x() + "," + spot.y() + "," + spot.z() + " " + (item == null ? "empty" : "item"));
    if (item == null) {
      store.remove(spot);
    } else {
      store.put(spot, item);
    }
  }

  @Override
  public Map<PlinthSpot, PlinthSlotItem> readChunk(World world, int chunkX, int chunkZ) {
    chunkReads++;
    Map<PlinthSpot, PlinthSlotItem> slots = new HashMap<>();
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : store.entrySet()) {
      PlinthSpot spot = entry.getKey();
      if (spot.world().equals(world.getUID()) && spot.chunkX() == chunkX && spot.chunkZ() == chunkZ) {
        slots.put(spot, entry.getValue());
      }
    }
    return slots;
  }

  @Override
  public boolean savePlate(World world, int x, int z) {
    events.add("save " + (x >> 9) + "," + (z >> 9));
    if (saveFailure != null) {
      throw saveFailure;
    }
    if (busySaves > 0) {
      busySaves--;
      return false;
    }
    return true;
  }

  @Override
  public Material blockType(World world, int x, int y, int z) {
    return blocks.getOrDefault(x + "," + y + "," + z, Material.AIR);
  }

  @Override
  public boolean runAt(Location location, Runnable task, int delayTicks) {
    if (refuseRunAt) {
      return false;
    }
    if (delayTicks <= 0) {
      task.run();
    } else {
      scheduled.add(task);
    }
    return true;
  }

  @Override
  public boolean runEntity(Entity entity, Runnable task, int delayTicks) {
    entityDelays.add(delayTicks);
    if (delayTicks <= 0) {
      task.run();
    } else {
      scheduled.add(task);
    }
    return true;
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0 && !deferAsync) {
      task.run();
    } else {
      asyncTasks.add(task);
    }
  }

  @Override
  public boolean owns(Location location) {
    return ownsEverything;
  }

  @Override
  public boolean owns(Entity entity) {
    return ownsEverything;
  }

  @Override
  public World world(UUID id) {
    return worlds.get(id);
  }

  @Override
  public ItemDisplay spawnDisplay(Location at, ItemStack item) {
    ItemDisplay display = mock(ItemDisplay.class);
    lenient().when(display.isValid()).thenReturn(true);
    lenient().when(display.getItemStack()).thenReturn(item);
    displays.add(display);
    return display;
  }

  @Override
  public void drop(Location at, ItemStack item) {
    events.add("drop " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
  }

  @Override
  public void popResult(Location at, ItemStack item) {
    events.add("pop " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
  }

  @Override
  public void give(Player player, ItemStack item) {
    events.add("give");
  }

  @Override
  public ItemStack takeOne(Player player) {
    events.add("take");
    return held.get(player);
  }

  @Override
  public void saveData(Player player) {
    events.add("save player");
  }

  @Override
  public boolean isCrook(ItemStack item) {
    return item != null && crooks.contains(item);
  }

  @Override
  public void tell(Player player, boolean warning, TextKey key, MessageArgument... arguments) {
    events.add((warning ? "warn " : "notify ") + key.id());
    Map<String, Object> values = new LinkedHashMap<>();
    for (MessageArgument argument : arguments) {
      values.put(argument.name(), argument.value());
    }
    told.add(new Told(warning, key, values, arguments));
  }

  @Override
  public void denied(Player player) {
    events.add("denied");
  }

  @Override
  public String resultName(Infusion infusion) {
    return "Result " + infusion.id();
  }

  @Override
  public void effect(PlinthEffect effect, Location at) {
    events.add("effect " + effect.name());
  }

  @Override
  public void swirl(Location center, InfusionRitual ritual) {
    events.add("swirl " + ritual.elapsed());
  }

  @Override
  public void fizzle(Location center, InfusionRitual ritual) {
    events.add("fizzle " + ritual.elapsed());
  }

  @Override
  public void collide(Location center, InfusionTint tint) {
    events.add("collide");
  }

  @Override
  public long nowMillis() {
    return now;
  }

  void swapAfterReads(PlinthSpot spot, PlinthSlotItem replacement, int reads) {
    swapSpot = spot;
    swapItem = replacement;
    readsBeforeSwap = reads;
  }

  void tick() {
    List<Runnable> due = List.copyOf(scheduled);
    scheduled.clear();
    for (Runnable task : due) {
      task.run();
    }
  }

  void drainAsync() {
    while (!asyncTasks.isEmpty()) {
      List<Runnable> due = List.copyOf(asyncTasks);
      asyncTasks.clear();
      for (Runnable task : due) {
        task.run();
      }
    }
  }

  record Told(boolean warning, TextKey key, Map<String, Object> values, MessageArgument[] arguments) {
  }
}
