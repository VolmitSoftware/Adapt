package art.arcane.adapt.util.director.context;

import art.arcane.adapt.engine.addon.AddonContent;
import art.arcane.adapt.engine.mutation.pair.PairClaims;
import art.arcane.adapt.engine.addon.SkillSource;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Command completion follows the catalog: a skill registered after startup completes without a rebuild call. */
class AdaptationListingHandlerTest {
  @TempDir
  Path data;

  @Test
  void aSkillRegisteredLaterIsOfferedForCompletion() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of(SkillSwords.class))) {
      AdaptationListingHandler.initializeAdaptationListings();
      assertThat(AdaptationListingHandler.getSkillProvider()).extracting(AdaptationListingHandler.SkillProvider::name)
          .contains("adapt:swords").doesNotContain("adapt:agility");

      adapt.core().getAdaptServer().getSkillRegistry().install(new AddonContent(Id.ADAPT,
          List.of(new SkillSource(Id.adapt("agility"), Id.ADAPT, SkillAgility::new)), List.of(), List.of(),
          Optional.empty(), List.of(), List.of(), List.of(), PairClaims.none(Id.ADAPT), List.of(), List.of()));

      assertThat(AdaptationListingHandler.getSkillProvider()).extracting(AdaptationListingHandler.SkillProvider::name)
          .contains("adapt:swords", "adapt:agility");
      assertThat(AdaptationListingHandler.getAdaptionListings()).extracting(AdaptationListingHandler.AdaptationList::name)
          .contains("skill:adapt:agility");
    }
  }
}
