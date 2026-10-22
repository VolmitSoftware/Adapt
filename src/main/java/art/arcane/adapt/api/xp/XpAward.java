package art.arcane.adapt.api.xp;

import org.bukkit.Location;

import java.util.Objects;

public record XpAward(String source, double amount, Location location, boolean notifyPlayer, Kind kind) {
  public XpAward {
    source = Objects.requireNonNull(source).trim();
    Objects.requireNonNull(kind);
    if (source.isEmpty() || source.length() > 160 || !Double.isFinite(amount) || amount <= 0D) {
      throw new IllegalArgumentException("XP awards require a bounded source and positive finite amount");
    }
    location = location == null ? null : location.clone();
  }

  public enum Kind {
    GAMEPLAY,
    CONSUMABLE,
    ADMINISTRATIVE
  }
}
