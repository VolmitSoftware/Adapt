/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import com.jeff_media.customblockdata.CustomBlockData;
import com.jeff_media.customblockdata.events.CustomBlockDataMoveEvent;
import com.jeff_media.customblockdata.events.CustomBlockDataRemoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ArchitectElevator extends SimpleAdaptation<ArchitectElevator.Config> {
  private static final int ELEVATOR_LEVELS = 1;
  private static final NamespacedKey ELEVATOR_KEY = new NamespacedKey(Adapt.instance, "elevator");
  private static final NamespacedKey TARGET_DOWN = new NamespacedKey(Adapt.instance, "target_down");
  private static final NamespacedKey TARGET_UP = new NamespacedKey(Adapt.instance, "target_up");

  private final Map<UUID, Boolean> players = playerState();

  public ArchitectElevator() {
    super("architect-elevator");
    registerConfiguration(ArchitectElevator.Config.class);
    setMaxLevel(ELEVATOR_LEVELS);
    setIcon(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
    setInterval(988);

    registerRecipe(AdaptRecipe.shaped()
        .key("elevator")
        .shape("XXX")
        .shape("XYX")
        .shape("XXX")
        .ingredient(new MaterialChar('X', Tag.WOOL))
        .ingredient(new MaterialChar('Y', Material.ENDER_PEARL))
        .result(getElevatorItem())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WHITE_WOOL)
        .key("challenge_architect_elevator_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WHITE_WOOL)
            .key("challenge_architect_elevator_penthouse")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_elevator_100", "architect.elevator.trips", 100, 300);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  protected void onRuntimeActivated() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      withPlayerThread(player, () -> normalizeStoredLevel(player));
    }
  }

  private static boolean isElevator(Block b) {
    return b.getType() == Material.NOTE_BLOCK
        && CustomBlockData.hasCustomBlockData(b, Adapt.instance)
        && new CustomBlockData(b, Adapt.instance)
        .has(ELEVATOR_KEY, PersistentDataType.INTEGER);
  }

  private static boolean hasEnoughSpace(Player player, int targetY) {
    BoundingBox box = player.getBoundingBox()
        .shift(0, -player.getLocation().getY(), 0)
        .shift(0, targetY, 0);

    double maxX = Math.ceil(box.getMaxX());
    double maxY = Math.ceil(box.getMaxY());
    double maxZ = Math.ceil(box.getMaxZ());
    World world = player.getWorld();
    for (int x = (int) box.getMinX(); x <= maxX; x++) {
      for (int z = (int) box.getMinZ(); z <= maxZ; z++) {
        for (int y = (int) box.getMinY(); y <= maxY; y++) {
          Block block = world.getBlockAt(x, y, z);
          if (block.isPassable() || block.isLiquid())
            continue;
          VoxelShape shape = block.getCollisionShape();
          box.shift(-x, -y, -z);
          if (shape.overlaps(box))
            return false;
          box.shift(x, y, z);
        }
      }
    }
    return true;
  }

  @Override
  public void addStats(int level, Element v) {

  }

  public ItemStack getElevatorItem() {
    ItemStack elevatorItem = CustomModel.get(Material.NOTE_BLOCK, "architect", "elevator", "item")
        .toItemStack();
    ItemMeta meta = elevatorItem.getItemMeta();
    if (meta != null) {
      meta.getPersistentDataContainer().set(ELEVATOR_KEY, PersistentDataType.BYTE, (byte) 0);
      meta.setDisplayName(AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_NAME));
      meta.setLore(List.of(AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_USAGE1),
          AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_USAGE2),
          AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_USAGE3)));
      elevatorItem.setItemMeta(meta);
    }
    return elevatorItem;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) return;
    Player player = e.getPlayer();

    if (!players.isEmpty()) {
      UUID trackedId = player.getUniqueId();
      if (players.containsKey(trackedId)) {
        if (player.isOnGround() || player.isFlying() || findElevator(player) == null)
          players.remove(trackedId);
        return;
      }
    }

    if (e.getFrom().getY() >= e.getTo().getY() || player.isFlying() || player.getVelocity().getY() <= 0)
      return;

    Block block = findElevator(player);
    if (block == null) return;
    players.put(player.getUniqueId(), Boolean.TRUE);
    handleElevatorMovement(block, player, false);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    withPlayerThread(player, () -> normalizeStoredLevel(player));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent event) {
    if (!event.isSneaking() || event.getPlayer().isInsideVehicle()) return;
    Player player = event.getPlayer();
    Block block = findElevator(player);
    if (block == null) return;
    handleElevatorMovement(block, player, true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    ItemMeta meta = event.getItemInHand().getItemMeta();
    if (meta == null || !meta.getPersistentDataContainer().has(ELEVATOR_KEY, PersistentDataType.BYTE))
      return;
    int maxDistance = getMaxDistance(event.getPlayer());
    if (maxDistance <= 0) {
      event.setCancelled(true);
      return;
    }

    Block block = event.getBlock();
    World world = block.getWorld();
    CustomBlockData data = new CustomBlockData(block, Adapt.instance);
    data.set(ELEVATOR_KEY, PersistentDataType.INTEGER, maxDistance);

    int lowerDist = Math.min(block.getY() - world.getMinHeight(), maxDistance);
    for (int d = 1; d <= lowerDist; d++) {
      org.bukkit.block.Block lower = block.getRelative(BlockFace.DOWN, d);
      if (checkElevator(lower, TARGET_UP, d)) {
        data.set(TARGET_DOWN, PersistentDataType.INTEGER, d);
        break;
      }
    }

    int upperDist = upwardScanDistance(block.getY(), world.getMaxHeight(), maxDistance);
    for (int d = 1; d <= upperDist; d++) {
      org.bukkit.block.Block upper = block.getRelative(BlockFace.UP, d);
      if (checkElevator(upper, TARGET_DOWN, d)) {
        data.set(TARGET_UP, PersistentDataType.INTEGER, d);
        break;
      }
    }
  }

  public int getMaxDistance(Player player) {
    normalizeStoredLevel(player);
    int level = getActiveLevel(player);
    if (level == 0) return 0;
    Config config = getConfig();
    return config.baseDistance * (level * config.multiplier);
  }

  boolean normalizeStoredLevel(PlayerSkillLine line) {
    if (line == null || line.getAdaptationLevel(getName()) <= ELEVATOR_LEVELS) {
      return false;
    }
    line.setAdaptation(this, ELEVATOR_LEVELS);
    return true;
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(CustomBlockDataMoveEvent event) {
    if (!event.getCustomBlockData().has(ELEVATOR_KEY)) return;
    event.setCancelled(true);

    Event bukkit = event.getBukkitEvent();
    if (bukkit instanceof Cancellable cancellable) {
      cancellable.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockExplodeEvent event) {
    event.blockList().removeIf(ArchitectElevator::isElevator);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityExplodeEvent event) {
    event.blockList().removeIf(ArchitectElevator::isElevator);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(CustomBlockDataRemoveEvent event) {
    CustomBlockData data = event.getCustomBlockData();
    if (!data.has(ELEVATOR_KEY)) return;
    Event bukkit = event.getBukkitEvent();
    if (!(bukkit instanceof BlockBreakEvent breakEvent)) {
      if (bukkit instanceof Cancellable cancellable)
        cancellable.setCancelled(true);
      event.setCancelled(true);
      return;
    }

    breakEvent.setDropItems(false);
    Block block = event.getBlock();
    World world = block.getWorld();
    Location location = block.getLocation();
    world.dropItemNaturally(location, getElevatorItem());

    data.remove(ELEVATOR_KEY);
    int y = block.getY();
    int lowerY = data.getOrDefault(TARGET_DOWN, PersistentDataType.INTEGER, 0);
    int upperY = data.getOrDefault(TARGET_UP, PersistentDataType.INTEGER, 0);
    data.remove(TARGET_DOWN);
    data.remove(TARGET_UP);

    if (!isWithinBuildHeight(y - lowerY, world.getMinHeight(), world.getMaxHeight()))
      lowerY = 0;

    if (!isWithinBuildHeight(y + upperY, world.getMinHeight(), world.getMaxHeight()))
      upperY = 0;

    if (lowerY != 0 && upperY != 0) {
      Block lower = block.getRelative(BlockFace.DOWN, lowerY);
      Block upper = block.getRelative(BlockFace.UP, upperY);

      boolean lowerElevator = isElevator(lower);
      boolean upperElevator = isElevator(upper);

      if (lowerElevator && upperElevator) {
        CustomBlockData lowerData = new CustomBlockData(lower, Adapt.instance);
        CustomBlockData upperData = new CustomBlockData(upper, Adapt.instance);

        int dist = upperY + lowerY;
        int lowerDist = lowerData.getOrDefault(ELEVATOR_KEY, PersistentDataType.INTEGER, 0);
        int upperDist = upperData.getOrDefault(ELEVATOR_KEY, PersistentDataType.INTEGER, 0);
        int maxDistance = Math.max(upperDist, lowerDist);

        if (dist <= maxDistance) {
          lowerData.set(TARGET_UP, PersistentDataType.INTEGER, dist);
          upperData.set(TARGET_DOWN, PersistentDataType.INTEGER, dist);
        } else {
          lowerData.remove(TARGET_UP);
          upperData.remove(TARGET_DOWN);
        }
      } else if (lowerElevator) {
        new CustomBlockData(lower, Adapt.instance)
            .remove(TARGET_UP);
      } else if (upperElevator) {
        new CustomBlockData(upper, Adapt.instance)
            .remove(TARGET_DOWN);
      }
    } else if (lowerY != 0) {
      Block lower = block.getRelative(BlockFace.DOWN, lowerY);

      if (isElevator(lower)) {
        new CustomBlockData(lower, Adapt.instance)
            .remove(TARGET_UP);
      }
    } else if (upperY != 0) {
      Block upper = block.getRelative(BlockFace.UP, upperY);

      if (isElevator(upper)) {
        new CustomBlockData(upper, Adapt.instance)
            .remove(TARGET_DOWN);
      }
    }
  }

  @Nullable
  private Block findElevator(Player player) {
    Block base = player.getLocation().getBlock();
    for (int d = 1; d <= 2; d++) {
      Block rel = base.getRelative(BlockFace.DOWN, d);
      if (isElevator(rel))
        return rel;
    }
    return null;
  }

  private boolean checkElevator(Block block, NamespacedKey key, int source) {
    if (!isElevator(block))
      return false;

    new CustomBlockData(block, Adapt.instance)
        .set(key, PersistentDataType.INTEGER, source);
    return true;
  }

  private void normalizeStoredLevel(Player player) {
    AdaptPlayer adaptPlayer = getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }
    PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(getSkill().getName());
    normalizeStoredLevel(line);
  }

  private void handleElevatorMovement(Block block, Player player, boolean down) {
    if (!isElevator(block) || player.isInsideVehicle())
      return;

    CustomBlockData data = new CustomBlockData(block, Adapt.instance);
    int distance = data.getOrDefault(down ? TARGET_DOWN : TARGET_UP, PersistentDataType.INTEGER, 0);
    if (distance == 0)
      return;
    int targetY = block.getY() + (down ? -distance : distance);
    if (!isWithinBuildHeight(targetY, block.getWorld().getMinHeight(), block.getWorld().getMaxHeight()))
      return;

    Block target = block.getRelative(down ? BlockFace.DOWN : BlockFace.UP, distance);
    if (!isElevator(target))
      return;

    org.bukkit.Location loc = player.getLocation();
    loc.setY(target.getY() + 1);

    if (!isWithinBuildHeight(loc.getBlockY(), block.getWorld().getMinHeight(), block.getWorld().getMaxHeight()))
      return;

    if (!hasEnoughSpace(player, loc.getBlockY()))
      return;

    teleportPlayer(player, loc, distance);
  }

  private void teleportPlayer(Player p, Location l, int distance) {
    fx(p.getLocation(), FxPriority.TRANSITION)
        .column(Particle.PORTAL, 12, 2.0D)
        .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.0f);

    CompletableFuture<Boolean> teleport;
    try {
      teleport = PaperCompat.teleportAsync(p, l, PlayerTeleportEvent.TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      Adapt.error("Architect Elevator could not start a teleport for " + p.getUniqueId() + ".");
      Adapt.error(error);
      return;
    }

    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) -> finishElevatorTeleport(p, distance, success, failure));
  }

  private void finishElevatorTeleport(Player p, int distance, Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error("Architect Elevator teleport failed for " + p.getUniqueId() + ".");
      Adapt.error(failure);
    }

    J.runEntity(p, () -> {
      if (!shouldRewardTeleport(success, failure, p.isOnline())) {
        return;
      }

      Location arrival = p.getLocation().clone();
      fx(arrival, FxPriority.TRANSITION)
          .helix(Particle.PORTAL, 0.8D, 2.5D, 16, 0)
          .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f,
              Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.5f);
      addStat(p, "architect.elevator.trips", 1);
      if (distance >= 50 && grantOnce(p, "challenge_architect_elevator_penthouse")) {
        FxPresets.learnCelebration(this, p);
        fx(arrival, FxPriority.TRANSITION)
            .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
      }
    });
  }

  static boolean shouldRewardTeleport(Boolean success, Throwable failure, boolean online) {
    return online && failure == null && Boolean.TRUE.equals(success);
  }

  static int upwardScanDistance(int blockY, int maxHeight, int maxDistance) {
    return Math.max(0, Math.min(maxHeight - 1 - blockY, Math.max(0, maxDistance)));
  }

  static boolean isWithinBuildHeight(int y, int minHeight, int maxHeight) {
    return y >= minHeight && y < maxHeight;
  }


  @ConfigDescription("Build wool elevators to teleport vertically.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Distance for the Architect Elevator adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseDistance = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Multiplier for the Architect Elevator adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int multiplier = 1;

    public Config() {
      baseCost = 5;
      costFactor = 0.40;
      initialCost = 1;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      maxLevel = ELEVATOR_LEVELS;
    }
  }
}
