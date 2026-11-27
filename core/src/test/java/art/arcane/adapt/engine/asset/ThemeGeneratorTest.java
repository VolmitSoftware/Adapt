package art.arcane.adapt.engine.asset;

import art.arcane.adapt.client.theme.DressSlot;
import art.arcane.adapt.client.theme.DressSprite;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemeSprite;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeGeneratorTest {
  private static final SkillLook CRYSTAL = new SkillLook(Id.of("alchemist", "alchemy"), 0x7A3FD1, Id.minecraft("brewing_stand"));
  private static final SkillLook GROVE = new SkillLook(Id.adapt("gardening"), 0x3FAF4A, Id.minecraft("oak_sapling"));

  @Test
  void aGeneratedThemeParsesWithTheRealParser() {
    for (SkillLook look : new SkillLook[]{CRYSTAL, GROVE}) {
      ThemeAssets theme = ThemeGenerator.generate(look, null);
      ThemeKit kit = ThemeKit.parse(look.skill().namespace(), new String(theme.kitJson(), StandardCharsets.UTF_8));

      assertThat(theme.generated()).isTrue();
      assertThat(kit.foot()).isNotNull();
      assertThat(kit.top()).isNotNull();
      assertThat(kit.sprite("mat")).isNotNull();
      assertThat(kit.sprite("matT")).isNotNull();
      assertThat(kit.sprite("div")).isNotNull();
      assertThat(kit.color('p') & 0xFFFFFF).isEqualTo(look.rgb());
      assertThat(kit.dress(DressSlot.HANG)).hasSize(3);
      assertThat(kit.dress(DressSlot.SIDE)).hasSize(2);
      assertThat(kit.dress(DressSlot.BIT)).hasSize(3);
    }
  }

  @Test
  void theKitNamesItsOwnAtlasAndSkill() {
    ThemeKit addon = ThemeKit.parse(CRYSTAL.skill().namespace(), new String(ThemeGenerator.generate(CRYSTAL, null).kitJson(), StandardCharsets.UTF_8));
    ThemeKit builtin = ThemeKit.parse(GROVE.skill().namespace(), new String(ThemeGenerator.generate(GROVE, null).kitJson(), StandardCharsets.UTF_8));

    assertThat(addon.skill()).isEqualTo("alchemist:alchemy");
    assertThat(addon.texture().id()).isEqualTo("alchemist:textures/gui/theme/alchemy.png");
    assertThat(builtin.skill()).isEqualTo("gardening");
    assertThat(builtin.texture().id()).isEqualTo("adapt:textures/gui/theme/gardening.png");
  }

  @Test
  void theSameLookGivesTheSameBytesAndAnotherColourDiffers() {
    ThemeAssets first = ThemeGenerator.generate(CRYSTAL, null);
    ThemeAssets second = ThemeGenerator.generate(new SkillLook(CRYSTAL.skill(), CRYSTAL.rgb(), CRYSTAL.icon()), null);
    ThemeAssets recoloured = ThemeGenerator.generate(new SkillLook(CRYSTAL.skill(), 0xD13F3F, CRYSTAL.icon()), null);

    assertThat(second.kitJson()).isEqualTo(first.kitJson());
    assertThat(second.atlasPng()).isEqualTo(first.atlasPng());
    assertThat(second.hash()).isEqualTo(first.hash());
    assertThat(recoloured.hash()).isNotEqualTo(first.hash());
  }

  @Test
  void everySpriteSitsInsideTheAtlasAndDressMasksMatchItsPixels() {
    ThemeAssets theme = ThemeGenerator.generate(GROVE, null);
    JsonObject json = JsonParser.parseString(new String(theme.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
    int[] size = PngImages.size(theme.atlasPng());
    assertThat(size).containsExactly(json.getAsJsonArray("atlas").get(0).getAsInt(), json.getAsJsonArray("atlas").get(1).getAsInt());
    for (Map.Entry<String, JsonElement> sprite : json.getAsJsonObject("sprites").entrySet()) {
      int x = sprite.getValue().getAsJsonArray().get(0).getAsInt();
      int y = sprite.getValue().getAsJsonArray().get(1).getAsInt();
      int w = sprite.getValue().getAsJsonArray().get(2).getAsInt();
      int h = sprite.getValue().getAsJsonArray().get(3).getAsInt();
      assertThat(x + w).as(sprite.getKey()).isLessThanOrEqualTo(size[0]);
      assertThat(y + h).as(sprite.getKey()).isLessThanOrEqualTo(size[1]);
    }
    ThemeKit kit = ThemeKit.parse(GROVE.skill().namespace(), new String(theme.kitJson(), StandardCharsets.UTF_8));
    for (DressSlot slot : DressSlot.values()) {
      for (DressSprite piece : kit.dress(slot)) {
        ThemeSprite frame = piece.frame(0, false);
        assertThat(frame.width()).isEqualTo(piece.width());
        assertThat(frame.height()).isEqualTo(piece.height());
      }
    }
  }

  @Test
  void theIconItemPicksTheSet() {
    assertThat(ThemeGenerator.organic(Id.minecraft("oak_sapling"))).isTrue();
    assertThat(ThemeGenerator.organic(Id.minecraft("diamond_sword"))).isFalse();
  }

  @Test
  void theTopBandIsClosedAndRunsPastTheTopLineAsAuthoredBandsDo() {
    for (SkillLook look : new SkillLook[]{CRYSTAL, GROVE}) {
      ThemeAssets theme = ThemeGenerator.generate(look, null);
      JsonObject json = JsonParser.parseString(new String(theme.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
      JsonObject ground = json.getAsJsonObject("top").getAsJsonObject("ground");
      assertThat(ground.get("h").getAsInt()).isEqualTo(ThemeGenerator.TOP_HEIGHT);
      assertThat(ground.get("past").getAsInt()).isEqualTo(ThemeGenerator.TOP_PAST);
      int[] atlas = PngImages.decode(theme.atlasPng());
      int width = PngImages.size(theme.atlasPng())[0];
      for (String strip : new String[]{"tg/0", "tg/1", "tg/2", "tg/start", "tg/end", "tg/fill"}) {
        int[] rect = rect(json, strip);
        assertThat(rect[3]).as(strip).isEqualTo(ThemeGenerator.TOP_HEIGHT);
        for (int y = 0; y < rect[3]; y++) {
          for (int x = 0; x < rect[2]; x++) {
            assertThat(atlas[(rect[1] + y) * width + rect[0] + x] >>> 24).as(strip + " at " + x + "," + y).isEqualTo(0xFF);
          }
        }
      }
    }
  }

  @Test
  void theCornerPlaquesFrameTheSkillIconFacingTheSameWay() {
    int[] pixels = new int[16 * 16];
    pixels[3 * 16 + 2] = 0xFF123456;
    ThemeAssets theme = ThemeGenerator.generate(GROVE, PngImages.encode(16, 16, pixels));
    JsonObject json = JsonParser.parseString(new String(theme.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
    int[] atlas = PngImages.decode(theme.atlasPng());
    int width = PngImages.size(theme.atlasPng())[0];
    for (String sprite : new String[]{"tc/emblem/0", "tc/emblem/0f"}) {
      int[] rect = rect(json, sprite);
      assertThat(atlas[(rect[1] + 4) * width + rect[0] + 3]).as(sprite).isEqualTo(0xFF123456);
    }
    ThemeAssets plain = ThemeGenerator.generate(GROVE, null);
    assertThat(ThemeGenerator.generate(GROVE, new byte[]{1, 2, 3}).hash()).isEqualTo(plain.hash());
    assertThat(theme.hash()).isNotEqualTo(plain.hash());
  }

  @Test
  void aPngReadsBackToItsPixels() {
    int[] pixels = new int[5 * 3];
    for (int index = 0; index < pixels.length; index++) {
      pixels[index] = index % 4 == 0 ? 0 : 0xFF000000 | index * 0x0F1E2D;
    }
    assertThat(PngImages.decode(PngImages.encode(5, 3, pixels))).containsExactly(pixels);
    assertThat(PngImages.decode(new byte[]{1, 2, 3})).isNull();
  }

  private static int[] rect(JsonObject json, String sprite) {
    JsonElement rect = json.getAsJsonObject("sprites").get(sprite);
    assertThat(rect).as(sprite).isNotNull();
    int[] values = new int[4];
    for (int index = 0; index < 4; index++) {
      values[index] = rect.getAsJsonArray().get(index).getAsInt();
    }
    return values;
  }
}
