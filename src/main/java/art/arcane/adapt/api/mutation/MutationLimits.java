package art.arcane.adapt.api.mutation;

public final class MutationLimits {
  public static final long MAX_DURATION_MILLIS = 31_536_000_000L;
  public static final int MAX_DELAY_TICKS = 72_000;
  public static final int QUALIFICATION_CANDIDATES_PER_DOMAIN = 64;
  public static final double GALE_MOMENTUM = 100D;
  public static final double BASTION_STABILITY = 8D;
  public static final int BASTION_TARGETS = 12;
  public static final int VERDANT_EFFECTS = 32;
  public static final int ARSENAL_CHAIN = 4;
  public static final int PACK_TEMPO = 6;
  public static final int PACK_MEMBERS = 8;
  public static final int UMBRAL_TARGETS = 8;
  public static final double LATTICE_ROOT_CHARGE = 12D;
  public static final int LATTICE_BLOCKS = 16;
  public static final int LATTICE_STRUCTURES = 3;
  public static final double DEEPBLOOD_ICHOR = 100D;
  public static final int MYCELIAL_RECIPIENTS = 8;
  public static final int MYCELIAL_EFFECT_COPIES = 32;
  public static final int GRAVEBLOOM_BLOOMS = 3;
  public static final int GRAVEBLOOM_CROPS = 16;
  public static final int GRAVEBLOOM_ANIMALS = 8;
  public static final int FORMULA_SIGILS = 3;

  private MutationLimits() {
  }
}
