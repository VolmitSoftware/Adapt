package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Objects;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public record GolemToolNote(TextKey key, String tool) {
  public GolemToolNote {
    Objects.requireNonNull(key);
  }

  public String text() {
    return tool == null ? AdaptLanguage.text(key) : AdaptLanguage.text(key, trusted("tool", tool));
  }
}
