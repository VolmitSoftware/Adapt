package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player completed a real (vanilla, datapack or mod) advancement. Read-only. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerAdvancementDoneEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Id advancement;
  private final AdvancementFrame frame;

  public PlayerAdvancementDoneEvent(PlayerRef player, Id advancement, AdvancementFrame frame) {
    this.player = Objects.requireNonNull(player, "player");
    this.advancement = Objects.requireNonNull(advancement, "advancement");
    this.frame = frame;
  }

  /** The player who completed the advancement. */
  public PlayerRef player() {
    return player;
  }

  /** Namespaced id of the advancement, such as {@code minecraft:story/mine_stone}. */
  public Id advancement() {
    return advancement;
  }

  /** Display frame of the advancement, or null when it has no display. */
  public AdvancementFrame frame() {
    return frame;
  }
}
