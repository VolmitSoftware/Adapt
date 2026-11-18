package art.arcane.adapt.api.golem;

public enum GolemNameMode {
  GLOSS,
  VANILLA;

  public static GolemNameMode resolve(boolean glossOverlay) {
    return glossOverlay ? GLOSS : VANILLA;
  }

  public boolean showsCustomName() {
    return this == VANILLA;
  }
}
