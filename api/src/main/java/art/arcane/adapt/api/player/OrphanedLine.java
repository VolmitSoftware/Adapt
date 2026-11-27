package art.arcane.adapt.api.player;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Saved progress with no installed provider. Either a whole skill line ({@link #adaptation()} empty) or one adaptation
 * inside a line whose skill is still registered, such as an addon's adaptation on a built-in skill; that entry stands
 * for the adaptation alone, never for the line that holds it. Adapt creates these; new accessors may be added.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface OrphanedLine {
  /** The saved line: a skill's own line or a mutated line of two skills. */
  Key line();

  /** The orphaned adaptation, when only that is missing. */
  Optional<Id> adaptation();

  /** The namespace whose addon would bring it back. */
  String namespace();

  /** XP of an orphaned line; 0 for a single adaptation. */
  double xp();

  /**
   * Knowledge held in an orphaned line; for a single adaptation, the knowledge its levels cost, which a refund returns
   * (0 for a region grant).
   */
  long knowledge();

  /** Learned levels inside an orphaned line, by adaptation. */
  Map<Id, Integer> adaptations();

  /** Which saved line an orphan belongs to. */
  sealed interface Key permits Line, Pair {
  }

  /**
   * The line of one skill.
   *
   * @param skill the skill id
   */
  record Line(Id skill) implements Key {
    public Line {
      Objects.requireNonNull(skill, "skill");
    }
  }

  /**
   * The mutated line of two skills.
   *
   * @param pair the two skills
   */
  record Pair(SkillPair pair) implements Key {
    public Pair {
      Objects.requireNonNull(pair, "pair");
    }
  }
}
