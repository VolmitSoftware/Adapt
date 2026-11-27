package art.arcane.adapt.localization;

import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MutatedTextLocalesTest {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @TempDir
  static Path dataFolder;
  private static TestAdapt adapt;
  private static final Map<Id, Skill<?>> skills = new LinkedHashMap<>();
  private static PairRegistry claims;

  @BeforeAll
  static void startBuiltInSkills() {
    List<Class<? extends SimpleSkill<?>>> types = BuiltinAddon.skills().stream()
        .<Class<? extends SimpleSkill<?>>>map(BuiltinAddon.Entry::type).toList();
    adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), types);
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    for (Skill<?> skill : registry.getSkills()) {
      skills.put(skill.getId(), skill);
    }
    claims = registry.getPairRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void everyLocaleComposesEveryPairFromItsOwnParts() throws IOException {
    for (String locale : VolmitLocales.nonEnglish()) {
      MutatedText.Words words = words(locale);
      for (Skill<?> lead : skills.values()) {
        for (Skill<?> partner : skills.values()) {
          if (lead == partner) {
            continue;
          }
          MutatedText.Parents parents = new MutatedText.Parents(lead, partner, claims);
          assertComposed(locale, MutatedText.mutationName(words, parents),
              MutatedText.mutationName(MutatedText.ENGLISH, parents));
          for (Recipe recipe : Recipe.values()) {
            assertComposed(locale, MutatedText.adaptationName(words, parents, recipe),
                MutatedText.adaptationName(MutatedText.ENGLISH, parents, recipe));
            assertComposed(locale, MutatedText.description(words, parents, recipe),
                MutatedText.description(MutatedText.ENGLISH, parents, recipe));
          }
        }
        for (BlockSlot slot : BlockSlot.values()) {
          assertComposed(locale, MutatedText.blockName(words, lead, slot), MutatedText.blockName(MutatedText.ENGLISH, lead, slot));
        }
      }
    }
  }

  @Test
  void germanAndJapaneseComposeSilentDuelistFromTheirOwnParts() throws IOException {
    MutatedText.Words german = words("de_DE");
    MutatedText.Words japanese = words("ja-JP");
    MutatedText.Parents swordsStealth = parents("swords", "stealth");

    assertThat(MutatedText.mutationName(german, swordsStealth)).isEqualTo("Schatten-Duellant");
    assertThat(MutatedText.adaptationName(german, swordsStealth, Recipe.GRAFT)).isEqualTo("Schatten-Pfropfung");
    assertThat(MutatedText.description(german, swordsStealth, Recipe.GRAFT)).isEqualTo(
        "Wenn du einen kritischen Schwerttreffer landest, gilt: Mobs im Umkreis von 8 Blöcken verlieren 3 s lang deine Spur.");
    assertThat(MutatedText.mutationName(japanese, swordsStealth)).isEqualTo("静寂の決闘者");
    assertThat(MutatedText.adaptationName(japanese, swordsStealth, Recipe.GRAFT)).isEqualTo("静寂の接ぎ木");
    assertThat(MutatedText.description(japanese, swordsStealth, Recipe.GRAFT))
        .isEqualTo("剣でクリティカルヒットを当てると、8ブロック以内のモブが3秒間自分を見失う。");
  }

  @Test
  void overrideRowWinsOverTheComposedTextInFrench() throws IOException {
    MutatedText.Words french = words("fr_FR");
    MutatedText.Parents swordsHunter = parents("swords", "hunter");
    TextKey override = pairText(swordsHunter, Recipe.ECHO);

    String shown = MutatedText.description(french, swordsHunter, Recipe.ECHO);

    assertThat(shown).isEqualTo(french.text(override))
        .isNotEqualTo(MutatedText.generated(french, swordsHunter, Recipe.ECHO))
        .isNotEqualTo(override.english());
  }

  @Test
  void barterOverrideRowKeepsThePairsGestureInGerman() throws IOException {
    MutatedText.Words german = words("de_DE");
    MutatedText.Parents herbalismUnarmed = parents("herbalism", "unarmed");
    BarterGesture pairGesture = MutatedText.gesture(herbalismUnarmed).orElseThrow();
    String gesture = german.text(MutatedMessages.gesture(pairGesture));

    String shown = MutatedText.description(german, herbalismUnarmed, Recipe.BARTER);

    assertThat(shown).contains(gesture)
        .isEqualTo(german.text(pairText(herbalismUnarmed, Recipe.BARTER)).replace("{gesture}", gesture));
  }

  private static MutatedText.Parents parents(String lead, String partner) {
    return new MutatedText.Parents(skills.get(Id.adapt(lead)), skills.get(Id.adapt(partner)), claims);
  }

  private static TextKey pairText(MutatedText.Parents parents, Recipe recipe) {
    PairText text = claims.text(parents.slot(recipe)).orElseThrow().value();
    return (TextKey) AdaptLanguage.declared(text.description().key());
  }

  private static void assertComposed(String locale, String composed, String english) {
    assertThat(composed).describedAs("%s: %s", locale, english)
        .doesNotContain("{", "}")
        .isNotEqualTo(english);
  }

  private static MutatedText.Words words(String locale) throws IOException {
    Path file = RESOURCE_ROOT.resolve(locale + ".toml");
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(AdaptLanguage.builtInCatalog(),
        List.of(AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file))), PluralSelector.oneOther()));
    return (TextKey key) -> ((TextValue) snapshot.value(key)).template();
  }
}
