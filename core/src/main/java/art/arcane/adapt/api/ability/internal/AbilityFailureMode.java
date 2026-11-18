package art.arcane.adapt.api.ability.internal;

import java.util.Locale;

public enum AbilityFailureMode {
  ALLOW,
  DENY;

  public static AbilityFailureMode parse(String value, AbilityFailureMode fallback) {
    if (value == null) {
      return fallback;
    }

    return switch (value.strip().toLowerCase(Locale.ROOT)) {
      case "deny", "denied", "closed", "fail-closed" -> DENY;
      case "allow", "allowed", "open", "fail-open" -> ALLOW;
      default -> fallback;
    };
  }
}
