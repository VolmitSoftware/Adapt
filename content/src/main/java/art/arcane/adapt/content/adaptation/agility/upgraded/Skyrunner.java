package art.arcane.adapt.content.adaptation.agility.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.adaptation.agility.AgilityDash;
import art.arcane.adapt.content.adaptation.agility.AgilityHighJump;
import art.arcane.adapt.content.adaptation.agility.AgilityStamina;
import art.arcane.adapt.content.adaptation.agility.AgilityWallLatch;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wall Jump, Super Jump and Air Dash fused: leaving the ground fills the mid-air charges, spent by a wall jump, a
 * mid-air high jump, an air dash or a grapple of the {@link GrapplingLine}, and latching onto a wall gives one back a
 * few times before you land. Sneaking on the ground still charges a high jump. The wall latch needs the gravity
 * attribute and the high jump the jump strength attribute; each part is inert where its attribute is missing.
 */
public class Skyrunner extends SkillUpgrade<Skyrunner.Config> {
  public static final Id ID = Id.adapt("agility/upgrade/skyrunner");

  private final Map<UUID, Integer> charges = playerState();
  private final Map<UUID, Boolean> groundGrapples = playerState();
  private final Map<UUID, Boolean> wasOnGround = playerState();
  private final Map<UUID, Integer> wallRefreshes = playerState();
  private final AgilityWallLatch latch = new AgilityWallLatch(this, playerState());
  private final GrapplingLine grapple;

