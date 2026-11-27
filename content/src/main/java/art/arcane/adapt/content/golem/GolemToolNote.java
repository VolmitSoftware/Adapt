package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;

import java.util.Objects;


public record GolemToolNote(Text key, String tool) {
  public GolemToolNote {
    Objects.requireNonNull(key);
  }

  public String text() {
    return tool == null ? key.resolve() : key.withMarkup("tool", String.valueOf(tool)).resolve();
  }
}
