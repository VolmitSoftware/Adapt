package art.arcane.adapt.api.golem.node;

import java.util.Locale;

public enum NodeVisibility {
  OWNER,
  ANY_GOLEMANCER;

  public static NodeVisibility parse(String name) {
    if (name == null) {
      return OWNER;
    }
    String normalized = name.trim().toUpperCase(Locale.ROOT);
    for (NodeVisibility visibility : values()) {
      if (visibility.name().equals(normalized)) {
        return visibility;
      }
    }
    return OWNER;
  }
}
