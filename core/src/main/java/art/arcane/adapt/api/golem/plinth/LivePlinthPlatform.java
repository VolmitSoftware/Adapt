package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.Billboard;
import art.arcane.adapt.platform.entity.display.ItemDisplayMode;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.data.StackExclusion;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class LivePlinthPlatform implements PlinthPlatform {
  private static final double POP_SPEED = 0.2D;
  private static final float DENY_VOLUME = 0.7F;
  private static final float DENY_PITCH = 1F;
  private final Supplier<PlinthHost> host;
  private final InfusionVisuals visuals;

  LivePlinthPlatform(Supplier<PlinthHost> host) {
    this.host = Objects.requireNonNull(host);
    this.visuals = new InfusionVisuals(host);
  }

  @Override
  public PlinthSlotItem read(WorldRef world, PlinthSpot spot) {
    return world.isLoaded() ? BlockStorePort.current().get(world, spot.x(), spot.y(), spot.z(), PlinthSlotCodec.SLICE) : null;
  }

  @Override
  public void write(WorldRef world, PlinthSpot spot, PlinthSlotItem item) {
    if (item == null) {
      BlockStorePort.current().remove(world, spot.x(), spot.y(), spot.z(), PlinthSlotCodec.SLICE);
    } else {
      BlockStorePort.current().set(world, spot.x(), spot.y(), spot.z(), PlinthSlotCodec.SLICE, item);
    }
  }

  @Override
  public Map<PlinthSpot, PlinthSlotItem> readChunk(WorldRef world, int chunkX, int chunkZ) {
    if (!world.isLoaded()) {
      return Map.of();
    }
    UUID worldId = world.uid();
    Map<PlinthSpot, PlinthSlotItem> slots = new HashMap<>();
    BlockStorePort.current().iterateChunk(world, chunkX, chunkZ, PlinthSlotCodec.SLICE,
        (int x, int y, int z, PlinthSlotItem item) -> slots.put(new PlinthSpot(worldId, x, y, z), item));
    return slots;
  }

  @Override
  public boolean savePlate(WorldRef world, int x, int z) {
    return BlockStorePort.current().savePlate(world, x, z);
  }

  @Override
  public Id blockType(WorldRef world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4) || !Tasks.isOwnedByCurrentRegion(new Loc(world, x, y, z))) {
      return null;
    }
    return world.blockAt(x, y, z).type();
  }

  @Override
  public boolean runAt(Loc location, Runnable task, int delayTicks) {
    return delayTicks <= 0 ? Tasks.runAt(location, task) : Tasks.runAt(location, task, delayTicks);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, int delayTicks) {
    if (AdaptCore.current() == null) {
      return false;
    }
    return delayTicks <= 0 ? Tasks.runEntity(entity, task) : Tasks.runEntity(entity, task, delayTicks);
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      Tasks.a(task);
    } else {
      Tasks.a(task, delayTicks);
    }
  }

  @Override
  public boolean owns(Loc location) {
    return Tasks.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean owns(EntityRef entity) {
    return Tasks.isOwnedByCurrentRegion(entity);
  }

  @Override
  public boolean regionsHalted() {
    return Tasks.isFoliaThreading() && !Tasks.isPrimaryThread();
  }

  @Override
  public WorldRef world(UUID id) {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().host().world(id);
  }

  @Override
  public ItemDisplayRef spawnDisplay(Loc at, ItemRef item) {
    ItemRef shown = item.copy();
    return at.world().spawnItemDisplay(at, shown, (ItemDisplayRef display) -> {
      display.setPersistent(false);
      StackExclusion.exclude(display);
      display.setItem(shown);
      display.setDisplayMode(ItemDisplayMode.FIXED);
      display.setBillboard(Billboard.FIXED);
    });
  }

  @Override
  public void drop(Loc at, ItemRef item) {
    at.world().dropItemNaturally(at.position(), item);
  }

  @Override
  public void popResult(Loc at, ItemRef item) {
    ItemEntityRef dropped = at.world().dropItem(at.position(), item);
    if (dropped != null) {
      dropped.setVelocity(new Vec3(0D, POP_SPEED, 0D));
    }
  }

  @Override
  public void give(PlayerRef player, ItemRef item) {
    for (ItemRef leftover : player.inventory().addItem(item)) {
      player.world().dropItemNaturally(player.location().position(), leftover);
    }
  }

  @Override
  public ItemRef takeOne(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    ItemRef held = inventory.mainHand();
    ItemRef single = held.copyWithAmount(1);
    int amount = held.amount();
    if (amount <= 1) {
      inventory.setMainHand(AdaptCore.platform().host().items().empty());
    } else {
      held.setAmount(amount - 1);
      inventory.setMainHand(held);
    }
    return single;
  }

  @Override
  public void saveData(PlayerRef player) {
    if (Tasks.isOwnedByCurrentRegion(player)) {
      player.saveData();
    } else {
      Tasks.runEntity(player, player::saveData);
    }
  }

  @Override
  public boolean isCrook(ItemRef item) {
    return GolemItems.kindOf(item).filter((GolemItemKind kind) -> kind == GolemItemKind.CROOK).isPresent();
  }

  @Override
  public void tell(PlayerRef player, boolean warning, TextKey key, MessageArgument... arguments) {
    Tasks.runEntity(player, () -> AdaptHud.actionBar(player, (warning ? C.RED : C.AQUA) + AdaptLanguage.text(key, arguments)));
  }

  @Override
  public void denied(PlayerRef player) {
    Tasks.runEntity(player, () -> player.playSound(player.location(), Sounds.ENTITY_VILLAGER_NO, SoundCategory.MASTER, DENY_VOLUME, DENY_PITCH));
  }

  @Override
  public String resultName(Infusion infusion) {
    ItemRef item = infusion.resultItem().get();
    String name = item.hasDisplayName() ? C.stripColor(MarkupPort.current().legacy(item.displayName())) : item.type().legacyName();
    Optional<GolemRune> rune = GolemItems.runeOf(item);
    if (rune.isEmpty()) {
      return name;
    }
    return name + " (" + AdaptLanguage.text(GolemancyMessages.RUNE_TIER, trusted("tier", rune.get().tier())) + ")";
  }

  @Override
  public void effect(PlinthEffect effect, Loc at) {
    PlinthHost active = host.get();
    if (active == null) {
      return;
    }
    switch (effect) {
      case LOADED -> active.fx(at, FxPriority.GAMEPLAY)
          .burst(Particles.END_ROD, 6, 0.2D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_PLACE, 0.6F, 1.5F);
      case RETRIEVED -> active.fx(at, FxPriority.GAMEPLAY)
          .burst(Particles.END_ROD, 4, 0.2D)
          .sound(Sounds.ENTITY_ITEM_PICKUP, 0.5F, 1.2F);
      case STARTED -> active.fx(at, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 2D, 16, 0.6D)
          .sound(Sounds.BLOCK_BEACON_ACTIVATE, 0.6F, 1.4F);
    }
  }

  @Override
  public void swirl(Loc center, InfusionRitual ritual) {
    visuals.swirl(center, ritual);
  }

  @Override
  public void fizzle(Loc center, InfusionRitual ritual) {
    visuals.fizzle(center, ritual);
  }

  @Override
  public void collide(Loc center, InfusionTint tint) {
    visuals.collide(center, tint);
  }

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }
}
