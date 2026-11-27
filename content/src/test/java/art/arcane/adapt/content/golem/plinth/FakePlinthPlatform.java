package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

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
  private static final Id AIR = Id.minecraft("air");
  final Map<PlinthSpot, PlinthSlotItem> store = new HashMap<>();
  final Map<String, Id> blocks = new HashMap<>();
  final Map<UUID, WorldRef> worlds = new HashMap<>();
  final List<Runnable> scheduled = new ArrayList<>();
  final List<Integer> entityDelays = new ArrayList<>();
  final List<Runnable> asyncTasks = new ArrayList<>();
  final List<String> events;
  final List<Told> told = new ArrayList<>();
  final List<ItemDisplayRef> displays = new ArrayList<>();
  final Map<PlayerRef, ItemRef> held = new HashMap<>();
  final Set<ItemRef> crooks = new HashSet<>();
  boolean ownsEverything = true;
  boolean regionsHalted;
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
  public PlinthSlotItem read(WorldRef world, PlinthSpot spot) {
    if (spot.equals(swapSpot) && readsBeforeSwap-- <= 0) {
      store.put(spot, swapItem);
      swapSpot = null;
    }
    return store.get(spot);
  }

  @Override
  public void write(WorldRef world, PlinthSpot spot, PlinthSlotItem item) {
    events.add("write " + spot.x() + "," + spot.y() + "," + spot.z() + " " + (item == null ? "empty" : "item"));
    if (item == null) {
      store.remove(spot);
    } else {
      store.put(spot, item);
    }
  }

  @Override
  public Map<PlinthSpot, PlinthSlotItem> readChunk(WorldRef world, int chunkX, int chunkZ) {
    chunkReads++;
    Map<PlinthSpot, PlinthSlotItem> slots = new HashMap<>();
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : store.entrySet()) {
      PlinthSpot spot = entry.getKey();
      if (spot.world().equals(world.uid()) && spot.chunkX() == chunkX && spot.chunkZ() == chunkZ) {
        slots.put(spot, entry.getValue());
      }
    }
    return slots;
  }

  @Override
  public boolean savePlate(WorldRef world, int x, int z) {
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
  public Id blockType(WorldRef world, int x, int y, int z) {
    return blocks.getOrDefault(x + "," + y + "," + z, AIR);
  }

  @Override
  public boolean runAt(Loc location, Runnable task, int delayTicks) {
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
  public boolean runEntity(EntityRef entity, Runnable task, int delayTicks) {
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
  public boolean owns(Loc location) {
    return ownsEverything;
  }

  @Override
  public boolean owns(EntityRef entity) {
    return ownsEverything;
  }

  @Override
  public boolean regionsHalted() {
    return regionsHalted;
  }

  @Override
  public WorldRef world(UUID id) {
    return worlds.get(id);
  }

  @Override
  public ItemDisplayRef spawnDisplay(Loc at, ItemRef item) {
    ItemDisplayRef display = mock(ItemDisplayRef.class);
    lenient().when(display.isValid()).thenReturn(true);
    lenient().when(display.item()).thenReturn(item);
    displays.add(display);
    return display;
  }

  @Override
  public void drop(Loc at, ItemRef item) {
    events.add("drop " + at.blockX() + "," + at.blockY() + "," + at.blockZ());
  }

  @Override
  public void popResult(Loc at, ItemRef item) {
    events.add("pop " + at.blockX() + "," + at.blockY() + "," + at.blockZ());
  }

  @Override
  public void give(PlayerRef player, ItemRef item) {
    events.add("give");
  }

  @Override
  public ItemRef takeOne(PlayerRef player) {
    events.add("take");
    return held.get(player);
  }

  @Override
  public void saveData(PlayerRef player) {
    events.add("save player");
  }

  @Override
  public boolean isCrook(ItemRef item) {
    return item != null && crooks.contains(item);
  }

  @Override
  public void tell(PlayerRef player, boolean warning, Text message) {
    events.add((warning ? "warn " : "notify ") + message.key());
    Map<String, Object> values = new LinkedHashMap<>();
    for (Text.Argument argument : message.arguments()) {
      values.put(argument.name(), argument.value());
    }
    told.add(new Told(warning, message.key(), values, message));
  }

  @Override
  public void denied(PlayerRef player) {
    events.add("denied");
  }

  @Override
  public String resultName(Infusion infusion) {
    return "Result " + infusion.id();
  }

  @Override
  public void effect(PlinthEffect effect, Loc at) {
    events.add("effect " + effect.name());
  }

  @Override
  public void swirl(Loc center, InfusionRitual ritual) {
    events.add("swirl " + ritual.elapsed());
  }

  @Override
  public void fizzle(Loc center, InfusionRitual ritual) {
    events.add("fizzle " + ritual.elapsed());
  }

  @Override
  public void collide(Loc center, InfusionTint tint) {
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

  record Told(boolean warning, String key, Map<String, Object> values, Text message) {
  }
}