  public Skyrunner() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("feather"));
    grapple = registerTool(new GrapplingLine(this, playerState()));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getHighJumpHeight(level), 2), 1);
    statLore(v, Format.f(getDashForce(level) * 20D, 1), 2);
    statLore(v, Format.f(getWallJumpVelocity(level), 2), 3);
    statLore(v, getMidAirCharges(level), 4);
    statLore(v, Format.f(getGrappleRange(level), 0), 5);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    boolean groundNow = p.isOnGround();
    Boolean previousGround = wasOnGround.put(id, groundNow);
    boolean groundBefore = previousGround == null ? groundNow : previousGround;
    if (groundNow) {
      if (!groundBefore || charges.containsKey(id) || latch.latched(id)) {
        land(p);
      }
      return;
    }

    if (!airborneEligible(p)) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      land(p);
      return;
    }
    if (groundBefore) {
      wallRefreshes.remove(id);
      int full = getMidAirCharges(level);
      setCharges(p, groundGrapples.remove(id) == null ? full : full - 1, full);
    }
    if (p.isSneaking() && canLatch()) {
      hold(p);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!e.isSneaking()) {
      if (p.isOnGround()) {
        AgilityHighJump.remove(this, p);
      } else if (latch.latched(p.uuid())) {
        wallJump(p, level);
      }
      return;
    }

    if (p.isOnGround()) {
      if (canHighJump()) {
        AgilityHighJump.apply(this, p, getHighJumpHeight(level));
        AgilityHighJump.crouch(this, p);
      }
      return;
    }

    if (!airborneEligible(p) || (canLatch() && hold(p))) {
      return;
    }
    if (canHighJump() && spend(p)) {
      double height = getHighJumpHeight(level);
      p.setVelocity(p.velocity().withY(AgilityHighJump.launchVelocity(height)));
      p.setFallDistance(0F);
      AgilityHighJump.burst(this, p, height);
      cue(p, MutationCue.WORK);
      addStat(p, "agility.skyrunner.air-jumps", 1);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerJumpEvent e) {
    PlayerRef p = e.player();
    if (p.isSneaking() && canHighJump() && getActiveLevel(p) > 0) {
      AgilityHighJump.burst(this, p, getHighJumpHeight(getActiveLevel(p)));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.LEFT_CLICK_AIR) {
      return;
    }

    PlayerRef p = e.player();
    withAdaptedPlayer(p, () -> dash(p));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    land(e.player());
    grapple.release(e.player());
    wasOnGround.remove(e.player().uuid());
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    land(e.player());
    grapple.release(e.player());
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      land(e.player());
      grapple.release(e.player());
    }
  }

  /**
   * Spends a charge for a grapple: from the ground the next airtime starts one charge short, in the air one charge is
   * spent; false when none is left.
   */
  boolean spendGrappleCharge(PlayerRef p) {
    if (p.isOnGround()) {
      groundGrapples.put(p.uuid(), true);
      return true;
    }
    return spend(p);
  }

  /** Fills an airborne holder's mid-air charges; false on the ground, without Skyrunner or with them already full. */
  public boolean refreshCharge(PlayerRef p) {
    int level = getActiveLevel(p);
    Integer left = charges.get(p.uuid());
    if (level <= 0 || p.isOnGround() || left == null || left >= getMidAirCharges(level)) {
      return false;
    }
    int full = getMidAirCharges(level);
    setCharges(p, full, full);
    return true;
  }

  void grappled(PlayerRef p) {
    cue(p, MutationCue.WORK);
    addStat(p, "agility.skyrunner.grapples", 1);
  }

  private void dash(PlayerRef p) {
    int level = getActiveLevel(p);
    if (level <= 0 || !AgilityDash.eligible(p) || p.foodLevel() <= 0 || latch.latched(p.uuid())) {
      return;
    }

    Vec3 direction = AgilityDash.direction(p);
    if (direction == null || !spend(p)) {
      return;
    }

    AgilityStamina.spend(p, getConfig().dashHungerCost);
    AgilityDash.launch(this, p, direction, getDashForce(level), getConfig().dashUpwardLift);
    cue(p, MutationCue.WORK);
    addStat(p, "agility.skyrunner.dashes", 1);
  }

  private boolean hold(PlayerRef p) {
    BlockRef wall = latch.findWall(p);
    if (wall == null) {
      latch.release(p);
      return false;
    }
    if (latch.hold(p, wall) && wallRefreshes.getOrDefault(p.uuid(), 0) < getConfig().wallRefreshesPerJump) {
      wallRefreshes.merge(p.uuid(), 1, Integer::sum);
      int full = getMidAirCharges(getActiveLevel(p));
      setCharges(p, Math.min(full, charges.getOrDefault(p.uuid(), 0) + 1), full);
    }
    return true;
  }

  private void wallJump(PlayerRef p, int level) {
    BlockRef wall = latch.wall(p.uuid());
    latch.release(p);
    if (wall == null || !spend(p)) {
      return;
    }

    p.setVelocity(p.velocity().withY(getWallJumpVelocity(level)));
    latch.kick(p, wall, 4, 1.2F);
    cue(p, MutationCue.WORK);
    addStat(p, "agility.skyrunner.wall-jumps", 1);
  }

  private void setCharges(PlayerRef p, int count, int full) {
    UUID id = p.uuid();
    int next = Math.max(0, count);
    Integer previous = charges.put(id, next);
    HudStatus.charges(id, this, next, full);
    if (previous != null && next > previous) {
      cue(p, MutationCue.BOON);
    }
  }

  private boolean spend(PlayerRef p) {
    UUID id = p.uuid();
    int left = charges.getOrDefault(id, 0);
    if (left <= 0) {
      return false;
    }
    grapple.release(p);
    setCharges(p, left - 1, getMidAirCharges(getActiveLevel(p)));
    return true;
  }

  private void land(PlayerRef p) {
    UUID id = p.uuid();
    latch.release(p);
    wallRefreshes.remove(id);
    if (!p.isOnGround()) {
      groundGrapples.remove(id);
    }
    if (charges.remove(id) != null) {
      HudStatus.clear(id, this, HudGlyph.CHARGES);
    }
  }

  private static boolean airborneEligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
        && !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.vehicle() == null;
  }

  private static boolean canLatch() {
    return PlatformNeed.usable(AgilityWallLatch.NEEDS);
  }

  private static boolean canHighJump() {
    return PlatformNeed.usable(List.of(PlatformNeed.attribute(Attributes.JUMP_STRENGTH)));
  }

  double getHighJumpHeight(int level) {
    return getConfig().highJumpHeightBase + (level * getConfig().highJumpHeightPerLevel);
  }

  double getDashForce(int level) {
    return getConfig().dashForceBase + (level * getConfig().dashForcePerLevel);
  }

  double getWallJumpVelocity(int level) {
    return getConfig().wallJumpVelocityBase + (level * getConfig().wallJumpVelocityPerLevel);
  }

  int getMidAirCharges(int level) {
    return Math.max(1, Math.min(getConfig().maxMidAirCharges,
        getConfig().midAirChargesBase + (Math.max(1, level) - 1) * getConfig().midAirChargesPerLevel));
  }

  double getGrappleRange(int level) {
    return getConfig().grappleRangeBase + (level * getConfig().grappleRangePerLevel);
  }

  double getReelSpeed(int level) {
    return getConfig().reelSpeedBase + (Math.max(1, level) * getConfig().reelSpeedPerLevel);
  }

  @ConfigDescription("Wall jump, high jump and air dash share one mid-air charge that a wall refreshes a few times per jump.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "High jump apex in blocks before level scaling.", impact = "Higher values make ground and mid-air high jumps rise further.")
    double highJumpHeightBase = 2.0;
    @ConfigDoc(value = "High jump apex in blocks added per level.", impact = "Higher values reward higher levels more.")
    double highJumpHeightPerLevel = 0.6;
    @ConfigDoc(value = "Upward velocity of a wall jump before level scaling, in blocks per tick.", impact = "Higher values push off walls higher.")
    double wallJumpVelocityBase = 0.7;
    @ConfigDoc(value = "Wall jump velocity added per level, in blocks per tick.", impact = "Higher values reward higher levels more.")
    double wallJumpVelocityPerLevel = 0.1;
    @ConfigDoc(value = "Air dash velocity before level scaling, in blocks per tick.", impact = "Higher values throw the dash further.")
    double dashForceBase = 0.9;
    @ConfigDoc(value = "Air dash velocity added per level, in blocks per tick.", impact = "Higher values reward higher levels more.")
    double dashForcePerLevel = 0.15;
    @ConfigDoc(value = "Upward velocity added to every air dash.", impact = "Higher values keep dashes airborne longer.")
    double dashUpwardLift = 0.12;
    @ConfigDoc(value = "Saturation and hunger spent per air dash.", impact = "Higher values make dashing drain stamina faster.")
    double dashHungerCost = 1.5;
    @ConfigDoc(value = "Walls that give back a mid-air charge before you touch the ground again.", impact = "Higher values let you climb higher up a wall.")
    int wallRefreshesPerJump = 3;
    @ConfigDoc(value = "Mid-air charges at level 1.", impact = "Higher values allow more air moves per jump.")
    int midAirChargesBase = 1;
    @ConfigDoc(value = "Mid-air charges added per level above 1.", impact = "Higher values reward higher levels more.")
    int midAirChargesPerLevel = 1;
    @ConfigDoc(value = "Most mid-air charges at any level.", impact = "Higher values allow longer chains of air moves.")
    int maxMidAirCharges = 2;
    @ConfigDoc(value = "Grappling line reach in blocks before level scaling.", impact = "Higher values hook blocks further away.")
    double grappleRangeBase = 8;
    @ConfigDoc(value = "Grappling line reach in blocks added per level.", impact = "Higher values reward higher levels more.")
    double grappleRangePerLevel = 8;
    @ConfigDoc(value = "Reel speed in blocks per tick before level scaling.", impact = "Higher values pull you to the hook faster.")
    double reelSpeedBase = 0.8;
    @ConfigDoc(value = "Reel speed in blocks per tick added per level.", impact = "Higher values reward higher levels more.")
    double reelSpeedPerLevel = 0.1;
    @ConfigDoc(value = "Share of your own momentum kept on each reel tick.", impact = "Higher values make the reel swing wider instead of pulling straight.")
    double reelMomentumCarry = 0.35;
    @ConfigDoc(value = "Upward velocity added on each reel tick while far from the hook, in blocks per tick.", impact = "Higher values arc the reel higher over ledges.")
    double reelArcLift = 0.08;
    @ConfigDoc(value = "Longest a reel pulls, in ticks.", impact = "Higher values let a reel run longer before it lets go.")
    int maxReelTicks = 60;
    @ConfigDoc(value = "Durability spent per grapple.", impact = "Higher values wear the line out faster.")
    int grappleDurabilityCost = 1;
  }
}
