package art.arcane.adapt.pack.hud;

import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.engine.notification.HudStatusSpec;
import art.arcane.adapt.pack.manifest.PackSprites;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class HudStripTest {
  private static final String RUN = "<reset><font:adapt:hud><shadow:#00000000>";
  private static final IconRef DASH = new IconRef("minecraft:feather", 0, null, "adapt:textures/gui/adaptation/agility-air-dash.png");
  private static final IconRef PLAIN = IconRef.item("minecraft:bone");
  private static final Map<String, String> GLYPHS = Map.of("adapt:gui/adaptation/agility-air-dash", "\uE100");
  private static final Function<String, String> NO_GLYPHS = (String spriteId) -> null;

  @Test
  void shiftsAddUpToThePixelCount() {
    assertThat(HudGlyphs.shift(0)).isEmpty();
    assertThat(HudGlyphs.shift(-19)).isEqualTo("\uE004\uE001\uE000");
    assertThat(HudGlyphs.shift(7)).isEqualTo("\uE022\uE021\uE020");
    assertThat(HudGlyphs.shift(300)).isEqualTo("\uE028\uE025\uE023\uE022");
  }

  @Test
  void stripWithCooldownChargesXpAndNoticeMatchesGolden() {
    List<HudStatusSpec> abilities = List.of(
        HudStatusSpec.cooldown("agility", DASH, "Air Dash", 0L, 8_000L),
        HudStatusSpec.charges("hunter", PLAIN, "Snares", 2, 3));
    HudXp xp = new HudXp("herbalism", "Herbalism", "adapt:gui/skill/herbalism", 0x4DB344, 46, 0.5, 35.4);

    String strip = HudStrip.compose(abilities, xp, List.of("<yellow>Saved"), GLYPHS::get, 3_000L);

    assertThat(strip).isEqualTo(
        RUN + "\uE040\uE004\uE001\uE000\uE100\uE004\uE000\uE045"
            + RUN + "\uE021\uE020"
            + RUN + "\uE040\uE004\uE001\uE000\uE06F\uE002\uE001<reset><white>2"
            + RUN + "\uE022\uE021\uE020"
            + "<reset><color:#4db344>Herbalism 46" + RUN + "\uE022\uE049\uE005\uE004\uE003\uE002\uE000<color:#4db344>\uE05F"
            + "<reset><color:#aaaaaa> +35"
            + RUN + "\uE023<reset><yellow>Saved");
  }

  @Test
  void onlyThreeAbilitiesAreShownAndNamesAreEscaped() {
    HudStatusSpec toggle = HudStatusSpec.toggle("stealth", PLAIN, "Hide", true);
    String strip = HudStrip.compose(List.of(toggle, toggle, toggle, toggle), new HudXp("x", "<b>X", "", 0xFFFFFF, 1, 0, 0),
        List.of(), NO_GLYPHS, 0L);

    assertThat(strip.chars().filter((int c) -> c == 0xE040).count()).isEqualTo(3);
    assertThat(strip).contains("\\<b>X 1").doesNotContain(" +");
  }

  @Test
  void emptyStripIsEmpty() {
    assertThat(HudStrip.compose(List.of(), null, List.of(" "), NO_GLYPHS, 0L)).isEmpty();
  }

  @Test
  void wipeFollowsEachGlyphKind() {
    assertThat(HudStrip.wipeStep(HudStatusSpec.cooldown("s", PLAIN, "", 0L, 8_000L), 0L)).isEqualTo(8);
    assertThat(HudStrip.wipeStep(HudStatusSpec.cooldown("s", PLAIN, "", 0L, 8_000L), 7_999L)).isEqualTo(1);
    assertThat(HudStrip.wipeStep(HudStatusSpec.cooldown("s", PLAIN, "", 0L, 8_000L), 8_000L)).isZero();
    assertThat(HudStrip.wipeStep(HudStatusSpec.duration("s", PLAIN, "", 0L, 8_000L, 0, 0), 0L)).isZero();
    assertThat(HudStrip.wipeStep(HudStatusSpec.duration("s", PLAIN, "", 0L, 8_000L, 0, 0), 6_000L)).isEqualTo(6);
    assertThat(HudStrip.wipeStep(HudStatusSpec.duration("s", PLAIN, "", 0L, 0L, 0, 0), 99_000L)).isZero();
    assertThat(HudStrip.wipeStep(HudStatusSpec.charges("s", PLAIN, "", 0, 3), 0L)).isEqualTo(8);
    assertThat(HudStrip.wipeStep(HudStatusSpec.charges("s", PLAIN, "", 1, 3, 0L, 4_000L), 1_000L)).isEqualTo(6);
    assertThat(HudStrip.wipeStep(HudStatusSpec.toggle("s", PLAIN, "", false), 0L)).isEqualTo(8);
    assertThat(HudStrip.wipeStep(HudStatusSpec.toggle("s", PLAIN, "", true), 0L)).isZero();
  }

  @Test
  void timedStatusesExpireAndOthersStay() {
    assertThat(HudStrip.expired(HudStatusSpec.cooldown("s", PLAIN, "", 0L, 1_000L), 1_000L)).isTrue();
    assertThat(HudStrip.expired(HudStatusSpec.duration("s", PLAIN, "", 0L, 0L, 0, 0), 1_000_000L)).isFalse();
    assertThat(HudStrip.expired(HudStatusSpec.charges("s", PLAIN, "", 0, 3, 0L, 1_000L), 5_000L)).isFalse();
  }

  @Test
  void nextChangeIsTheNextWipeBoundary() {
    HudStatusSpec cooldown = HudStatusSpec.cooldown("s", PLAIN, "", 0L, 8_000L);
    assertThat(HudStrip.nextChange(List.of(cooldown), 0L)).isEqualTo(1_000L);
    assertThat(HudStrip.nextChange(List.of(cooldown), 3_000L)).isEqualTo(4_000L);
    assertThat(HudStrip.nextChange(List.of(cooldown), 7_500L)).isEqualTo(8_000L);
    assertThat(HudStrip.nextChange(List.of(cooldown), 8_000L)).isEqualTo(Long.MAX_VALUE);
    assertThat(HudStrip.wipeStep(cooldown, 999L)).isEqualTo(8);
    assertThat(HudStrip.wipeStep(cooldown, 1_000L)).isEqualTo(7);
  }

  @Test
  void spriteIdsMapToPackIdsAndItemModels() {
    assertThat(PackSprites.packId("adapt:textures/gui/skill/herbalism.png")).isEqualTo("adapt:gui/skill/herbalism");
    assertThat(PackSprites.packId("addon:gui/skill/x|adapt:textures/gui/mark/knowledge.png#ff0000"))
        .isEqualTo("addon:gui/skill/x");
    assertThat(PackSprites.packId(null)).isNull();
    assertThat(PackSprites.itemModel("adapt:textures/gui/skill/herbalism.png").toString())
        .isEqualTo("adapt:adapt_ui/gui/skill/herbalism");
  }
}
