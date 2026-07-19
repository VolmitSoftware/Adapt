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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilityParkourMomentum extends SimpleAdaptation<AgilityParkourMomentum.Config> {
  private final Map<UUID, MomentumState> states = playerState();

  public AgilityParkourMomentum() {
    super("agility-parkour-momentum");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_FOOT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_FOOT)
        .key("challenge_agility_parkour_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_agility_parkour_500", "agility.parkour-momentum.chained-landings", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxMomentum(level), 1);
    statLore(v, Form.f(getMaximumHorizontalSpeed(level) * 20D, 1), 2);
    statLore(v, Form.f(getMaximumVerticalBoost(level), 2), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerJumpEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> handleJump(p));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    Player p = e.getPlayer();
    MomentumState state = states.get(p.getUniqueId());
    if (state == null || !state.pendingLanding) {
      return;
    }

    withPlayerThread(p, e, () -> handleLandingMovement(p, state));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      states.remove(e.getPlayer().getUniqueId());
    }
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    states.remove(e.getEntity().getUniqueId());
  }

  private void handleJump(Player p) {
    int level = getActiveLevel(p);
    if (level <= 0 || !isChainEligible(p)) {
      states.remove(p.getUniqueId());
      return;
    }

    long now = System.currentTimeMillis();
    MomentumState state = states.computeIfAbsent(p.getUniqueId(), unused -> new MomentumState());
    if (chainExpired(state.lastLandingMillis, now, getConfig().chainWindowMillis)) {
      state.momentum = 0;
      state.lastLandingMillis = 0L;
    }

    state.pendingLanding = true;
    state.airborneObserved = false;
    int currentMomentum = state.momentum;
    if (currentMomentum <= 0) {
      return;
    }

    J.runEntity(p, () -> applyJumpBoost(p, state, level, currentMomentum), 1);
  }

  private void handleLandingMovement(Player p, MomentumState expectedState) {
    MomentumState state = states.get(p.getUniqueId());
    if (state != expectedState) {
      return;
    }

    if (!p.isOnGround()) {
      state.airborneObserved = true;
      return;
    }

    if (!state.airborneObserved) {
      return;
    }

    state.pendingLanding = false;
    int level = getActiveLevel(p);
    if (level <= 0 || !isChainEligible(p)) {
      states.remove(p.getUniqueId());
      return;
    }

    state.momentum = advanceMomentum(state.momentum, getConfig().landingGain, getMaxMomentum(level));
    state.lastLandingMillis = System.currentTimeMillis();
    addStat(p, "agility.parkour-momentum.chained-landings", 1);
    fx(p.getLocation(), FxPriority.TRANSITION)
        .particle(Particles.BLOCK_CRACK, 4, 0, 0.1D, 0, 0.2D, 0.05D,
            p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData())
        .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.25F, Math.min(2.0F, 1.0F + (state.momentum * 0.06F)));
  }

  private void applyJumpBoost(Player p, MomentumState expectedState, int level, int momentum) {
    if (!p.isOnline() || p.isDead() || p.isOnGround() || states.get(p.getUniqueId()) != expectedState
        || !expectedState.pendingLanding || !isChainEligible(p)) {
      return;
    }

    VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(p, getConfig().fallbackInputVelocityThresholdSquared());
    Vector direction = VelocitySpeed.resolveHorizontalDirection(p, input);
    if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
      direction = p.getLocation().getDirection().clone().setY(0);
    }

    Vector velocity = p.getVelocity();
    if (direction.lengthSquared() > VelocitySpeed.EPSILON) {
      direction.normalize();
      double currentHorizontalSpeed = Math.sqrt((velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ()));
      double targetHorizontalSpeed = horizontalSpeed(momentum, getConfig().horizontalSpeedBase,
          getConfig().horizontalSpeedPerStack, getMaximumHorizontalSpeed(level));
      if (targetHorizontalSpeed > currentHorizontalSpeed) {
        velocity.setX(direction.getX() * targetHorizontalSpeed);
        velocity.setZ(direction.getZ() * targetHorizontalSpeed);
      }
    }

    double verticalBoost = verticalBoost(momentum, getConfig().verticalBoostPerStack, getMaximumVerticalBoost(level));
    velocity.setY(velocity.getY() + verticalBoost);
    p.setVelocity(velocity);
    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .trail(Particle.CLOUD, -velocity.getX(), 0.1D, -velocity.getZ(), 0.7D, Math.min(7, 2 + momentum));
  }

  private boolean isChainEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return p.isSprinting() && !p.isDead() && !p.isFlying() && !p.isGliding() && !p.isSwimming()
        && p.getVehicle() == null;
  }

  static boolean chainExpired(long lastLandingMillis, long nowMillis, long windowMillis) {
    return lastLandingMillis > 0L && nowMillis - lastLandingMillis > Math.max(0L, windowMillis);
  }

  static int advanceMomentum(int current, int gain, int maximum) {
    return Math.max(0, Math.min(Math.max(0, maximum), current + Math.max(0, gain)));
  }

  static double horizontalSpeed(int momentum, double base, double perStack, double maximum) {
    double target = Math.max(0D, base) + (Math.max(0, momentum) * Math.max(0D, perStack));
    return Math.min(Math.max(0D, maximum), target);
  }

  static double verticalBoost(int momentum, double perStack, double maximum) {
    double target = Math.max(0, momentum) * Math.max(0D, perStack);
    return Math.min(Math.max(0D, maximum), target);
  }

  private int getMaxMomentum(int level) {
    return Math.max(3, (int) Math.round(getConfig().momentumBase + (getLevelPercent(level) * getConfig().momentumFactor)));
  }

  private double getMaximumHorizontalSpeed(int level) {
    return horizontalSpeed(getMaxMomentum(level), getConfig().horizontalSpeedBase,
        getConfig().horizontalSpeedPerStack, getConfig().maxHorizontalSpeed);
  }

  private double getMaximumVerticalBoost(int level) {
    return verticalBoost(getMaxMomentum(level), getConfig().verticalBoostPerStack, getConfig().maxVerticalBoost);
  }

  @ConfigDescription("Chain consecutive sprint-jumps. Each clean landing builds momentum that boosts the next jump.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base maximum momentum stacks before level scaling.", impact = "Higher values allow longer chains at every level.")
    double momentumBase = 4;
    @ConfigDoc(value = "Additional maximum momentum stacks granted at max level.", impact = "Higher values make leveling support longer chains.")
    double momentumFactor = 8;
    @ConfigDoc(value = "Momentum stacks granted for each clean sprint-jump landing.", impact = "Higher values build jump power faster.")
    int landingGain = 1;
    @ConfigDoc(value = "Time allowed between a clean landing and the next sprint-jump.", impact = "Higher values make chains easier to continue after landing.")
    long chainWindowMillis = 2500L;
    @ConfigDoc(value = "Minimum horizontal speed targeted by a momentum jump.", impact = "Higher values make the first boosted jump faster.")
    double horizontalSpeedBase = 0.28D;
    @ConfigDoc(value = "Horizontal speed added to the target for each momentum stack.", impact = "Higher values make long chains accelerate more strongly.")
    double horizontalSpeedPerStack = 0.025D;
    @ConfigDoc(value = "Hard cap for horizontal momentum-jump speed.", impact = "Higher values permit faster maximum parkour movement.")
    double maxHorizontalSpeed = 0.45D;
    @ConfigDoc(value = "Vertical velocity added to the next jump per momentum stack.", impact = "Higher values make long chains jump higher.")
    double verticalBoostPerStack = 0.015D;
    @ConfigDoc(value = "Hard cap for vertical velocity added by momentum.", impact = "Higher values permit taller maximum momentum jumps.")
    double maxVerticalBoost = 0.12D;
    @ConfigDoc(value = "Fallback movement threshold used when direct input is unavailable.", impact = "Only affects direction detection on runtimes without player input access.")
    double fallbackInputVelocityThreshold = 0.0008D;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }

    double fallbackInputVelocityThresholdSquared() {
      double threshold = Math.max(0D, fallbackInputVelocityThreshold);
      return threshold * threshold;
    }
  }

  private static final class MomentumState {
    private int momentum;
    private long lastLandingMillis;
    private boolean pendingLanding;
    private boolean airborneObserved;
  }
}
