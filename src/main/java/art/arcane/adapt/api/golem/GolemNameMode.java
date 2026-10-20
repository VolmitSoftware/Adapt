package art.arcane.adapt.api.golem;

public enum GolemNameMode {
  GLOSS,
  INSIGHT,
  VANILLA;

  public static GolemNameMode resolve(boolean glossOverlay, boolean insightEnabled) {
    if (glossOverlay) {
      return GLOSS;
    }
    return insightEnabled ? INSIGHT : VANILLA;
  }

  public boolean showsCustomName() {
    return this != INSIGHT;
  }
}
