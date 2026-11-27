package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player's skill reached a new level. Fired once per level, after the level is set. */
@ApiStatus.AvailableSince("1.0.0")
public final class SkillLevelUpEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Id skill;
  private final int level;
  private final long knowledge;

  public SkillLevelUpEvent(PlayerRef player, Id skill, int level, long knowledge) {
    this.player = Objects.requireNonNull(player, "player");
    this.skill = Objects.requireNonNull(skill, "skill");
    this.level = level;
    this.knowledge = knowledge;
  }

  /** The player who levelled up. */
  public PlayerRef player() {
    return player;
  }

  /** The skill that levelled up; for a mutated line, the lead skill. */
  public Id skill() {
    return skill;
  }

  /** The level just reached. */
  public int level() {
    return level;
  }

  /** Knowledge the level granted. */
  public long knowledge() {
    return knowledge;
  }
}
