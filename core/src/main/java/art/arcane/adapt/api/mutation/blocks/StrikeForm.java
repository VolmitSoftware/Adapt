package art.arcane.adapt.api.mutation.blocks;

/**
 * Which Strike form a mutated adaptation uses, set by the kind of Trigger that fires it. Barter and Attunement have
 * no Strike.
 */
public enum StrikeForm {
  FIGHT,
  WORK,
  EITHER,
  NONE;

  public static StrikeForm of(TriggerKind kind) {
    return switch (kind) {
      case FIGHT -> FIGHT;
      case WORK -> WORK;
      case EITHER -> EITHER;
    };
  }
}
