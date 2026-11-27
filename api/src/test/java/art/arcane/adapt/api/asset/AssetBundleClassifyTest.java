package art.arcane.adapt.api.asset;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetBundleClassifyTest {
  @Test
  void iconFoldersAreTheSixteenPixelFamilies() {
    assertThat(AssetBundle.classify("demo", "textures/gui/skill/alchemy.png"))
        .isEqualTo(new AssetEntry(AssetKind.SPRITE, Id.of("demo", "gui/skill/alchemy"), "textures/gui/skill/alchemy.png"));
    assertThat(AssetBundle.classify("demo", "textures/gui/adaptation/alchemy/transmute.png").kind())
        .isEqualTo(AssetKind.SPRITE);
    assertThat(AssetBundle.classify("demo", "textures/gui/preference/on.png").kind())
        .isEqualTo(AssetKind.PREFERENCE_ICON);
    assertThat(AssetBundle.classify("demo", "textures/gui/emblem/alchemy.png").kind()).isEqualTo(AssetKind.EMBLEM);
    assertThat(AssetBundle.classify("demo", "textures/gui/trim/alchemy.png").kind()).isEqualTo(AssetKind.TRIM);
  }

  @Test
  void otherGuiAndFontArtIsAGuiTextureOfAnySize() {
    for (String path : new String[] {"textures/gui/kit/button.png", "textures/gui/toast/frame.png",
        "textures/gui/hud/bar.png", "textures/gui/glyph/echo.png", "textures/font/runes.png"}) {
      AssetEntry entry = AssetBundle.classify("demo", path);
      assertThat(entry.kind()).as(path).isEqualTo(AssetKind.GUI_TEXTURE);
      assertThat(entry.id()).isEqualTo(Id.of("demo", path.substring("textures/".length(), path.length() - 4)));
    }
  }

  @Test
  void themesModelsAndTheRest() {
    assertThat(AssetBundle.classify("demo", "textures/gui/theme/alchemy.png"))
        .isEqualTo(new AssetEntry(AssetKind.THEME_ATLAS, Id.of("demo", "alchemy"), "textures/gui/theme/alchemy.png"));
    assertThat(AssetBundle.classify("demo", "themes/alchemy.json").id()).isEqualTo(Id.of("demo", "alchemy"));
    assertThat(AssetBundle.classify("demo", "models/item/stone.json"))
        .isEqualTo(new AssetEntry(AssetKind.MODEL, Id.of("demo", "item/stone"), "models/item/stone.json"));
    assertThat(AssetBundle.classify("demo", "textures/item/stone.png"))
        .isEqualTo(new AssetEntry(AssetKind.OTHER, Id.of("demo", "item/stone"), "textures/item/stone.png"));
    assertThat(AssetBundle.classify("demo", "textures/gui/kit/button.json").kind()).isEqualTo(AssetKind.OTHER);
  }
}
