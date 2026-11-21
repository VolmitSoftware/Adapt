package art.arcane.adapt.client.protocol;

import java.util.List;
import java.util.Objects;

/**
 * {@code value} is the hint's current value as text: ON/OFF, a number, the text, the chosen option or a route label.
 * {@code typed} means the server takes {@code PanelInput} for the tile, so a stepper sends the new value instead of
 * clicks. {@code options} lists a CHOICE's values in order; {@code min}, {@code max} and {@code step} bound a NUMBER
 * and are null when unknown.
 */
public record TileHint(HintKind kind, String value, boolean typed, List<String> options, Double min, Double max,
                       Double step) {
  public TileHint {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(value, "value");
    options = List.copyOf(options);
  }

  public static TileHint of(HintKind kind, String value) {
    return new TileHint(kind, value, false, List.of(), null, null, null);
  }
}
