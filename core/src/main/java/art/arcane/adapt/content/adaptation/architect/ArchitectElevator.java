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

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockFadeEvent;
import art.arcane.adapt.platform.event.block.BlockFertilizeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.StructureGrowEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectElevator extends SimpleAdaptation<ArchitectElevator.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> ASCENT = CommonPreferences.toggle("ascent", ArchitectMessages.ARCHITECTELEVATOR_PREFERENCE_ASCENT, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DESCENT = CommonPreferences.toggle("descent", ArchitectMessages.ARCHITECTELEVATOR_PREFERENCE_DESCENT, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK_ASCENT = CommonPreferences.toggle("sneak-ascent", ArchitectMessages.ARCHITECTELEVATOR_PREFERENCE_SNEAK_ASCENT, CommonPreferences.Toggle.OFF);

  private static final int ELEVATOR_LEVELS = 1;
  private static final Id NOTE_BLOCK = Id.minecraft("note_block");
  private static final Id FIRE = Id.minecraft("fire");
  private static final DataKey<Byte> ELEVATOR_ITEM_KEY = DataKey.adapt("elevator", DataCodecs.BYTE);
  private static final DataKey<Integer> ELEVATOR_KEY = DataKey.adapt("elevator", DataCodecs.INT);
  private static final DataKey<Integer> TARGET_DOWN = DataKey.adapt("target_down", DataCodecs.INT);
  private static final DataKey<Integer> TARGET_UP = DataKey.adapt("target_up", DataCodecs.INT);

  private static volatile ArchitectElevator active;

  private final Map<UUID, Boolean> players = playerState();
  private final Map<Long, BrokenElevator> pendingBreaks = new ConcurrentHashMap<>();

  public ArchitectElevator() {
    super("architect-elevator");
    registerConfiguration(ArchitectElevator.Config.class);
    setMaxLevel(ELEVATOR_LEVELS);
    setIcon(Id.minecraft("heavy_weighted_pressure_plate"));
    setInterval(988);

    registerRecipe(new RecipeSpec.Shaped(Id.adapt("elevator"),
        List.of("XXX", "XYX", "XXX"),
        Map.of('X', new Ingredient.Tag(Id.minecraft("wool")), 'Y', Ingredient.of(Id.minecraft("ender_pearl"))),
        getElevatorItem()));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("white_wool"))
        .key("challenge_architect_elevator_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("white_wool"))
            .key("challenge_architect_elevator_penthouse")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_elevator_100", "architect.elevator.trips", 100, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, ASCENT, DESCENT, SNEAK_ASCENT);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  protected void onRuntimeActivated() {
    active = this;
  }

  @Override
  public void unregister() {
    if (active == this) {
      active = null;
    }
    super.unregister();
  }

  private static boolean isElevator(BlockRef b) {
    return NOTE_BLOCK.equals(b.type())
        && b.hasData()
        && b.data().has(ELEVATOR_KEY);
  }

  private static boolean hasElevatorData(BlockRef b) {
    return b.hasData() && b.data().hasAny(ELEVATOR_KEY.id());
  }

  private static boolean hasEnoughSpace(PlayerRef player, int targetY) {
    Box box = player.boundingBox()
        .shift(0, -player.location().y(), 0)
        .shift(0, targetY, 0);

    double maxX = Math.ceil(box.maxX());
    double maxY = Math.ceil(box.maxY());
    double maxZ = Math.ceil(box.maxZ());
    WorldRef world = player.world();
    for (int x = (int) box.minX(); x <= maxX; x++) {
      for (int z = (int) box.minZ(); z <= maxZ; z++) {
        for (int y = (int) box.minY(); y <= maxY; y++) {
          BlockRef block = world.blockAt(x, y, z);
          if (block.isPassable() || block.isLiquid()) {
            continue;
          }
          for (Box shape : block.collisionBoxes()) {
            if (shape.overlaps(box)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public void addStats(int level, StatLines v) {

  }

  public ItemRef getElevatorItem() {
    ItemRef elevatorItem = CustomModel.get(NOTE_BLOCK, "architect", "elevator", "item").toItemStack();
    elevatorItem.data().set(ELEVATOR_ITEM_KEY, (byte) 0);
    elevatorItem.setDisplayName(AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_NAME));
    elevatorItem.setLore(List.of(AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_USAGE1),
        AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_USAGE2),
        AdaptLanguage.text(ItemsMessages.ELEVATOR_BLOCK_USAGE3)));
    return elevatorItem;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) return;
    PlayerRef player = e.player();

    if (!players.isEmpty()) {
      UUID trackedId = player.uuid();
      if (players.containsKey(trackedId)) {
        if (player.isOnGround() || player.isFlying() || findElevator(player) == null)
          players.remove(trackedId);
        return;
      }
    }

    if (e.from().y() >= e.to().y() || player.isFlying() || player.velocity().y() <= 0)
      return;

    BlockRef block = findElevator(player);
    if (block == null) return;
    players.put(player.uuid(), Boolean.TRUE);
    handleElevatorMovement(block, player, false);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent event) {
    if (!event.isSneaking() || event.player().vehicle() != null) return;
    PlayerRef player = event.player();
    BlockRef block = findElevator(player);
    if (block == null) return;
    handleElevatorMovement(block, player, true);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPlaceEvent event) {
    if (!event.itemInHand().data().has(ELEVATOR_ITEM_KEY)) {
      return;
    }
    int maxDistance = getMaxDistance(event.player());
    if (maxDistance <= 0) {
      event.setCancelled(true);
      return;
    }

    BlockRef block = event.block();
    WorldRef world = block.world();
    DataHolder data = block.data();
    data.set(ELEVATOR_KEY, maxDistance);

    int lowerDist = Math.min(block.y() - world.minHeight(), maxDistance);
    for (int d = 1; d <= lowerDist; d++) {
      BlockRef lower = block.relative(BlockFace.DOWN, d);
      if (checkElevator(lower, TARGET_UP, d)) {
        data.set(TARGET_DOWN, d);
        break;
      }
    }

    int upperDist = upwardScanDistance(block.y(), world.maxHeight(), maxDistance);
    for (int d = 1; d <= upperDist; d++) {
      BlockRef upper = block.relative(BlockFace.UP, d);
      if (checkElevator(upper, TARGET_DOWN, d)) {
        data.set(TARGET_UP, d);
        break;
      }
    }
  }

  public int getMaxDistance(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level == 0) return 0;
    Config config = getConfig();
    return config.baseDistance * (level * config.multiplier);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(BlockExplodeEvent event) {
    event.blocks().removeIf(ArchitectElevator::isElevator);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityExplodeEvent event) {
    event.blocks().removeIf(ArchitectElevator::isElevator);
  }

  private static boolean touchesElevatorData(List<BlockRef> blocks) {
    for (BlockRef block : blocks) {
      if (hasElevatorData(block)) {
        return true;
      }
    }
    return false;
  }

  private static boolean touchesElevator(List<BlockChange> changes) {
    for (BlockChange change : changes) {
      if (hasElevatorData(change.block())) {
        return true;
      }
    }
    return false;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void onBreaking(BlockBreakEvent event) {
    BlockRef block = event.block();
    if (!hasElevatorData(block)) {
      return;
    }
    DataHolder data = block.data();
    pendingBreaks.put(event.dispatchId(), new BrokenElevator(
        data.getOrDefault(TARGET_DOWN, 0), data.getOrDefault(TARGET_UP, 0)));
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent breakEvent) {
    BrokenElevator broken = pendingBreaks.remove(breakEvent.dispatchId());
    if (broken == null || breakEvent.isCancelled()) {
      return;
    }

    breakEvent.setDropItems(false);
    BlockRef block = breakEvent.block();
    WorldRef world = block.world();
    world.dropItemNaturally(block.location().position(), getElevatorItem());

    DataHolder data = block.data();
    data.remove(ELEVATOR_KEY);
    int y = block.y();
    int lowerY = broken.lowerY();
    int upperY = broken.upperY();
    data.remove(TARGET_DOWN);
    data.remove(TARGET_UP);

    if (!isWithinBuildHeight(y - lowerY, world.minHeight(), world.maxHeight()))
      lowerY = 0;

    if (!isWithinBuildHeight(y + upperY, world.minHeight(), world.maxHeight()))
      upperY = 0;

    if (lowerY != 0 && upperY != 0) {
      BlockRef lower = block.relative(BlockFace.DOWN, lowerY);
      BlockRef upper = block.relative(BlockFace.UP, upperY);

      boolean lowerElevator = isElevator(lower);
      boolean upperElevator = isElevator(upper);

      if (lowerElevator && upperElevator) {
        DataHolder lowerData = lower.data();
        DataHolder upperData = upper.data();

        int dist = upperY + lowerY;
        int lowerDist = lowerData.getOrDefault(ELEVATOR_KEY, 0);
        int upperDist = upperData.getOrDefault(ELEVATOR_KEY, 0);
        int maxDistance = Math.max(upperDist, lowerDist);

        if (dist <= maxDistance) {
          lowerData.set(TARGET_UP, dist);
          upperData.set(TARGET_DOWN, dist);
        } else {
          lowerData.remove(TARGET_UP);
          upperData.remove(TARGET_DOWN);
        }
      } else if (lowerElevator) {
        lower.data().remove(TARGET_UP);
      } else if (upperElevator) {
        upper.data().remove(TARGET_DOWN);
      }
    } else if (lowerY != 0) {
      BlockRef lower = block.relative(BlockFace.DOWN, lowerY);

      if (isElevator(lower)) {
        lower.data().remove(TARGET_UP);
      }
    } else if (upperY != 0) {
      BlockRef upper = block.relative(BlockFace.UP, upperY);

      if (isElevator(upper)) {
        upper.data().remove(TARGET_DOWN);
      }
    }
  }

  private BlockRef findElevator(PlayerRef player) {
    BlockRef base = player.world().blockAt(player.location());
    for (int d = 1; d <= 2; d++) {
      BlockRef rel = base.relative(BlockFace.DOWN, d);
      if (isElevator(rel))
        return rel;
    }
    return null;
  }

  private boolean checkElevator(BlockRef block, DataKey<Integer> key, int source) {
    if (!isElevator(block))
      return false;

    block.data().set(key, source);
    return true;
  }

  private void handleElevatorMovement(BlockRef block, PlayerRef player, boolean down) {
    if (!hasActiveAdaptation(player) || !preferenceEnabled(player, down ? DESCENT : ASCENT)
        || (!down && preferenceEnabled(player, SNEAK_ASCENT) && !player.isSneaking())
        || !isElevator(block) || player.vehicle() != null)
      return;

    DataHolder data = block.data();
    int distance = data.getOrDefault(down ? TARGET_DOWN : TARGET_UP, 0);
    if (distance == 0)
      return;
    int targetY = block.y() + (down ? -distance : distance);
    if (!isWithinBuildHeight(targetY, block.world().minHeight(), block.world().maxHeight()))
      return;

    BlockRef target = block.relative(down ? BlockFace.DOWN : BlockFace.UP, distance);
    if (!isElevator(target))
      return;

    Loc loc = player.location().withY(target.y() + 1);

    if (!isWithinBuildHeight(loc.blockY(), block.world().minHeight(), block.world().maxHeight()))
      return;

    if (!hasEnoughSpace(player, loc.blockY()))
      return;

    teleportPlayer(player, loc, distance);
  }

  private void teleportPlayer(PlayerRef p, Loc l, int distance) {
    fx(p.location(), FxPriority.TRANSITION)
        .column(Particles.PORTAL, 12, 2.0D)
        .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.0f);

    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(l, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error("Architect Elevator could not start a teleport for " + p.uuid() + ".");
      AdaptLog.error(error);
      return;
    }

    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) -> finishElevatorTeleport(p, distance, success, failure));
  }

  private void finishElevatorTeleport(PlayerRef p, int distance, Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error("Architect Elevator teleport failed for " + p.uuid() + ".");
      AdaptLog.error(failure);
    }

    Tasks.runEntity(p, () -> {
      if (!shouldRewardTeleport(success, failure, p.isOnline())) {
        return;
      }

      awardUsageBaselineXp(p, getLevel(p));
      Loc arrival = p.location();
      fx(arrival, FxPriority.TRANSITION)
          .helix(Particles.PORTAL, 0.8D, 2.5D, 16, 0)
          .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f,
              Sounds.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.5f);
      addStat(p, "architect.elevator.trips", 1);
      if (distance >= 50 && grantOnce(p, "challenge_architect_elevator_penthouse")) {
        FxPresets.learnCelebration(this, p);
        fx(arrival, FxPriority.TRANSITION)
            .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
      }
    });
  }

  private record BrokenElevator(int lowerY, int upperY) {
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
      baseCost = 3;
      costFactor = 1;
      maxLevel = ELEVATOR_LEVELS;
      initialCost = 4;
    }
  }

  /**
   * Keeps elevator blocks from being moved, burnt, faded, grown over, blown up or replaced while the adaptation is
   * active. Registered once after the skills, so it cancels at MONITOR after every skill's MONITOR handler has run and
   * before the services registered later, where the block-data library listener did it before.
   */
  public static final class BlockGuard {
    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockPistonEvent event) {
      if (active == null) {
        return;
      }
      for (BlockRef block : event.blocks()) {
        if (hasElevatorData(block)) {
          event.setCancelled(true);
          return;
        }
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockExplodeEvent event) {
      if (active != null && touchesElevatorData(event.blocks())) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(EntityExplodeEvent event) {
      if (active != null && touchesElevatorData(event.blocks())) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockBurnEvent event) {
      if (active != null && hasElevatorData(event.block())) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockFadeEvent event) {
      BlockRef block = event.block();
      if (active != null && !FIRE.equals(block.type()) && !block.type().equals(event.newState().type()) && hasElevatorData(block)) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(EntityChangeBlockEvent event) {
      BlockRef block = event.block();
      if (active != null && !block.type().equals(event.to().type()) && hasElevatorData(block)) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(StructureGrowEvent event) {
      if (active != null && touchesElevator(event.blocks())) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockFertilizeEvent event) {
      if (active != null && touchesElevator(event.blocks())) {
        event.setCancelled(true);
      }
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent event) {
      if (active != null && !event.itemInHand().data().has(ELEVATOR_ITEM_KEY) && hasElevatorData(event.block())) {
        event.setCancelled(true);
      }
    }
  }
}
