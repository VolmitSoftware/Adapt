package art.arcane.adapt.api.player;

import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** One player's line in one skill. A live view: reads reflect later changes. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface SkillProgress {
  /** The skill of the line; for a mutated line, the lead skill. */
  Id skill();

  /** The line's current level. */
  int level();

  /** Total XP earned in the line. */
  double xp();

  /** XP still missing for the next level. */
  double xpToNextLevel();

  /** Unspent knowledge. */
  long knowledge();

  /** Learned adaptations of this line and their levels. */
  Map<Id, Integer> adaptations();

  /**
   * Whether the line can change: its skill is registered and enabled and, for a mutated line, both parents are
   * registered. The write methods throw {@link IllegalStateException} on a line that is not available.
   */
  boolean isAvailable();

  /**
   * Grants XP. Fires {@code SkillXpEvent}; {@link #xp()} and {@link #level()} include the grant when this returns.
   * {@code SkillLevelUpEvent} and the level-up knowledge follow, once per level gained, on the player's next progress
   * update.
   */
  void award(XpAward award);

  /** Sets the level by granting or removing exactly the XP it takes. */
  void setLevel(int level);

  /** Adds knowledge, or removes it for a negative amount; never drops below 0. Fires {@code KnowledgeEvent}. */
  void addKnowledge(long amount);

  /** Unlearns everything in the line without refund and sets XP and knowledge to 0. */
  void reset();

  /**
   * Takes {@code amount} XP off the line, never below 0, without the level-down notice a lower level would show.
   * Does nothing for a line the player never touched.
   */
  @ApiStatus.Experimental
  void loseXp(double amount);

  /** Adds the XP Adapt still pools for the line now and shows its notice, instead of with the next batch. */
  @ApiStatus.Experimental
  void flush();

  /**
   * A value the skill saved in this line under {@code key}, or null. Numbers come back as {@code Double} after the
   * player's data reloads.
   */
  @ApiStatus.Experimental
  @Nullable Object stored(String key);

  /** Saves a string, number or boolean in this line under {@code key}; null removes it. */
  @ApiStatus.Experimental
  void store(String key, @Nullable Object value);

  /**
   * Lowers every adaptation of the line by {@code levels}, without refund. Adaptations that no longer meet their
   * requirements are removed, and adaptations over the player's power are dropped. Levels a region grants are not
   * forfeited.
   */
  @ApiStatus.Experimental
  void lowerAdaptations(int levels);
}
