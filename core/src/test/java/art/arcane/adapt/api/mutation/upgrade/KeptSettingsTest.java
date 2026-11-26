package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.client.view.XpGuideViews;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.localization.catalog.XpGuideMessages;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A kept original's settings belong to whoever keeps it through a mutation: they can be changed as if it were learned,
 * they take effect, and they outlive a relog and a mutation reset. A player who neither learned nor keeps it is refused.
 * A mutated skill's XP guide combines both parents' lines with the mutation's rules.
 */
class KeptSettingsTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static String lead;
  private static String partner;
  private static Adaptation<?> kept;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    for (SkillBlocks blocks : SkillBlockCatalog.all()) {
      List<Adaptation<?>> originals = SkillUpgrades.kept(registry.getSkill(blocks.skillId()));
      if (!originals.isEmpty()) {
        lead = blocks.skillId();
        kept = originals.getFirst();
        break;
      }
    }
    partner = SkillBlockCatalog.all().stream().map(SkillBlocks::skillId).filter((String id) -> !id.equals(lead)).findFirst().orElseThrow();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  private static AdaptPlayer mutated(String name) {
    FakePlayer player = platform.player(name, 0.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    new MutationKit(adapt, profile).mutate(lead, partner);
    platform.scheduler().advance(20);
    return profile;
  }

  @Test
  void aMutatedHolderSetsAKeptOriginalsSettingAndItTakesEffect() {
    AdaptPlayer holder = mutated("KeptHolder");
    assertThat(kept.getLevel(holder)).isPositive();
    assertThat(kept.isPlayerEnabled(holder.getPlayer())).isTrue();

    assertThat(PlayerPreferences.set(kept, holder, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isTrue();

    assertThat(holder.getData().getPreferences().get(kept.getName(), CommonPreferences.ENABLED.id())).isEqualTo("OFF");
    assertThat(kept.isPlayerEnabled(holder.getPlayer())).isFalse();
    PlayerView view = PlayerViews.build(registry, holder);
    assertThat(view.preferences()).contains(new PreferenceValue(
        new PreferenceKey(PreferenceOwner.ADAPTATION, kept.getName(), CommonPreferences.ENABLED.id()), "OFF"));
  }

  @Test
  void aPlayerWhoNeitherLearnedNorKeepsItIsRefused() {
    FakePlayer player = platform.player("Stranger", 4.5D, 61D, 0.5D);
    AdaptPlayer stranger = adapt.join(player);
    platform.scheduler().advance(20);
    assertThat(kept.getLevel(stranger)).isZero();

    assertThat(PlayerPreferences.set(kept, stranger, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isFalse();
    assertThat(stranger.getData().getPreferences().get(kept.getName(), CommonPreferences.ENABLED.id())).isNull();
  }

  @Test
  void theSettingOutlivesARelogAndAMutationReset() {
    AdaptPlayer holder = mutated("KeptKeeper");
    assertThat(PlayerPreferences.set(kept, holder, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isTrue();

    PlayerData reloaded = PlayerData.fromJson(holder.getData().toJson(true));
    assertThat(reloaded.getPreferences().get(kept.getName(), CommonPreferences.ENABLED.id())).isEqualTo("OFF");

    SkillMutations.unmutateAll(holder.getData(), "Reset");
    assertThat(holder.getData().getPreferences().get(kept.getName(), CommonPreferences.ENABLED.id())).isEqualTo("OFF");
  }

  @Test
  void aMutatedSkillsGuideCombinesBothParentsWithTheRules() {
    AdaptPlayer holder = mutated("GuideReader");
    MutatedSkill mutated = new MutationKit(adapt, holder).mutated(lead, partner);

    XpGuideView guide = XpGuideViews.guide(registry, mutated);

    assertThat(guide.parents()).containsExactly(lead, partner);
    assertThat(guide.sections()).hasSize(3);
    assertThat(guide.sections().get(0).skillId()).isEqualTo(lead);
    assertThat(guide.sections().get(0).lines()).hasSize(XpGuideMessages.lines(lead).size());
    assertThat(guide.sections().get(1).skillId()).isEqualTo(partner);
    assertThat(guide.sections().get(1).lines()).hasSize(XpGuideMessages.lines(partner).size());
    assertThat(guide.sections().get(2).skillId()).isEmpty();
    assertThat(guide.sections().get(2).lines()).hasSize(3).anyMatch((String line) -> line.contains("25%"));
  }
}
