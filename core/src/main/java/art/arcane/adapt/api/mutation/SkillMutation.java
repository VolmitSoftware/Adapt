package art.arcane.adapt.api.mutation;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Two skills a player merged into one; the lead names the mutation's noun and supplies its icon. */
public record SkillMutation(String lead, String partner) {
  public static final String PREFIX = "mutated-";
  private static final Map<SkillMutation, String> IDS = new ConcurrentHashMap<>();

  public SkillMutation {
    Objects.requireNonNull(lead, "lead");
    Objects.requireNonNull(partner, "partner");
  }

  /** The skill line id of this mutation, such as mutated-swords-stealth. */
  public String id() {
    return IDS.computeIfAbsent(this, (SkillMutation mutation) -> PREFIX + mutation.lead + "-" + mutation.partner);
  }

  public boolean involves(String skill) {
    return lead.equals(skill) || partner.equals(skill);
  }

  public static boolean isMutationId(String id) {
    return parse(id) != null;
  }

  /** The mutation an id names, or null when the id is not one; both parents must be skills with mutation words. */
  public static SkillMutation parse(String id) {
    if (id == null || !id.startsWith(PREFIX)) {
      return null;
    }
    String pair = id.substring(PREFIX.length());
    int split = pair.indexOf('-');
    if (split <= 0 || split == pair.length() - 1) {
      return null;
    }
    String lead = pair.substring(0, split);
    String partner = pair.substring(split + 1);
    if (lead.equals(partner) || SkillWords.of(lead) == null || SkillWords.of(partner) == null) {
      return null;
    }
    return new SkillMutation(lead, partner);
  }
}
