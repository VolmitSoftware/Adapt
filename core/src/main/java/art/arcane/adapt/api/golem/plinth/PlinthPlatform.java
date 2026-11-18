package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;
import java.util.UUID;

interface PlinthPlatform {
  PlinthSlotItem read(WorldRef world, PlinthSpot spot);

  void write(WorldRef world, PlinthSpot spot, PlinthSlotItem item);

  Map<PlinthSpot, PlinthSlotItem> readChunk(WorldRef world, int chunkX, int chunkZ);

  boolean savePlate(WorldRef world, int x, int z);

  Id blockType(WorldRef world, int x, int y, int z);

  boolean runAt(Loc location, Runnable task, int delayTicks);

  boolean runEntity(EntityRef entity, Runnable task, int delayTicks);

  void async(Runnable task, int delayTicks);

  boolean owns(Loc location);

  boolean owns(EntityRef entity);

  boolean regionsHalted();

  WorldRef world(UUID id);

  ItemDisplayRef spawnDisplay(Loc at, ItemRef item);

  void drop(Loc at, ItemRef item);

  void popResult(Loc at, ItemRef item);

  void give(PlayerRef player, ItemRef item);

  ItemRef takeOne(PlayerRef player);

  void saveData(PlayerRef player);

  boolean isCrook(ItemRef item);

  void tell(PlayerRef player, boolean warning, TextKey key, MessageArgument... arguments);

  void denied(PlayerRef player);

  String resultName(Infusion infusion);

  void effect(PlinthEffect effect, Loc at);

  void swirl(Loc center, InfusionRitual ritual);

  void fizzle(Loc center, InfusionRitual ritual);

  void collide(Loc center, InfusionTint tint);

  long nowMillis();
}
