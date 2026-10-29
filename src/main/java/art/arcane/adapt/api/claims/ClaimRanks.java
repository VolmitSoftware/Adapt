package art.arcane.adapt.api.claims;

public record ClaimRanks(boolean founding, int fortification, int citizenship, int expansion, boolean homestead) {
  public ClaimRanks {
    if (fortification < 0 || fortification > 4 || citizenship < 0 || citizenship > 4 || expansion < 0 || expansion > 4) {
      throw new IllegalArgumentException("Claims ranks must be between zero and four");
    }
  }

  public static ClaimRanks none() {
    return new ClaimRanks(false, 0, 0, 0, false);
  }
}
