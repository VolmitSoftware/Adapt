package art.arcane.adapt.util.common.input;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.math.PlayerInputPort;

import java.util.Map;
import java.util.UUID;

public final class DoubleJumpGesture {
  private static final Id CLIMBABLE = Id.minecraft("climbable");
  private final Map<UUID, State> states = PlayerStateRegistry.newPlayerMap();

  public boolean update(PlayerRef p) {
    State state = states.computeIfAbsent(p.uuid(), unused -> new State());
    boolean jump = readJump(p);
    boolean onGround = p.isOnGround();
    boolean completed = false;

    if (onGround) {
      state.airbornePresses = 0;
    } else if (state.wasOnGround) {
      state.airbornePresses = jump ? 1 : 0;
    } else if (jump && !state.lastJump) {
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

  public void reset(PlayerRef p) {
    reset(p.uuid());
  }

  public void reset(UUID id) {
    states.remove(id);
  }

  private boolean isJumpContext(PlayerRef p) {
    if (p.isGliding() || p.vehicle() != null) {
      return false;
    }

    BlockRef feet = p.world().blockAt(p.location());
    return !feet.isLiquid()
        && !AdaptCore.platform().host().registries().isTagged(RegistryKind.BLOCK, CLIMBABLE, feet.type());
  }

  private boolean readJump(PlayerRef p) {
    PlayerInput input = AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(p);
    return input != null && input.jump();
  }

  private static final class State {
    private boolean lastJump;
    private boolean wasOnGround = true;
    private int airbornePresses;
  }
}
