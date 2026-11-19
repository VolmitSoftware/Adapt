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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgilityWallJump extends SimpleAdaptation<AgilityWallJump.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", AgilityMessages.AGILITYWALLJUMP_PREFERENCE_CONTROL, Control.HOLD, List.of(
          new PlayerPreference.Choice<>(Control.HOLD, AgilityMessages.AGILITYWALLJUMP_PREFERENCE_CONTROL_HOLD, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.TAP, AgilityMessages.AGILITYWALLJUMP_PREFERENCE_CONTROL_TAP, Id.minecraft("feather"), 1))));

  private static final String SLOT_LATCH = "latch";
  private static final double GRAVITY_CANCEL = -1D;
  private static final int[][] WALL_OFFSETS = {
      {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0},
      {1, 0, -1}, {1, 0, 1}, {-1, 0, -1}, {-1, 0, 1},
      {1, 1, -1}, {1, 1, 1}, {-1, 1, -1}, {-1, 1, 1},
      {0, 1, -1}, {0, 1, 1}, {1, 1, 0}, {-1, 1, 0}
  };
  private final Map<UUID, Double> airjumps = playerState();
  private final Map<UUID, Vec3> horizontalIntent = playerState();
  private final Map<UUID, Long> horizontalIntentTime = playerState();
  private final Map<UUID, Boolean> sneakState = playerState();
  private final Map<UUID, BlockRef> latchedWalls = playerState();

  public AgilityWallJump() {
    super("agility-wall-jump");
    needs(PlatformNeed.attribute(Attributes.GRAVITY));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("vine"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ladder"))
        .key("challenge_agility_wall_jump_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("feather"))
        .key("challenge_agility_parkour_master")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_agility_wall_jump_500", "agility.wall-jump.air-jumps", 500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    releaseLatch(player.getPlayer());
    sneakState.remove(player.getPlayer().uuid());
  }

  @Override
  public void unregister() {
    super.unregister();
    airjumps.clear();
    horizontalIntent.clear();
    horizontalIntentTime.clear();
    sneakState.clear();
    latchedWalls.clear();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxJumps(level), 1);
    statLore(v, Form.pc(getJumpHeight(level), 0), 2);
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (preference(p, CONTROL) == Control.TAP) {
      if (e.isSneaking()) {
        if (latchedWalls.containsKey(id)) {
          releaseJump(p);
          sneakState.put(id, false);
        } else {
          sneakState.put(id, true);
          updatePlayer(p);
        }
      }
      return;
    }
    sneakState.put(id, e.isSneaking());
    if (shouldReleaseJump(e.isSneaking(), latchedWalls.containsKey(id))) {
      releaseJump(p);
      return;
    }
    updatePlayer(p);
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    clearPlayerState(e.player());
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      clearPlayerState(e.player());
    }
  }

  private int getMaxJumps(int level) {
    return (int) (level + (level / getConfig().maxJumpsLevelBonusDivisor));
  }

  private double getJumpHeight(int level) {
    return getConfig().jumpHeightBase + (getLevelPercent(level) * getConfig().jumpHeightBonusLevelMultiplier);
  }

  @Handle
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    boolean sneaking = preference(p, CONTROL) == Control.TAP ? sneakState.getOrDefault(p.uuid(), false) : p.isSneaking();
    if (!sneaking && airjumps.isEmpty() && latchedWalls.isEmpty()) {
      return;
    }

    UUID id = p.uuid();
    if (!sneaking && !airjumps.containsKey(id) && !latchedWalls.containsKey(id)) {
      return;
    }
    sneakState.put(id, sneaking);
    if (airjumps.containsKey(id)) {
      if (p.isOnGround() && !blockAt(p.location()).relative(BlockFace.DOWN).isAir()) {
        airjumps.remove(id);
      }
    }

    Loc from = e.from();
    Loc to = e.to();
    if (to != null && from.world() != null && to.world() != null && from.sameWorld(to)) {
      double dx = to.x() - from.x();
      double dz = to.z() - from.z();
      double movementThresholdSq = getConfig().inputMovementThreshold * getConfig().inputMovementThreshold;
      if ((dx * dx) + (dz * dz) >= movementThresholdSq) {
        horizontalIntent.put(id, new Vec3(dx, 0D, dz).normalize());
        horizontalIntentTime.put(id, M.ms());
      }
    }

    updatePlayer(p);
  }

  private void updatePlayer(PlayerRef p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.uuid();
    if (!airjumps.containsKey(id) && !latchedWalls.containsKey(id) && !isSneaking(id, p)) {
      return;
    }
    int level = getActiveInteractLevel(p, p.location());
    if (level <= 0) {
      horizontalIntent.remove(id);
      horizontalIntentTime.remove(id);
      sneakState.remove(id);
      releaseLatch(p);
      return;
    }

    Double j = airjumps.get(id);

    if (j != null && j - 0.25 >= getMaxJumps(level)) {
      if (releaseLatch(p)) {
        fx(p.location(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 4, 0.15D)
            .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.4F);
      }
      return;
    }

    if (p.isOnGround()) {
      airjumps.remove(id);
      releaseLatch(p);
      return;
    }

    BlockRef stickBlock = stickToWall(p);
    if (p.isFlying() || !isSneaking(id, p)) {
      if (releaseLatch(p)) {
        sfx(p.location(), Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1F, 0.439F);
      }
      return;
    }

    if (stickBlock == null) {
      releaseLatch(p);
      return;
    }

    if (!latchedWalls.containsKey(id)) {
      Loc location = p.location();
      double dx = (stickBlock.x() + 0.5D) - location.x();
      double dz = (stickBlock.z() + 0.5D) - location.z();
      double len = Math.sqrt((dx * dx) + (dz * dz));
      Loc contact = len < 1.0E-4D
          ? location.add(0, 0.3D, 0)
          : location.add((dx / len) * 0.35D, 0.3D, (dz / len) * 0.35D);
      fx(contact, FxPriority.COMBAT)
          .particle(Particles.blockCrack(stickBlock.state()), 8, 0, 0.2D, 0, 0.25D, 0.05D)
          .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1F, 0.89F, Sounds.ITEM_ARMOR_EQUIP_CHAIN, 1F, 1.39F, Sounds.BLOCK_LADDER_STEP, 0.4F, 0.8F);
    }

    applyWallStickForce(p, stickBlock);
    latchedWalls.put(id, stickBlock);
    clearLatchedFallDistance(p);
    AdaptAttributeService.get().apply(p, getName(), SLOT_LATCH, Attributes.GRAVITY, GRAVITY_CANCEL, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    Vec3 c = p.velocity();
    p.setVelocity(c.withY((c.y() * 0.35) - 0.0025));
    Double vv = airjumps.get(id);
    vv = vv == null ? 0 : vv;
    vv += 0.0127;
    airjumps.put(id, vv);
    if (M.r(0.25)) {
      fx(p.location(), FxPriority.TRAIL)
          .particle(Particles.blockCrack(stickBlock.state()), 1, 0, 0, 0, 0.05D, 0.05D);
    }
  }

  private void releaseJump(PlayerRef p) {
    UUID id = p.uuid();
    int level = getActiveInteractLevel(p, p.location());
    BlockRef stickBlock = latchedWalls.get(id);
    if (stickBlock == null) {
      stickBlock = stickToWall(p);
    }
    double currentJumps = airjumps.getOrDefault(id, 0D);
    if (level <= 0 || stickBlock == null || currentJumps - 0.25D >= getMaxJumps(level)) {
      releaseLatch(p);
      return;
    }

    double nextJumps = currentJumps + 1D;
    if (nextJumps - 0.25D > getMaxJumps(level)) {
      releaseLatch(p);
      return;
    }

    releaseLatch(p);
    Vec3 launch = p.velocity().withY(getJumpHeight(level));
    if (isBackwardLaunch(p)) {
      Vec3 direction = p.location().direction().withY(0);
      if (direction.lengthSquared() > 0.000001D) {
        direction = direction.normalize().multiply(-getConfig().backwardPushSpeed);
        launch = new Vec3(direction.x(), launch.y(), direction.z());
      }
    }
    p.setVelocity(launch);
    airjumps.put(id, nextJumps);

    int jumpCount = (int) Math.floor(nextJumps);
    Loc location = p.location();
    double awayX = location.x() - (stickBlock.x() + 0.5D);
    double awayZ = location.z() - (stickBlock.z() + 0.5D);
    int cloudCount = Math.max(2, Math.min(6, getMaxJumps(level) - jumpCount + 1));
    float kickPitch = (float) Math.min(2.0D, 0.8D + (jumpCount * 0.12D));
    fx(location, FxPriority.GAMEPLAY)
        .trail(Particles.CLOUD, awayX, 0.2D, awayZ, 0.8D, cloudCount)
        .particle(Particles.blockCrack(stickBlock.state()), 3, 0, 0.3D, 0, 0.15D, 0.05D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.9F, kickPitch);
    addStat(p, "agility.wall-jump.air-jumps", 1);
    if (nextJumps >= 5D && AdaptConfig.get().isAdvancements()
        && !getPlayer(p).getData().isGranted("challenge_agility_parkour_master")) {
      getPlayer(p).getAdvancementHandler().grant("challenge_agility_parkour_master");
    }
  }

  private boolean isBackwardLaunch(PlayerRef p) {
    UUID id = p.uuid();
    Long at = horizontalIntentTime.get(id);
    Vec3 intent = horizontalIntent.get(id);
    if (at == null || intent == null || M.ms() - at > getConfig().inputWindowMs) {
      return false;
    }

    Vec3 facing = p.location().direction().withY(0);
    if (facing.lengthSquared() <= 0.000001) {
      return false;
    }

    facing = facing.normalize();
    return intent.dot(facing) <= -Math.abs(getConfig().backwardIntentDotThreshold);
  }

  private boolean isSneaking(UUID id, PlayerRef p) {
    Boolean cached = sneakState.get(id);
    return cached != null ? cached : p.isSneaking();
  }

  static boolean shouldReleaseJump(boolean sneaking, boolean latched) {
    return !sneaking && latched;
  }

  static void clearLatchedFallDistance(PlayerRef p) {
    p.setFallDistance(0F);
  }

  private void clearPlayerState(PlayerRef p) {
    UUID id = p.uuid();
    airjumps.remove(id);
    horizontalIntent.remove(id);
    horizontalIntentTime.remove(id);
    sneakState.remove(id);
    releaseLatch(p);
  }

  private boolean releaseLatch(PlayerRef p) {
    if (latchedWalls.remove(p.uuid()) == null) {
      return false;
    }

    clearLatchedFallDistance(p);
    AdaptAttributeService.get().remove(p, getName(), SLOT_LATCH, Attributes.GRAVITY);
    return true;
  }

  private BlockRef stickToWall(PlayerRef p) {
    for (BlockRef wall : getBlocks(p)) {
      if (wall.isSolid()) {
        return wall;
      }
    }

    return null;
  }

  private void applyWallStickForce(PlayerRef p, BlockRef wall) {
    Vec3 velocity = p.velocity();
    Loc location = p.location();
    double shiftX = location.x() - (wall.x() + 0.5D);
    double shiftZ = location.z() - (wall.z() + 0.5D);
    p.setVelocity(new Vec3(velocity.x() - (shiftX / 16), velocity.y(), velocity.z() - (shiftZ / 16)));
  }

  private BlockRef[] getBlocks(PlayerRef p) {
    BlockRef base = blockAt(p.location());
    BlockRef[] blocks = new BlockRef[WALL_OFFSETS.length];
    for (int index = 0; index < WALL_OFFSETS.length; index++) {
      int[] offset = WALL_OFFSETS[index];
      blocks[index] = base.relative(offset[0], offset[1], offset[2]);
    }
    return blocks;
  }

  private static BlockRef blockAt(Loc location) {
    return location.world().blockAt(location);
  }

  @ConfigDescription("Hold shift while mid-air against a wall to latch, then release shift to jump.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Jumps Level Bonus Divisor for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxJumpsLevelBonusDivisor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Height Base for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpHeightBase = 0.625;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Height Bonus Level Multiplier for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpHeightBonusLevelMultiplier = 0.225;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Backward Push Speed for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double backwardPushSpeed = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Backward Intent Dot Threshold for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double backwardIntentDotThreshold = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Input Movement Threshold for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double inputMovementThreshold = 0.0025;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Input Window Ms for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long inputWindowMs = 450;

    public Config() {
      baseCost = 2;
      costFactor = 0.65;
      initialCost = 8;
    }
  }

  public enum Control { HOLD, TAP }
}
