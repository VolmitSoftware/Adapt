package art.arcane.adapt.content.golem.reveal;

import art.arcane.adapt.content.golem.GolemNameMode;

public enum GolemStatusRoute {
  HIDDEN,
  LABEL,
  GLOSS;

  public static GolemStatusRoute of(GolemNameMode mode, boolean statusShown) {
    if (!statusShown) {
      return HIDDEN;
    }
    return switch (mode) {
      case GLOSS -> GLOSS;
      case VANILLA -> LABEL;
    };
  }
}
