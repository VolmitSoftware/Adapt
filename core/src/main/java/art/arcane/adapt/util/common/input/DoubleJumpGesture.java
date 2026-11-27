package art.arcane.adapt.util.common.input;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ability.DoubleJump;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.math.PlayerInputPort;

import java.util.Map;
import java.util.UUID;

/**
 * Jump, then one more jump press in the air. The press that leaves the ground counts as the first; after walking off
 * an edge both presses happen in the air. Jump input comes from the player input packet (1.21.2+), from the Adapt
 * client's reports ({@link ReportedJumps}) or, for other clients before 1.21.2, from flight requests
 * ({@link MidairJumps}). Never completes while flying, gliding, riding, swimming or climbing, or for a player who
 * may fly.
 */
public final class DoubleJumpGesture implements DoubleJump {
  private static final Id CLIMBABLE = Id.minecraft("climbable");
  private final Map<UUID, State> states = PlayerStateRegistry.newPlayerMap();

  @Override
  public boolean update(PlayerRef p) {
    State state = states.computeIfAbsent(p.uuid(), unused -> new State());
    PlayerInput input = AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(p);
    if (input != null) {
      return updateKeys(p, state, input.jump(), input.jump() && !state.lastJump);
    }
    if (ReportedJumps.reporting(p.uuid())) {
      stopMidair(p, state);
      long presses = ReportedJumps.presses(p.uuid());
      boolean pressed = state.reportedPresses >= 0L && presses != state.reportedPresses;
      state.reportedPresses = presses;
      boolean down = ReportedJumps.down(p.uuid());
      return updateKeys(p, state, down || pressed, pressed || down && !state.lastJump);
    }
    MidairJumps midair = MidairJumps.active();
    return midair != null && updateMidair(p, state, midair);
  }

  private boolean updateKeys(PlayerRef p, State state, boolean jump, boolean pressed) {
    boolean onGround = p.isOnGround();
    boolean completed = false;
    if (onGround) {
      state.airbornePresses = 0;
    } else if (state.wasOnGround) {
      state.airbornePresses = jump ? 1 : 0;
    } else if (pressed) {
      state.airbornePresses++;
      if (state.airbornePresses >= 2 && isJumpContext(p)) {
        state.airbornePresses = 0;
        completed = true;
      }
    }
    state.lastJump = jump;
    state.wasOnGround = onGround;
    return completed;
  }

  /**
   * Without jump input the client's flight request stands for the second press: {@link MidairJumps} lets the client
   * ask to fly, and a vanilla client asks after two jump presses less than 7 ticks apart, the first usually being the
   * one that left the ground.
   */
  private boolean updateMidair(PlayerRef p, State state, MidairJumps midair) {
    long presses = midair.listen(p, this);
    boolean completed = state.midairPresses >= 0 && presses != state.midairPresses && !p.isOnGround()
        && isJumpContext(p);
    state.midairPresses = presses;
    return completed;
  }

  private void stopMidair(PlayerRef p, State state) {
    state.midairPresses = -1L;
    MidairJumps midair = MidairJumps.active();
    if (midair != null) {
      midair.stopListening(p.uuid(), this);
    }
  }

  /** Forgets the presses seen so far and keeps listening, for a player who moved without walking (a teleport). */
  @Override
  public void restart(UUID id) {
    states.remove(id);
  }

  @Override
  public void reset(UUID id) {
    states.remove(id);
    MidairJumps midair = MidairJumps.active();
    if (midair != null) {
      midair.stopListening(id, this);
    }
  }

  private boolean isJumpContext(PlayerRef p) {
    if (p.isGliding() || p.isFlying() || p.allowFlight() || p.vehicle() != null) {
      return false;
    }

    BlockRef feet = p.world().blockAt(p.location());
    return !feet.isLiquid()
        && !AdaptCore.platform().host().registries().isTagged(RegistryKind.BLOCK, CLIMBABLE, feet.type());
  }

  private static final class State {
    private boolean lastJump;
    private boolean wasOnGround = true;
    private int airbornePresses;
    private long midairPresses = -1L;
    private long reportedPresses = -1L;
  }
}
