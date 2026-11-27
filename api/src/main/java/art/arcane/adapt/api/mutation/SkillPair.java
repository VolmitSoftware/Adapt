package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Two skills merged into one mutation. The lead names the mutation's noun and supplies its emblem; the partner
 * supplies the adjective and the trim. {@code (a, b)} and {@code (b, a)} are different mutations.
 *
 * @param lead    the leading skill's id, a single path segment
 * @param partner the partner skill's id, a single path segment, not the lead
 */
@ApiStatus.Experimental
public record SkillPair(Id lead, Id partner) {
  private static final char SEPARATOR = '+';

  /**
   * @throws IllegalArgumentException when either id breaks the skill id grammar, both are the same skill, or the
   *                                  mutated skill's {@link #id()} would be longer than an id may be
   */
  public SkillPair {
    ContentIds.requireSingleSegment(lead, "lead skill");
    ContentIds.requireSingleSegment(partner, "partner skill");
    if (lead.equals(partner)) {
      throw new IllegalArgumentException("A skill cannot mutate with itself: " + lead);
    }
    if (!ContentIds.validPath(mutatedPath(lead, partner))) {
      throw new IllegalArgumentException("The mutated skill id of " + lead + SEPARATOR + partner + " is longer than "
          + ContentIds.MAX_PATH_LENGTH + " characters");
    }
  }

  /**
   * The id of the mutated skill: {@code <lead namespace>:mutated/<lead path>/<partner namespace>/<partner path>},
   * such as {@code adapt:mutated/swords/adapt/stealth}. Never parsed; content the pair owns has ids below it. Skill ids
   * have one path segment, so the form is unambiguous.
   */
  public Id id() {
    return Id.of(lead.namespace(), mutatedPath(lead, partner));
  }

  private static String mutatedPath(Id lead, Id partner) {
    return "mutated/" + lead.path() + "/" + partner.namespace() + "/" + partner.path();
  }

  /** The id of content this pair owns, {@code path} below {@link #id()}. */
  public Id child(String path) {
    Id base = id();
    return ContentIds.require(Id.of(base.namespace(), base.path() + "/" + path), "mutation content");
  }

  /** Whether {@code skill} is the lead or the partner. */
  public boolean involves(Id skill) {
    return lead.equals(skill) || partner.equals(skill);
  }

  /** The other parent; throws when {@code skill} is neither. */
  public Id other(Id skill) {
    if (lead.equals(skill)) {
      return partner;
    }
    if (partner.equals(skill)) {
      return lead;
    }
    throw new IllegalArgumentException(skill + " is not part of " + this);
  }

  /**
   * The key of the mutated skill line in a player's save: {@code <lead>+<partner>}, both written in full. {@code +}
   * cannot occur in an id, so the key splits without knowing any skill.
   */
  public String key() {
    return lead.toString() + SEPARATOR + partner;
  }

  /**
   * The pair a {@link #key()} names; empty, never throwing, when {@code key} does not hold exactly one {@code +}
   * between the ids of two different skills that {@link #SkillPair(Id, Id)} accepts, such as an adaptation id with
   * several path segments.
   */
  public static Optional<SkillPair> parse(String key) {
    Objects.requireNonNull(key, "key");
    int split = key.indexOf(SEPARATOR);
    if (split <= 0 || split == key.length() - 1 || key.indexOf(SEPARATOR, split + 1) >= 0) {
      return Optional.empty();
    }
    Id lead = Id.tryParse(key.substring(0, split));
    Id partner = Id.tryParse(key.substring(split + 1));
    if (lead == null || partner == null || lead.equals(partner) || !skill(lead) || !skill(partner)
        || !ContentIds.validPath(mutatedPath(lead, partner))) {
      return Optional.empty();
    }
    return Optional.of(new SkillPair(lead, partner));
  }

  private static boolean skill(Id id) {
    return ContentIds.valid(id) && id.path().indexOf('/') < 0;
  }

  @Override
  public String toString() {
    return key();
  }
}
