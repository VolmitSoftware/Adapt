package art.arcane.adapt.client.view;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientViewBuildersTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Viewer", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRift.class, SkillAgility.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void catalogDescribesEverySkillAndAdaptation() throws ClientProtocolException {
    CatalogView catalog = CatalogViews.build(registry, player.uuid(), List.of(), false);

    assertThat(catalog.revision()).isEqualTo(registry.getCatalogRevision());
    assertThat(catalog.locale()).isNotBlank();
    assertThat(catalog.skills()).extracting(SkillView::id).containsExactlyInAnyOrder("rift", "agility");
    SkillView rift = skill(catalog, "rift");
    assertThat(rift.adaptations()).hasSize(registry.getSkill("rift").getAdaptations().size());
    assertThat(rift.preferences()).extracting(PreferenceView::id).containsExactly("enabled");
    assertThat(rift.color()).isBetween(0, 0xFFFFFF);

    AdaptationView blink = adaptation(rift, "rift-blink");
    assertThat(blink.skillId()).isEqualTo("rift");
    assertThat(blink.knowledgeCosts()).hasSize(blink.maxLevel()).allMatch((Integer cost) -> cost > 0);
    assertThat(blink.statLines()).hasSize(blink.maxLevel());
    assertThat(blink.icon().itemId()).contains(":");
    PreferenceView landing = blink.preferences().stream()
        .filter((PreferenceView preference) -> preference.id().equals("landing")).findFirst().orElseThrow();
    assertThat(landing.choices()).extracting(PreferenceChoiceView::value).contains("SERVER", "NEAR", "NONE");
    assertThat(landing.label()).isNotBlank();

    ServerFrame frame = new ServerFrame.Catalog(catalog);
    assertThat(FrameCodec.decodeServer(FrameCodec.encodeServer(frame))).isEqualTo(frame);
  }

  @Test
  void playerViewReflectsLearnedAdaptationsAndPreferences() throws ClientProtocolException {
    Adaptation<?> blink = adaptation(registry.getSkill("rift"), "rift-blink");
    profile.getData().getSkillLine("rift").setAdaptation(blink, 1);
    profile.getData().getSkillLine("rift").giveKnowledge(7L);
    profile.getData().getPreferences().set("rift-blink", "landing", "NEAR");

    PlayerView view = PlayerViews.build(registry, profile);

    assertThat(view.skills()).extracting(SkillLineView::skillId).containsExactlyInAnyOrder("rift", "agility");
    SkillLineView rift = view.skills().stream()
        .filter((SkillLineView line) -> line.skillId().equals("rift")).findFirst().orElseThrow();
    assertThat(rift.knowledge()).isEqualTo(7L);
    assertThat(view.adaptationLevels()).containsEntry("rift-blink", 1);
    assertThat(view.header().powerUsed()).isEqualTo(1);
    assertThat(view.preferences()).contains(
        new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, "rift-blink", "landing"), "NEAR"),
        new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, "rift", "enabled"), "ON"));
    assertThat(view.preferences()).extracting(PreferenceValue::key).isSorted();

    ServerFrame frame = new ServerFrame.Player(view);
    assertThat(FrameCodec.decodeServer(FrameCodec.encodeServer(frame))).isEqualTo(frame);
  }

  @Test
  void unlearningShowsUpInTheDelta() {
    Adaptation<?> blink = adaptation(registry.getSkill("rift"), "rift-blink");
    profile.getData().getSkillLine("rift").setAdaptation(blink, 1);
    PlayerView before = PlayerViews.build(registry, profile);

    profile.getData().getSkillLine("rift").setAdaptation(blink, 0);
    PlayerDelta delta = PlayerViewDiff.diff(before, PlayerViews.build(registry, profile));

    assertThat(delta.adaptationLevels()).containsEntry("rift-blink", 0);
    assertThat(delta.clearedPreferences()).contains(
        new PreferenceKey(PreferenceOwner.ADAPTATION, "rift-blink", "landing"));
    assertThat(delta.header()).isNotNull();
  }

  private static SkillView skill(CatalogView catalog, String id) {
    return catalog.skills().stream().filter((SkillView skill) -> skill.id().equals(id)).findFirst().orElseThrow();
  }

  private static AdaptationView adaptation(SkillView skill, String id) {
    return skill.adaptations().stream()
        .filter((AdaptationView adaptation) -> adaptation.id().equals(id)).findFirst().orElseThrow();
  }

  private static Adaptation<?> adaptation(Skill<?> skill, String id) {
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation.getName().equals(id)) {
        return adaptation;
      }
    }
    throw new AssertionError("No adaptation " + id);
  }
}
