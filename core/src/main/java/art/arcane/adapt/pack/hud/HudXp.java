package art.arcane.adapt.pack.hud;

import java.util.Objects;

/** The strip's xp line: a skill's plain name, its colour (0xRRGGBB), level, progress into it and the xp shown gained. */
public record HudXp(String skillId, String name, int colour, int level, double progress, double gained) {
  public HudXp {
    Objects.requireNonNull(skillId, "skillId");
    Objects.requireNonNull(name, "name");
    colour &= 0xFFFFFF;
    progress = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
    gained = Double.isFinite(gained) ? Math.max(0.0, gained) : 0.0;
  }

  /** The same line with {@code more} xp shown gained and the newest level and progress. */
  public HudXp add(int newLevel, double newProgress, double more) {
    return new HudXp(skillId, name, colour, newLevel, newProgress, gained + more);
  }
}
