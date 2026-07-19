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

import art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class AgilityLadderSlide extends SimpleAdaptation<AgilityLadderSlide.Config> {
  private static final double VANILLA_CLIMB_SPEED = 0.15D;
  private static final double TICKS_PER_SECOND = 20.0D;
  private static final int CLIMB_TOP_LOOKAHEAD = 2;
  private static final long SLIDE_GRACE_MS = 700L;
  private static final Predicate<Player> GROUNDED_ACTIONS = player -> !player.isFlying() && !player.isGliding() && !player.isSwimming();

  private final Map<UUID, LadderState> states = playerState();
  private final Cooldowns slideGrace = cooldowns();

  public AgilityLadderSlide() {
    super("agility-ladder-slide");
    registerConfiguration(Config.class);
    setIcon(Material.LADDER);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LADDER)
        .key("challenge_agility_ladder_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_CHAIN)
            .key("challenge_agility_ladder_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_ladder_500", "agility.ladder-slide.blocks-climbed", 500, 300);
    registerMilestone("challenge_agility_ladder_10k", "agility.ladder-slide.blocks-climbed", 10000, 1000);
  }


  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDescentSpeed(level) * TICKS_PER_SECOND, 1), 1);
    statLore(v, Form.f(getClimbAssist(level) * TICKS_PER_SECOND, 1), 2);
    statLore(v, Form.pc(1.0D, 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isClimbing()) {
      resetState(p);
      return;
    }

    withPlayerThread(p, e, () -> handleClimb(p));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
      return;
    }

    if (!getConfig().safeLanding || slideGrace.isReady(p.getUniqueId(), SLIDE_GRACE_MS)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      slideGrace.clear(p.getUniqueId());
      e.setCancelled(true);
      p.setFallDistance(0);
      fx(p.getLocation(), FxPriority.TRANSITION)
          .burst(Particle.CLOUD, 4, 0.12D)
          .sound(Sound.BLOCK_LADDER_STEP, 0.35F, 1.1F);
    });
  }

  private void handleClimb(Player p) {
    BlockActionContext context = resolveInteractContext(p, p.getLocation(), GROUNDED_ACTIONS);
    if (context == null) {
      resetState(p);
      return;
    }

    Block climbable = activeClimbable(context.location());
    if (climbable == null) {
      resetState(p);
      return;
    }

    int level = context.level();
    Vector velocity = p.getVelocity();
    boolean sneaking = p.isSneaking();
    LadderState state = states.computeIfAbsent(p.getUniqueId(), id -> new LadderState(velocity.getY()));
    LadderInput input = readLadderInput(p);
    Mode mode = resolveMode(sneaking, input.climb(), input.descend());
    switch (mode) {
      case CLIMB -> engageClimb(p, state, velocity, level, climbable);
      case SLIDE -> engageSlide(p, state, velocity, level);
      case BRAKE -> engageBrake(p, state, velocity);
      case NONE -> {
        transition(p, state, Mode.NONE);
        state.velocity = velocity.getY();
      }
    }
  }

  private void engageClimb(Player p, LadderState state, Vector velocity, int level, Block climbable) {
    boolean nearTop = isNearTop(climbable);
    double target = nearTop ? VANILLA_CLIMB_SPEED : getClimbAssist(level);
    transition(p, state, nearTop ? Mode.NONE : Mode.CLIMB);
    state.velocity = Math.min(target, ramp(state.velocity, target));
    applyVerticalVelocity(p, velocity, state.velocity);
    p.setFallDistance(0);
    addStat(p, "agility.ladder-slide.blocks-climbed", 1);
    if (!nearTop) {
      maybeTrail(p, false);
    }
  }

  private void engageSlide(Player p, LadderState state, Vector velocity, int level) {
    double target = -getDescentSpeed(level);
    transition(p, state, Mode.SLIDE);
    state.velocity = Math.max(target, ramp(state.velocity, target));
    applyVerticalVelocity(p, velocity, state.velocity);
    p.setFallDistance(0);
    if (getConfig().safeLanding) {
      slideGrace.mark(p.getUniqueId());
    }
    addStat(p, "agility.ladder-slide.blocks-descended", 1);
    maybeTrail(p, true);
  }

  private void engageBrake(Player p, LadderState state, Vector velocity) {
    transition(p, state, Mode.BRAKE);
    state.velocity = Math.min(0D, ramp(state.velocity, 0D));
    applyVerticalVelocity(p, velocity, state.velocity);
    p.setFallDistance(0);
    if (getConfig().safeLanding) {
      slideGrace.mark(p.getUniqueId());
    }
  }

  private void transition(Player p, LadderState state, Mode to) {
    if (state.mode == to) {
      return;
    }

    state.mode = to;
    switch (to) {
      case CLIMB -> fx(p.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particle.CRIT, 2, 0.05D)
          .sound(Sound.ENTITY_PHANTOM_FLAP, 0.22F, 1.2F);
      case SLIDE -> fx(p.getLocation(), FxPriority.TRANSITION)
          .burst(Particle.CLOUD, 3, 0.08D)
          .sound(Sound.BLOCK_LADDER_STEP, 0.3F, 0.7F);
      case BRAKE -> fx(p.getLocation(), FxPriority.TRANSITION)
          .sound(Sound.BLOCK_LADDER_HIT, 0.28F, 0.9F);
      case NONE -> {
      }
    }
  }

  private void maybeTrail(Player p, boolean sliding) {
    if (!M.r(0.08D)) {
      return;
    }

    double oy = sliding ? 0.2D : -0.2D;
    fx(p.getLocation(), FxPriority.TRAIL).particle(Particle.CLOUD, 1, 0, oy, 0, 0.05D, 0.02D);
  }

  private double ramp(double current, double target) {
    double smoothing = clamp(getConfig().assistSmoothing, 0.05D, 1.0D);
    return current + ((target - current) * smoothing);
  }

  private void applyVerticalVelocity(Player p, Vector velocity, double targetY) {
    if (Math.abs(velocity.getY() - targetY) <= Math.abs(getConfig().velocityEpsilon)) {
      return;
    }

    p.setVelocity(velocity.setY(targetY));
  }

  private void resetState(Player p) {
    states.remove(p.getUniqueId());
  }

  private Block activeClimbable(Location location) {
    Block feet = location.getBlock();
    if (isSlideClimbable(feet.getType())) {
      return feet;
    }

    Block head = feet.getRelative(BlockFace.UP);
    if (isSlideClimbable(head.getType())) {
      return head;
    }

    return null;
  }

  private boolean isNearTop(Block climbable) {
    Block cursor = climbable;
    for (int i = 0; i < CLIMB_TOP_LOOKAHEAD; i++) {
      cursor = cursor.getRelative(BlockFace.UP);
      if (!isSlideClimbable(cursor.getType())) {
        return true;
      }
    }

    return false;
  }

  private boolean isSlideClimbable(Material type) {
    return type != Material.SCAFFOLDING && Tag.CLIMBABLE.isTagged(type);
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private LadderInput readLadderInput(Player p) {
    try {
      Input input = p.getCurrentInput();
      if (input == null) {
        return LadderInput.NONE;
      }
      return new LadderInput(input.isForward() || input.isJump(), input.isBackward());
    } catch (NoSuchMethodError ignored) {
      return LadderInput.NONE;
    }
  }

  static Mode resolveMode(boolean sneaking, boolean climbInput, boolean descentInput) {
    if (sneaking) {
      return Mode.BRAKE;
    }
    if (climbInput) {
      return Mode.CLIMB;
    }
    if (descentInput) {
      return Mode.SLIDE;
    }
    return Mode.NONE;
  }

  private double getDescentSpeed(int level) {
    return getConfig().descentSpeedBase + (getLevelPercent(level) * getConfig().descentSpeedPerLevel);
  }

  private double getClimbAssist(int level) {
    return getConfig().climbAssistBase + (getLevelPercent(level) * getConfig().climbAssistPerLevel);
  }

  @ConfigDescription("Hold forward or jump to climb faster, hold backward to slide quickly, and sneak to brake.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base downward slide speed in blocks per tick before per-level scaling.", impact = "Higher values make ladder descents faster; lower values keep them gentle.")
    double descentSpeedBase = 0.30;
    @ConfigDoc(value = "Additional downward slide speed granted at the maximum level.", impact = "Higher values widen the descent-speed gain earned from leveling this adaptation.")
    double descentSpeedPerLevel = 0.30;
    @ConfigDoc(value = "Base upward climb-assist speed in blocks per tick before per-level scaling.", impact = "Higher values make ladder ascents faster; lower values keep them closer to vanilla.")
    double climbAssistBase = 0.28;
    @ConfigDoc(value = "Additional upward climb-assist speed granted at the maximum level.", impact = "Higher values widen the climb-speed gain earned from leveling this adaptation.")
    double climbAssistPerLevel = 0.22;
    @ConfigDoc(value = "Smoothing factor blending current vertical speed toward the target each move.", impact = "Values near 1.0 snap to the target quickly; lower values ease in more gradually.")
    double assistSmoothing = 0.35;
    @ConfigDoc(value = "Vertical velocity difference below which tiny corrective writes are skipped.", impact = "Lower values apply more frequent micro-updates; higher values reduce minor velocity writes.")
    double velocityEpsilon = 0.003;
    @ConfigDoc(value = "Prevents fall damage that results directly from a fast ladder slide.", impact = "True negates slide-attributable landing damage; false lets vanilla fall damage apply.")
    boolean safeLanding = true;

    public Config() {
      baseCost = 1;
      costFactor = 0.12;
      maxLevel = 1;
      initialCost = 1;
    }
  }

  enum Mode {
    NONE,
    CLIMB,
    SLIDE,
    BRAKE
  }

  private record LadderInput(boolean climb, boolean descend) {
    private static final LadderInput NONE = new LadderInput(false, false);
  }

  private static final class LadderState {
    private Mode mode;
    private double velocity;

    private LadderState(double velocity) {
      this.mode = Mode.NONE;
      this.velocity = velocity;
    }
  }
}
