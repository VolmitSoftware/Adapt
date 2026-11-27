package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.Toast;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** What the engine does for the HUD: action bar lines, ambient lines, glyphs and toasts. */
@ApiStatus.Internal
public interface HudRuntime {
  void actionBar(PlayerRef player, @Nullable Adaptation<?> source, String message);

  void ambient(PlayerRef player, String purpose, String message);

  void clearAmbient(PlayerRef player, String purpose);

  /** One call for all four glyph kinds; fields a kind does not use are 0 or false. Duration takes stacks as lit/max. */
  boolean glyph(UUID player, Adaptation<?> source, HudGlyph glyph, int lit, int max, long startedAtMillis,
                long durationMillis, boolean on);

  void clearGlyph(UUID player, Adaptation<?> source, HudGlyph glyph);

  boolean capable(UUID player);

  void toast(PlayerRef player, Toast toast);
}
