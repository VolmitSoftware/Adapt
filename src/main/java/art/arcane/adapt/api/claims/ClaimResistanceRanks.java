package art.arcane.adapt.api.claims;

public final class ClaimResistanceRanks {
  public ClaimResistance rank1 = new ClaimResistance(16, 128, 64, 512);
  public ClaimResistance rank2 = new ClaimResistance(32, 256, 128, 1024);
  public ClaimResistance rank3 = new ClaimResistance(48, 384, 192, 1536);
  public ClaimResistance rank4 = new ClaimResistance(64, 512, 256, 2048);

  public void validate() {
    if (rank1 == null || rank2 == null || rank3 == null || rank4 == null) {
      throw new IllegalArgumentException("Claims requires four resistance ranks");
    }
    rank1.validate();
    rank2.validate();
    rank3.validate();
    rank4.validate();
  }

  public ClaimResistance forRank(int rank) {
    return switch (rank) {
      case 1 -> rank1;
      case 2 -> rank2;
      case 3 -> rank3;
      case 4 -> rank4;
      default -> throw new IllegalArgumentException("Resistance rank must be between one and four");
    };
  }

  public ClaimResistanceRanks validatedCopy() {
    validate();
    ClaimResistanceRanks copy = new ClaimResistanceRanks();
    copy.rank1 = rank1;
    copy.rank2 = rank2;
    copy.rank3 = rank3;
    copy.rank4 = rank4;
    return copy;
  }
}
