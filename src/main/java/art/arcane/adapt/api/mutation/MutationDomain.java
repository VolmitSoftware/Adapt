package art.arcane.adapt.api.mutation;

public enum MutationDomain {
  BODY("Body"),
  HUNT("Hunt"),
  INDUSTRY("Industry"),
  WILD("Wild"),
  CRAFT("Craft"),
  ANOMALY("Anomaly");

  private final String displayName;

  MutationDomain(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
