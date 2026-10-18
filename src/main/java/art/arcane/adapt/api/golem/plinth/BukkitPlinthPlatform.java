package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class BukkitPlinthPlatform implements PlinthPlatform {
  private static final double POP_SPEED = 0.2D;
  private static final int STREAM_POINTS = 8;

  private final Supplier<PlinthHost> host;

  BukkitPlinthPlatform(Supplier<PlinthHost> host) {
    this.host = Objects.requireNonNull(host);
  }

  @Override
  public PlinthSlotItem read(World world, PlinthSpot spot) {
    return WorldData.loaded(world).map(data -> data.get(spot.x(), spot.y(), spot.z(), PlinthSlotItem.class)).orElse(null);
  }

  @Override
  public void write(World world, PlinthSpot spot, PlinthSlotItem item) {
    WorldData data = WorldData.loaded(world).orElseThrow(() -> new IllegalStateException("World " + world.getName() + " is no longer loaded"));
    if (item == null) {
      data.remove(spot.x(), spot.y(), spot.z(), PlinthSlotItem.class);
    } else {
      data.set(spot.x(), spot.y(), spot.z(), item);
    }
  }

  @Override
  public Map<PlinthSpot, PlinthSlotItem> readChunk(World world, int chunkX, int chunkZ) {
    Optional<WorldData> data = WorldData.loaded(world);
    if (data.isEmpty()) {
      return Map.of();
    }
    UUID worldId = world.getUID();
    Map<PlinthSpot, PlinthSlotItem> slots = new HashMap<>();
    data.get().iterateChunk(chunkX, chunkZ, PlinthSlotItem.class, (x, y, z, item) -> slots.put(new PlinthSpot(worldId, x, y, z), item));
    return slots;
  }

  @Override
  public boolean savePlate(World world, int x, int z) {
    return WorldData.loaded(world).map(data -> data.savePlate(x, z)).orElse(false);
  }

  @Override
  public Material blockType(World world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4) || !J.isOwnedByCurrentRegion(new Location(world, x, y, z))) {
      return null;
    }
    return world.getBlockAt(x, y, z).getType();
  }

  @Override
  public boolean runAt(Location location, Runnable task, int delayTicks) {
    return delayTicks <= 0 ? J.runAt(location, task) : J.runAt(location, task, delayTicks);
  }

  @Override
  public boolean runEntity(Entity entity, Runnable task, int delayTicks) {
    if (!Adapt.instance.isEnabled()) {
      return false;
    }
    return delayTicks <= 0 ? J.runEntity(entity, task) : J.runEntity(entity, task, delayTicks);
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      J.a(task);
    } else {
      J.a(task, delayTicks);
    }
  }

  @Override
  public boolean owns(Location location) {
    return J.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean owns(Entity entity) {
    return J.isOwnedByCurrentRegion(entity);
  }

  @Override
  public World world(UUID id) {
    return Bukkit.getWorld(id);
  }

  @Override
  public ItemDisplay spawnDisplay(Location at, ItemStack item) {
    ItemStack shown = item.clone();
    return PaperCompat.spawnHydrated(at.getWorld(), at, ItemDisplay.class, display -> {
      display.setPersistent(false);
      StackExclusion.exclude(display);
      display.setItemStack(shown);
      display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
      display.setBillboard(Display.Billboard.FIXED);
    });
  }

  @Override
  public void drop(Location at, ItemStack item) {
    at.getWorld().dropItemNaturally(at, item);
  }

  @Override
  public void popResult(Location at, ItemStack item) {
    Item dropped = at.getWorld().dropItem(at, item);
    dropped.setVelocity(new Vector(0D, POP_SPEED, 0D));
  }

  @Override
  public void give(Player player, ItemStack item) {
    for (ItemStack leftover : player.getInventory().addItem(item).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
  }

  @Override
  public ItemStack takeOne(Player player) {
    PlayerInventory inventory = player.getInventory();
    ItemStack held = inventory.getItemInMainHand();
    ItemStack single = held.clone();
    single.setAmount(1);
    int amount = held.getAmount();
    if (amount <= 1) {
      inventory.setItemInMainHand(null);
    } else {
      held.setAmount(amount - 1);
    }
    return single;
  }

  @Override
  public void saveData(Player player) {
    if (J.isOwnedByCurrentRegion(player)) {
      player.saveData();
    } else {
      J.runEntity(player, player::saveData);
    }
  }

  @Override
  public boolean isCrook(ItemStack item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }

  @Override
  public void tell(Player player, boolean warning, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, (warning ? C.RED : C.AQUA) + AdaptLanguage.text(key, arguments)));
  }

  @Override
  public void effect(PlinthEffect effect, Location at, double progress) {
    PlinthHost active = host.get();
    if (active == null) {
      return;
    }
    switch (effect) {
      case LOADED -> active.fx(at, FxPriority.GAMEPLAY)
          .burst(Particles.END_ROD, 6, 0.2D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.6F, 1.5F);
      case RETRIEVED -> active.fx(at, FxPriority.GAMEPLAY)
          .burst(Particles.END_ROD, 4, 0.2D)
          .sound(Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.2F);
      case STARTED -> active.fx(at, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 2D, 16, 0.6D)
          .sound(Sound.BLOCK_BEACON_ACTIVATE, 0.6F, 1.4F);
      case HUM -> active.fx(at, FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, 12, 0D, 1D, 0D, 0.6D, 0.4D)
          .sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 0.8F + (float) progress * 0.8F);
      case FIZZLE -> active.fx(at, FxPriority.AMBIENT)
          .burst(Particles.SMOKE, 10, 0.4D)
          .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.6F);
      case COMPLETED -> active.fx(at, FxPriority.TRANSITION)
          .burst(Particles.END_ROD, 24, 0.5D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F)
          .sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.5F, 1.6F);
    }
  }

  @Override
  public void stream(Location from, Location to) {
    PlinthHost active = host.get();
    if (active == null) {
      return;
    }
    active.fx(from, FxPriority.AMBIENT).line(Particles.REDSTONE, to.getX(), to.getY(), to.getZ(), STREAM_POINTS);
  }

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }
}
