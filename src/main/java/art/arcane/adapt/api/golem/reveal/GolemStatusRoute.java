package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.GolemNameMode;

public enum GolemStatusRoute {
  HIDDEN,
  LABEL,
  GLOSS,
  INSIGHT;

  public static GolemStatusRoute of(GolemNameMode mode, boolean statusShown, boolean insightActive) {
    if (!statusShown) {
      return HIDDEN;
    }
    return switch (mode) {
      case GLOSS -> GLOSS;
      case INSIGHT -> insightActive ? INSIGHT : LABEL;
      case VANILLA -> LABEL;
    };
  }
}
