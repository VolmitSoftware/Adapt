package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.kinetics.KineticsLandingWatch;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Map;
import java.util.UUID;

/**
 * Holds while the player is off the ground on their own: not flying, gliding, swimming, climbing or riding. The recent
 * form asks instead whether they landed from a jump or fall within the window, through Rubber Soul's landing watch.
 */
public class KineticsState extends StateBlock<KineticsState.Config> {
  private final KineticsLandingWatch landings = new KineticsLandingWatch(playerState(), playerState());
  private final Map<UUID, Long> landedAt = playerState();

  public KineticsState() {
    super("block-kinetics-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("elytra"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    landings.observe(this, e, (PlayerRef player, PlayerMoveEvent landing) ->
        landedAt.put(player.uuid(), System.currentTimeMillis()));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return !player.isOnGround() && !player.isFlying() && !player.isGliding() && !player.isSwimming()
        && !player.isClimbing() && !player.isInWater() && player.vehicle() == null;
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long landed = landedAt.get(player.uuid());
    return holds(player) || (landed != null && System.currentTimeMillis() - landed <= windowMillis);
  }

  @ConfigDescription("You are airborne.")
  public static class Config extends MutationBlock.Config {
  }
}
