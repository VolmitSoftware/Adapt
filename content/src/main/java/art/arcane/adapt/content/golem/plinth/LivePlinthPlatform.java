package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.text.Markup;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.world.BlockStore;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;


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
    return world.isLoaded() ? BlockStore.get(world, spot.x(), spot.y(), spot.z(), PlinthSlotCodec.slice()) : null;
  }

  @Override
  public void write(WorldRef world, PlinthSpot spot, PlinthSlotItem item) {
    if (item == null) {
      BlockStore.remove(world, spot.x(), spot.y(), spot.z(), PlinthSlotCodec.slice());
    } else {
      BlockStore.set(world, spot.x(), spot.y(), spot.z(), PlinthSlotCodec.slice(), item);
    }
  }

  @Override
  public Map<PlinthSpot, PlinthSlotItem> readChunk(WorldRef world, int chunkX, int chunkZ) {
    if (!world.isLoaded()) {
      return Map.of();
    }
    UUID worldId = world.uid();
    Map<PlinthSpot, PlinthSlotItem> slots = new HashMap<>();
    BlockStore.forEach(world, chunkX, chunkZ, PlinthSlotCodec.slice(),
        (int x, int y, int z, PlinthSlotItem item) -> slots.put(new PlinthSpot(worldId, x, y, z), item));
    return slots;
  }

  @Override
  public boolean savePlate(WorldRef world, int x, int z) {
    return BlockStore.save(world, x, z);
  }

  @Override
  public Id blockType(WorldRef world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4) || !AdaptApi.scheduler().owns(new Loc(world, x, y, z))) {
      return null;
    }
    return world.blockAt(x, y, z).type();
  }

  @Override
  public boolean runAt(Loc location, Runnable task, int delayTicks) {
    return delayTicks <= 0 ? AdaptApi.scheduler().runAt(location, task) : AdaptApi.scheduler().runAt(location, task, delayTicks);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, int delayTicks) {
    if (!AdaptApi.isRunning()) {
      return false;
    }
    return delayTicks <= 0 ? AdaptApi.scheduler().runEntity(entity, task) : AdaptApi.scheduler().runEntity(entity, task, delayTicks);
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      AdaptApi.scheduler().runAsync(task);
    } else {
      AdaptApi.scheduler().runAsync(task, delayTicks);
    }
  }

  @Override
  public boolean owns(Loc location) {
    return AdaptApi.scheduler().owns(location);
  }

  @Override
  public boolean owns(EntityRef entity) {
    return AdaptApi.scheduler().owns(entity);
  }

  @Override
  public boolean regionsHalted() {
    return AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().isTickThread();
  }

  @Override
  public WorldRef world(UUID id) {
    return !AdaptApi.isRunning() ? null : AdaptApi.platform().host().world(id);
  }

  @Override
  public ItemDisplayRef spawnDisplay(Loc at, ItemRef item) {
    ItemRef shown = item.copy();
    return at.world().spawnItemDisplay(at, shown, (ItemDisplayRef display) -> {
      display.setPersistent(false);
      Integrations.excludeFromStacking(display);
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
      inventory.setMainHand(AdaptApi.platform().host().items().empty());
    } else {
      held.setAmount(amount - 1);
      inventory.setMainHand(held);
    }
    return single;
  }

  @Override
  public void saveData(PlayerRef player) {
    if (AdaptApi.scheduler().owns(player)) {
      player.saveData();
    } else {
      AdaptApi.scheduler().runEntity(player, player::saveData);
    }
  }

  @Override
  public boolean isCrook(ItemRef item) {
    return GolemItems.kindOf(item).filter((GolemItemKind kind) -> kind == GolemItemKind.CROOK).isPresent();
  }

  @Override
  public void tell(PlayerRef player, boolean warning, Text message) {
    AdaptApi.scheduler().runEntity(player, () -> HudStatus.show(player, (warning ? TextFormat.RED : TextFormat.AQUA) + message.resolve(player)));
  }

  @Override
  public void denied(PlayerRef player) {
    AdaptApi.scheduler().runEntity(player, () -> player.playSound(player.location(), Sounds.ENTITY_VILLAGER_NO, SoundCategory.MASTER, DENY_VOLUME, DENY_PITCH));
  }

  @Override
  public String resultName(Infusion infusion) {
    ItemRef item = infusion.resultItem().get();
    String name = item.hasDisplayName() ? TextFormat.strip(Markup.legacy(item.displayName())) : item.type().legacyName();
    Optional<GolemRune> rune = GolemItems.runeOf(item);
    if (rune.isEmpty()) {
      return name;
    }
    return name + " (" + Text.key("adapt.golemancy.item.rune.tier").withMarkup("tier", String.valueOf(rune.get().tier())).resolve() + ")";
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
