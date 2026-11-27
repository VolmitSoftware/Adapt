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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgilityWallJump extends SimpleAdaptation<AgilityWallJump.Config> {
  public static final Id ID = Id.adapt("agility/wall_jump");
  public static final PlayerPreference<Control> CONTROL = PlayerPreference.of("control", Control.class, Control.HOLD, List.of(
          new PlayerPreference.Choice<>(Control.HOLD, Text.key("adapt.agilitywalljump.preferences.preference_control_hold"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.TAP, Text.key("adapt.agilitywalljump.preferences.preference_control_tap"), Id.minecraft("feather"), 1))).labelled(Text.key("adapt.agilitywalljump.preferences.preference_control"));

  private final Map<UUID, Double> airjumps = playerState();
  private final Map<UUID, Vec3> horizontalIntent = playerState();
  private final Map<UUID, Long> horizontalIntentTime = playerState();
  private final Map<UUID, Boolean> sneakState = playerState();
  private final Map<UUID, BlockRef> latchedWalls = playerState();
  private final AgilityWallLatch latch = new AgilityWallLatch(this, latchedWalls);

  public AgilityWallJump() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.GRAVITY));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("vine"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ladder"))
        .key("challenge_agility_wall_jump_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("feather"))
        .key("challenge_agility_parkour_master")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_agility_wall_jump_500", "agility.wall-jump.air-jumps", 500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    latch.release(player.player());
    sneakState.remove(player.player().uuid());
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
    statLore(v, Format.pc(getJumpHeight(level), 0), 2);
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (preference(p, CONTROL) == Control.TAP) {
      if (e.isSneaking()) {
        if (latch.latched(id)) {
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
    if (shouldReleaseJump(e.isSneaking(), latch.latched(id))) {
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
    if (!sneaking && airjumps.isEmpty() && !latch.anyLatched()) {
      return;
    }

    UUID id = p.uuid();
    if (!sneaking && !airjumps.containsKey(id) && !latch.latched(id)) {
      return;
    }
    sneakState.put(id, sneaking);
    if (airjumps.containsKey(id)) {
      if (p.isOnGround() && !blockAt(p.location()).relative(BlockFace.DOWN).isAir()) {
        clearJumps(id);
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
        horizontalIntentTime.put(id, System.currentTimeMillis());
      }
    }

    updatePlayer(p);
  }

  private void updatePlayer(PlayerRef p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.uuid();
    if (!airjumps.containsKey(id) && !latch.latched(id) && !isSneaking(id, p)) {
      return;
    }
    int level = getActiveInteractLevel(p, p.location());
    if (level <= 0) {
      horizontalIntent.remove(id);
      horizontalIntentTime.remove(id);
      sneakState.remove(id);
      latch.release(p);
      return;
    }

    Double j = airjumps.get(id);

    if (j != null && j - 0.25 >= getMaxJumps(level)) {
      if (latch.release(p)) {
        fx(p.location(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 4, 0.15D)
            .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.4F);
      }
      return;
    }

    if (p.isOnGround()) {
      clearJumps(id);
      latch.release(p);
      return;
    }

    BlockRef stickBlock = latch.findWall(p);
    if (p.isFlying() || !isSneaking(id, p)) {
      if (latch.release(p)) {
        sfx(p.location(), Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1F, 0.439F);
      }
      return;
    }

    if (stickBlock == null) {
      latch.release(p);
      return;
    }

    if (latch.hold(p, stickBlock)) {
      publishJumps(id, level, airjumps.getOrDefault(id, 0D));
    }
    Double vv = airjumps.get(id);
    vv = vv == null ? 0 : vv;
    vv += 0.0127;
    airjumps.put(id, vv);
  }

  private void releaseJump(PlayerRef p) {
    UUID id = p.uuid();
    int level = getActiveInteractLevel(p, p.location());
    BlockRef stickBlock = latch.wall(id);
    if (stickBlock == null) {
      stickBlock = latch.findWall(p);
    }
    double currentJumps = airjumps.getOrDefault(id, 0D);
    if (level <= 0 || stickBlock == null || currentJumps - 0.25D >= getMaxJumps(level)) {
      latch.release(p);
      return;
    }

    double nextJumps = currentJumps + 1D;
    if (nextJumps - 0.25D > getMaxJumps(level)) {
      latch.release(p);
      return;
    }

    latch.release(p);
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
    publishJumps(id, level, nextJumps);

    int jumpCount = (int) Math.floor(nextJumps);
    int cloudCount = Math.max(2, Math.min(6, getMaxJumps(level) - jumpCount + 1));
    float kickPitch = (float) Math.min(2.0D, 0.8D + (jumpCount * 0.12D));
    latch.kick(p, stickBlock, cloudCount, kickPitch);
    addStat(p, "agility.wall-jump.air-jumps", 1);
    if (nextJumps >= 5D && AdaptApi.settings().advancements()
        && !getPlayer(p).isGranted(getId(), "challenge_agility_parkour_master")) {
      getPlayer(p).grant(getId(), "challenge_agility_parkour_master");
    }
  }

  private boolean isBackwardLaunch(PlayerRef p) {
    UUID id = p.uuid();
    Long at = horizontalIntentTime.get(id);
    Vec3 intent = horizontalIntent.get(id);
    if (at == null || intent == null || System.currentTimeMillis() - at > getConfig().inputWindowMs) {
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

  private void publishJumps(UUID id, int level, double used) {
    int max = getMaxJumps(level);
    HudStatus.charges(id, this, Math.max(0, (int) Math.floor(max + 0.25D - used)), max);
  }

  private void clearJumps(UUID id) {
    if (airjumps.remove(id) != null) {
      HudStatus.clear(id, this, HudGlyph.CHARGES);
    }
  }

  private void clearPlayerState(PlayerRef p) {
    UUID id = p.uuid();
    clearJumps(id);
    horizontalIntent.remove(id);
    horizontalIntentTime.remove(id);
    sneakState.remove(id);
    latch.release(p);
  }

  private static BlockRef blockAt(Loc location) {
    return location.world().blockAt(location);
  }

  @ConfigDescription("Hold shift while mid-air against a wall to latch, then release shift to jump.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Max Jumps Level Bonus Divisor for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxJumpsLevelBonusDivisor = 2;
    @ConfigDoc(value = "Controls Jump Height Base for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpHeightBase = 0.625;
    @ConfigDoc(value = "Controls Jump Height Bonus Level Multiplier for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpHeightBonusLevelMultiplier = 0.225;
    @ConfigDoc(value = "Controls Backward Push Speed for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double backwardPushSpeed = 0.22;
    @ConfigDoc(value = "Controls Backward Intent Dot Threshold for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double backwardIntentDotThreshold = 0.35;
    @ConfigDoc(value = "Controls Input Movement Threshold for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double inputMovementThreshold = 0.0025;
    @ConfigDoc(value = "Controls Input Window Ms for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long inputWindowMs = 450;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  public enum Control { HOLD, TAP }


  static void latchFx(Adaptation<?> owner, Loc contact, BlockRef wall) {
    Fx.now(owner, contact, FxPriority.COMBAT)
        .particle(Particles.blockCrack(wall.state()), 8, 0, 0.2D, 0, 0.25D, 0.05D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1F, 0.89F, Sounds.ITEM_ARMOR_EQUIP_CHAIN, 1F, 1.39F, Sounds.BLOCK_LADDER_STEP, 0.4F, 0.8F);
  }

  static void clingFx(Adaptation<?> owner, PlayerRef p, BlockRef wall) {
    Fx.now(owner, p.location(), FxPriority.TRAIL)
        .particle(Particles.blockCrack(wall.state()), 1, 0, 0, 0, 0.05D, 0.05D);
  }

  static void kickFx(Adaptation<?> owner, Loc location, BlockRef wall, double awayX, double awayZ, int cloudCount, float pitch) {
    Fx.now(owner, location, FxPriority.GAMEPLAY)
        .trail(Particles.CLOUD, awayX, 0.2D, awayZ, 0.8D, cloudCount)
        .particle(Particles.blockCrack(wall.state()), 3, 0, 0.3D, 0, 0.15D, 0.05D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.9F, pitch);
  }
}
