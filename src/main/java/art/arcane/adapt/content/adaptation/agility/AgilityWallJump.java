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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilityWallJump extends SimpleAdaptation<AgilityWallJump.Config> {
  private static final String SLOT_LATCH = "latch";
  private static final double GRAVITY_CANCEL = -1D;
  private final Map<UUID, Double> airjumps = playerState();
  private final Map<UUID, Vector> horizontalIntent = playerState();
  private final Map<UUID, Long> horizontalIntentTime = playerState();
  private final Map<UUID, Boolean> sneakState = playerState();
  private final Map<UUID, Block> latchedWalls = playerState();

  public AgilityWallJump() {
    super("agility-wall-jump");
    registerConfiguration(Config.class);
    setIcon(Material.VINE);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LADDER)
        .key("challenge_agility_wall_jump_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FEATHER)
        .key("challenge_agility_parkour_master")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_agility_wall_jump_500", "agility.wall-jump.air-jumps", 500, 500);
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
  public void addStats(int level, Element v) {
    statLore(v, getMaxJumps(level), 1);
    statLore(v, Form.pc(getJumpHeight(level), 0), 2);
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    sneakState.put(id, e.isSneaking());
    if (shouldReleaseJump(e.isSneaking(), latchedWalls.containsKey(id))) {
      releaseJump(p);
      return;
    }
    updatePlayer(p);
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    clearPlayerState(e.getEntity());
  }

  @EventHandler
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.getNewGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      clearPlayerState(e.getPlayer());
    }
  }

  private int getMaxJumps(int level) {
    return (int) (level + (level / getConfig().maxJumpsLevelBonusDivisor));
  }

  private double getJumpHeight(int level) {
    return getConfig().jumpHeightBase + (getLevelPercent(level) * getConfig().jumpHeightBonusLevelMultiplier);
  }

  @EventHandler
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    boolean sneaking = p.isSneaking();
    if (!sneaking && airjumps.isEmpty() && latchedWalls.isEmpty()) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!sneaking && !airjumps.containsKey(id) && !latchedWalls.containsKey(id)) {
      return;
    }
    sneakState.put(id, sneaking);
    if (airjumps.containsKey(id)) {
      if (p.isOnGround() && !p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData().getMaterial().isAir()) {
        airjumps.remove(id);
      }
    }

    if (e.getTo() != null && e.getFrom().getWorld() != null && e.getTo().getWorld() != null && e.getFrom().getWorld().equals(e.getTo().getWorld())) {
      Vector delta = e.getTo().toVector().subtract(e.getFrom().toVector());
      delta.setY(0);
      double movementThresholdSq = getConfig().inputMovementThreshold * getConfig().inputMovementThreshold;
      if (delta.lengthSquared() >= movementThresholdSq) {
        horizontalIntent.put(id, delta.normalize());
        horizontalIntentTime.put(id, M.ms());
      }
    }

    updatePlayer(p);
  }

  private void updatePlayer(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!airjumps.containsKey(id) && !latchedWalls.containsKey(id) && !isSneaking(id, p)) {
      return;
    }
    int level = getActiveInteractLevel(p, p.getLocation());
    if (level <= 0) {
      clearPlayerState(p);
      return;
    }

    Double j = airjumps.get(id);

    if (j != null && j - 0.25 >= getMaxJumps(level)) {
      if (releaseLatch(p)) {
        fx(p.getLocation(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 4, 0.15D)
            .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.4F);
      }
      return;
    }

    if (p.isOnGround()) {
      airjumps.remove(id);
      releaseLatch(p);
      return;
    }

    Block stickBlock = stickToWall(p);
    if (p.isFlying() || !isSneaking(id, p)) {
      if (releaseLatch(p)) {
        sfx(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1F, 0.439F);
      }
      return;
    }

    if (stickBlock == null) {
      releaseLatch(p);
      return;
    }

    if (!latchedWalls.containsKey(id)) {
      double dx = (stickBlock.getX() + 0.5D) - p.getLocation().getX();
      double dz = (stickBlock.getZ() + 0.5D) - p.getLocation().getZ();
      double len = Math.sqrt((dx * dx) + (dz * dz));
      Location contact = len < 1.0E-4D
          ? p.getLocation().clone().add(0, 0.3D, 0)
          : p.getLocation().clone().add((dx / len) * 0.35D, 0.3D, (dz / len) * 0.35D);
      fx(contact, FxPriority.COMBAT)
          .particle(Particles.BLOCK_CRACK, 8, 0, 0.2D, 0, 0.25D, 0.05D, stickBlock.getBlockData())
          .chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 1F, 0.89F, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1F, 1.39F, Sound.BLOCK_LADDER_STEP, 0.4F, 0.8F);
    }

    applyWallStickForce(p, stickBlock);
    latchedWalls.put(id, stickBlock);
    clearLatchedFallDistance(p);
    AdaptAttributeService.get().apply(p, getName(), SLOT_LATCH, Attributes.GRAVITY, GRAVITY_CANCEL, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    Vector c = p.getVelocity();
    p.setVelocity(p.getVelocity().setY((c.getY() * 0.35) - 0.0025));
    Double vv = airjumps.get(id);
    vv = vv == null ? 0 : vv;
    vv += 0.0127;
    airjumps.put(id, vv);
    if (M.r(0.25)) {
      fx(p.getLocation(), FxPriority.TRAIL)
          .particle(Particles.BLOCK_CRACK, 1, 0, 0, 0, 0.05D, 0.05D, stickBlock.getBlockData());
    }
  }

  private void releaseJump(Player p) {
    UUID id = p.getUniqueId();
    int level = getActiveInteractLevel(p, p.getLocation());
    Block stickBlock = latchedWalls.get(id);
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
    Vector launch = p.getVelocity().clone().setY(getJumpHeight(level));
    if (isBackwardLaunch(p)) {
      Vector direction = p.getLocation().getDirection().clone().setY(0);
      if (direction.lengthSquared() > 0.000001D) {
        direction.normalize().multiply(-getConfig().backwardPushSpeed);
        launch.setX(direction.getX());
        launch.setZ(direction.getZ());
      }
    }
    p.setVelocity(launch);
    airjumps.put(id, nextJumps);

    int jumpCount = (int) Math.floor(nextJumps);
    double awayX = p.getLocation().getX() - (stickBlock.getX() + 0.5D);
    double awayZ = p.getLocation().getZ() - (stickBlock.getZ() + 0.5D);
    int cloudCount = Math.max(2, Math.min(6, getMaxJumps(level) - jumpCount + 1));
    float kickPitch = (float) Math.min(2.0D, 0.8D + (jumpCount * 0.12D));
    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .trail(Particle.CLOUD, awayX, 0.2D, awayZ, 0.8D, cloudCount)
        .particle(Particles.BLOCK_CRACK, 3, 0, 0.3D, 0, 0.15D, 0.05D, stickBlock.getBlockData())
        .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.9F, kickPitch);
    addStat(p, "agility.wall-jump.air-jumps", 1);
    if (nextJumps >= 5D && AdaptConfig.get().isAdvancements()
        && !getPlayer(p).getData().isGranted("challenge_agility_parkour_master")) {
      getPlayer(p).getAdvancementHandler().grant("challenge_agility_parkour_master");
    }
  }

  private boolean isBackwardLaunch(Player p) {
    UUID id = p.getUniqueId();
    Long at = horizontalIntentTime.get(id);
    Vector intent = horizontalIntent.get(id);
    if (at == null || intent == null || M.ms() - at > getConfig().inputWindowMs) {
      return false;
    }

    Vector facing = p.getLocation().getDirection().clone().setY(0);
    if (facing.lengthSquared() <= 0.000001) {
      return false;
    }

    facing.normalize();
    return intent.dot(facing) <= -Math.abs(getConfig().backwardIntentDotThreshold);
  }

  private boolean isSneaking(UUID id, Player p) {
    Boolean cached = sneakState.get(id);
    return cached != null ? cached : p.isSneaking();
  }

  static boolean shouldReleaseJump(boolean sneaking, boolean latched) {
    return !sneaking && latched;
  }

  static void clearLatchedFallDistance(Player p) {
    p.setFallDistance(0F);
  }

  private void clearPlayerState(Player p) {
    UUID id = p.getUniqueId();
    airjumps.remove(id);
    horizontalIntent.remove(id);
    horizontalIntentTime.remove(id);
    sneakState.remove(id);
    releaseLatch(p);
  }

  private boolean releaseLatch(Player p) {
    if (latchedWalls.remove(p.getUniqueId()) == null) {
      return false;
    }

    clearLatchedFallDistance(p);
    AdaptAttributeService.get().remove(p, getName(), SLOT_LATCH, Attributes.GRAVITY);
    return true;
  }

  private Block stickToWall(Player p) {
    for (Block wall : getBlocks(p)) {
      if (wall.getBlockData().getMaterial().isSolid()) {
        return wall;
      }
    }

    return null;
  }

  private void applyWallStickForce(Player p, Block wall) {
    Vector velocity = p.getVelocity();
    Vector shift = p.getLocation().toVector().subtract(wall.getLocation().clone().add(0.5, 0.5, 0.5).toVector());
    velocity.setX(velocity.getX() - (shift.getX() / 16));
    velocity.setZ(velocity.getZ() - (shift.getZ() / 16));
    p.setVelocity(velocity);
  }

  private Block[] getBlocks(Player p) {
    Block base = p.getLocation().getBlock();
    return new Block[]{
        base.getRelative(BlockFace.NORTH),
        base.getRelative(BlockFace.SOUTH),
        base.getRelative(BlockFace.EAST),
        base.getRelative(BlockFace.WEST),
        base.getRelative(BlockFace.NORTH_EAST),
        base.getRelative(BlockFace.SOUTH_EAST),
        base.getRelative(BlockFace.NORTH_WEST),
        base.getRelative(BlockFace.SOUTH_WEST),
        base.getRelative(BlockFace.NORTH_EAST).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.SOUTH_EAST).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.NORTH_WEST).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.SOUTH_WEST).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.NORTH).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.SOUTH).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.EAST).getRelative(BlockFace.UP),
        base.getRelative(BlockFace.WEST).getRelative(BlockFace.UP),
    };
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
}
