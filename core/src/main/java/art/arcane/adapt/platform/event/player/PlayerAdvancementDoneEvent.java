package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** A player completed a real (vanilla, datapack or mod) advancement. frame is null when it has no display. */
public class PlayerAdvancementDoneEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Id advancement;
  private final AdvancementFrame frame;

  public PlayerAdvancementDoneEvent(PlayerRef player, Id advancement, AdvancementFrame frame) {
    this.player = Objects.requireNonNull(player, "player");
    this.advancement = Objects.requireNonNull(advancement, "advancement");
    this.frame = frame;
  }

  public PlayerRef player() {
    return player;
  }

  public Id advancement() {
    return advancement;
  }

  public AdvancementFrame frame() {
    return frame;
  }
}
