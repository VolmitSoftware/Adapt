package art.arcane.adapt.client.view;

import java.util.Objects;

/** The spread's labels in the player's language; {@code level} holds a {@code {level}} placeholder. */
public record MutationWords(String title, String lead, String partner, String gains, String loses, String hold,
                            String permanent, String swap, String level) {
  public MutationWords {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(lead, "lead");
    Objects.requireNonNull(partner, "partner");
    Objects.requireNonNull(gains, "gains");
    Objects.requireNonNull(loses, "loses");
    Objects.requireNonNull(hold, "hold");
    Objects.requireNonNull(permanent, "permanent");
    Objects.requireNonNull(swap, "swap");
    Objects.requireNonNull(level, "level");
  }

  public String level(int value) {
    return level.replace("{level}", Integer.toString(value));
  }
}
