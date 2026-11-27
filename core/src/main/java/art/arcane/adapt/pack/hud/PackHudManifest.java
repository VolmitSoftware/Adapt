package art.arcane.adapt.pack.hud;

import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.manifest.PackManifests;
import art.arcane.adapt.platform.value.Id;

/**
 * What the strip and pack toasts read from the published pack's manifest: the {@code hud.icons} glyphs and the kits'
 * toast icons. {@link #current()} views
 * {@link PackManifests}; {@link #NONE} knows no pack.
 */
public interface PackHudManifest {
  PackHudManifest NONE = new PackHudManifest() {
    @Override
    public String icon(String spriteId) {
      return null;
    }

    @Override
    public Id toastModel(String spriteId) {
      return null;
    }
  };

  static PackHudManifest current() {
    return PackManifests.installed().current().map(PackHudManifest::of).orElse(NONE);
  }

  /** The HUD's view of {@code manifest}. */
  static PackHudManifest of(PackManifest manifest) {
    return new PackHudManifest() {
      @Override
      public String icon(String spriteId) {
        return manifest.hudGlyphs().get(spriteId);
      }

      @Override
      public Id toastModel(String spriteId) {
        return manifest.toastModels().get(spriteId);
      }
    };
  }

  /** The {@code adapt:hud} glyph that draws sprite {@code spriteId} ({@code ns:gui/<kind>/<name>}), or null. */
  String icon(String spriteId);

  /** The kit toast icon (the skill icon in the kit's corners) for a skill's own sprite, or null for other sprites. */
  Id toastModel(String spriteId);
}
