package art.arcane.adapt.api.mutation;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;

public enum MutationDomain {
  BODY("Body"),
  HUNT("Hunt"),
  INDUSTRY("Industry"),
  WILD("Wild"),
  CRAFT("Craft"),
  ANOMALY("Anomaly");

  private final TextKey displayName;

  MutationDomain(String displayName) {
    this.displayName = TextKey.of("mutation.domain." + name().toLowerCase(), displayName);
  }

  public String displayName() {
    return AdaptLanguage.text(displayName);
  }

  public static List<TextKey> keys() {
    List<TextKey> keys = new ArrayList<>(values().length);
    for (MutationDomain domain : values()) {
      keys.add(domain.displayName);
    }
    return List.copyOf(keys);
  }
}
