package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * XP to grant through {@code SkillProgress.award}. Start from {@link #gameplay}, {@link #consumable} or {@link #of}
 * and refine with {@link #at}, {@link #silent} and {@link #from}; each returns a new award.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class XpAward {
  /** The longest source label, in characters, after trimming. */
  public static final int MAX_SOURCE_LENGTH = 160;

  private final double amount;
  private final String source;
  private final Optional<Loc> location;
  private final boolean quiet;
  private final Kind kind;

  private XpAward(double amount, String source, Optional<Loc> location, boolean quiet, Kind kind) {
    this.location = Objects.requireNonNull(location, "location");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.source = Objects.requireNonNull(source, "source").trim();
    if (this.source.isEmpty() || this.source.length() > MAX_SOURCE_LENGTH) {
      throw new IllegalArgumentException("An XP award source must be 1 to " + MAX_SOURCE_LENGTH
          + " characters, was \"" + this.source + "\"");
    }
    if (!Double.isFinite(amount) || amount <= 0D) {
      throw new IllegalArgumentException("An XP award amount must be positive and finite, was " + amount);
    }
    this.amount = amount;
    this.quiet = quiet;
  }

  /** An administrative award: granted exactly, with the player notified. */
  public static XpAward of(double amount) {
    return new XpAward(amount, "api", Optional.empty(), false, Kind.ADMINISTRATIVE);
  }

  /** A gameplay award from a named source. */
  public static XpAward gameplay(double amount, String source) {
    return new XpAward(amount, source, Optional.empty(), false, Kind.GAMEPLAY);
  }

  /** A consumable award, such as an experience orb, from a named source: granted as given. */
  public static XpAward consumable(double amount, String source) {
    return new XpAward(amount, source, Optional.empty(), false, Kind.CONSUMABLE);
  }

  /** The same award with nothing shown to the player. */
  public XpAward silent() {
    return new XpAward(amount, source, location, true, kind);
  }

  /** The same award, earned at {@code where}. */
  public XpAward at(Loc where) {
    return new XpAward(amount, source, Optional.of(where), quiet, kind);
  }

  /** The same award under another source label. */
  public XpAward from(String label) {
    return new XpAward(amount, label, location, quiet, kind);
  }

  /** Positive and finite. */
  public double amount() {
    return amount;
  }

  /** A short label for logs and anti-farm bookkeeping, such as {@code alchemist:alchemy/transmuting}. */
  public String source() {
    return source;
  }

  /** Where the XP was earned, for the floating number and area effects. */
  public Optional<Loc> location() {
    return location;
  }

  /** True when the player sees no ticker line or sound. */
  public boolean quiet() {
    return quiet;
  }

  /** Gameplay XP is subject to multipliers and anti-farm rules; the other kinds are granted as given. */
  public Kind kind() {
    return kind;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof XpAward award && Double.compare(amount, award.amount) == 0 && quiet == award.quiet
        && source.equals(award.source) && location.equals(award.location) && kind == award.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, source, location, quiet, kind);
  }

  @Override
  public String toString() {
    return "XpAward[amount=" + amount + ", source=" + source + ", location=" + location + ", quiet=" + quiet
        + ", kind=" + kind + "]";
  }

  /**
   * Where XP comes from, which decides whether multipliers and anti-farm rules apply.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Kind {
    /** Earned by playing; multipliers and anti-farm rules apply. */
    GAMEPLAY,
    /** Redeemed from an item such as an experience orb; granted as given. */
    CONSUMABLE,
    /** Granted by an admin or through the API; granted as given. */
    ADMINISTRATIVE
  }
}
