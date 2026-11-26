package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.mutation.blocks.BarterGesture;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.MutationOverrides;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MutatedTextLocalesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @Test
  void everyLocaleComposesEveryPairFromItsOwnParts() throws IOException {
    for (String locale : VolmitLocales.nonEnglish()) {
      MutatedText.Words words = words(locale);
      for (SkillBlocks lead : SkillBlockCatalog.all()) {
        for (SkillBlocks partner : SkillBlockCatalog.all()) {
          if (lead == partner) {
            continue;
          }
          assertComposed(locale, MutatedText.mutationName(words, lead.skillId(), partner.skillId()),
              MutatedText.mutationName(MutatedText.ENGLISH, lead.skillId(), partner.skillId()));
          for (Recipe recipe : Recipe.values()) {
            assertComposed(locale, MutatedText.adaptationName(words, partner.skillId(), recipe),
                MutatedText.adaptationName(MutatedText.ENGLISH, partner.skillId(), recipe));
            assertComposed(locale, MutatedText.description(words, lead, partner, recipe),
                MutatedText.description(MutatedText.ENGLISH, lead, partner, recipe));
          }
        }
        for (BlockSlot slot : BlockSlot.values()) {
          assertComposed(locale, MutatedText.blockName(words, lead.ref(slot)),
              MutatedText.blockName(MutatedText.ENGLISH, lead.ref(slot)));
        }
      }
    }
  }

  @Test
  void germanAndJapaneseComposeSilentDuelistFromTheirOwnParts() throws IOException {
    MutatedText.Words german = words("de_DE");
    MutatedText.Words japanese = words("ja-JP");
    SkillBlocks swords = SkillBlockCatalog.require("swords");
    SkillBlocks stealth = SkillBlockCatalog.require("stealth");

    assertThat(MutatedText.mutationName(german, "swords", "stealth")).isEqualTo("Schatten-Duellant");
    assertThat(MutatedText.adaptationName(german, "stealth", Recipe.GRAFT)).isEqualTo("Schatten-Pfropfung");
    assertThat(MutatedText.description(german, swords, stealth, Recipe.GRAFT)).isEqualTo(
        "Wenn du einen kritischen Schwerttreffer landest, gilt: Mobs im Umkreis von 8 Blöcken verlieren 3 s lang deine Spur.");
    assertThat(MutatedText.mutationName(japanese, "swords", "stealth")).isEqualTo("静寂の決闘者");
    assertThat(MutatedText.adaptationName(japanese, "stealth", Recipe.GRAFT)).isEqualTo("静寂の接ぎ木");
    assertThat(MutatedText.description(japanese, swords, stealth, Recipe.GRAFT))
        .isEqualTo("剣でクリティカルヒットを当てると、8ブロック以内のモブが3秒間自分を見失う。");
  }

  @Test
  void overrideRowWinsOverTheComposedTextInFrench() throws IOException {
    MutatedText.Words french = words("fr_FR");
    SkillBlocks swords = SkillBlockCatalog.require("swords");
    SkillBlocks hunter = SkillBlockCatalog.require("hunter");
    MutationOverride override = MutationOverrides.find("swords", "hunter", Recipe.ECHO);

    String shown = MutatedText.description(french, swords, hunter, Recipe.ECHO);

    assertThat(shown).isEqualTo(french.text(MutatedMessages.override(override)))
        .isNotEqualTo(MutatedText.generated(french, swords, hunter, Recipe.ECHO))
        .isNotEqualTo(override.description());
  }

  @Test
  void barterOverrideRowKeepsThePairsGestureInGerman() throws IOException {
    MutatedText.Words german = words("de_DE");
    MutationOverride override = MutationOverrides.find("herbalism", "unarmed", Recipe.BARTER);
    String gesture = german.text(MutatedMessages.gesture(BarterGesture.of("herbalism", "unarmed")));

    String shown = MutatedText.description(german, SkillBlockCatalog.require("herbalism"),
        SkillBlockCatalog.require("unarmed"), Recipe.BARTER);

    assertThat(shown).contains(gesture)
        .isEqualTo(german.text(MutatedMessages.override(override)).replace("{gesture}", gesture));
  }

  private static void assertComposed(String locale, String composed, String english) {
    assertThat(composed).describedAs("%s: %s", locale, english)
        .doesNotContain("{", "}")
        .isNotEqualTo(english);
  }

  private static MutatedText.Words words(String locale) throws IOException {
    Path file = RESOURCE_ROOT.resolve(locale + ".toml");
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(AdaptMessages.catalog(),
        List.of(AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file))), PluralSelector.oneOther()));
    return key -> ((TextValue) snapshot.value(key)).template();
  }
}
