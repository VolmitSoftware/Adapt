package art.arcane.adapt.engine.advancement;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptStatTracker;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancementKeysTest {
  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
  private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

  @TempDir
  Path dataFolder;

  @Test
  void everyRegisteredSkillAndAdaptationHasAValidAdvancementKeyAndTheCatalogEnables() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      platform.scheduler().advance(200);
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      List<String> invalid = new ArrayList<>();
      int adaptations = 0;
      for (Skill<?> skill : registry.getSkills()) {
        AdaptAdvancement tree = SkillHost.of(skill).buildAdvancements();
        assertThat(tree.getKey()).isEqualTo(AdaptAdvancement.skillKey(skill.getId()));
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          assertThat(AdaptationHost.of(adaptation).buildAdvancements().getKey()).isEqualTo(AdaptAdvancement.adaptationKey(adaptation.getId()));
          adaptations++;
        }
        collectInvalid(tree.toNode(), invalid);
      }

      assertThat(registry.getSkills()).isNotEmpty();
      assertThat(adaptations).isPositive();
      assertThat(invalid).as("advancement keys that are not resource locations").isEmpty();
      assertThat(adapt.core().getAdvancementManager().isCatalogReady(registry.getCatalogRevision()))
          .as("advancement catalog enabled").isTrue();
      assertThat(platform.log().errors()).as("errors logged at enable").isEmpty();
    }
  }

  @Test
  void aJoiningPlayerHoldsEverySkillRootSoEveryTabShows() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      platform.scheduler().advance(200);
      AdaptPlayer profile = adapt.join(platform.player("Reader", 0.5D, 64D, 0.5D));
      platform.scheduler().advance(200);

      List<String> missing = new ArrayList<>();
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        String root = AdaptAdvancement.skillKey(skill.getId());
        if (!profile.getData().isGranted(root)) {
          missing.add(root);
        }
      }
      assertThat(missing).as("skill roots not granted on join").isEmpty();
    }
  }

  @Test
  void thirdPartyContentKeysAreValidResourceLocationsInTheAdaptNamespace() {
    Id skill = AdaptAdvancement.advancementId(AdaptAdvancement.skillKey(Id.of("alchemist.lab", "alchemy")));
    Id adaptation = AdaptAdvancement.advancementId(AdaptAdvancement.adaptationKey(Id.of("alchemist.lab", "alchemy/transmute")));

    assertThat(skill).hasToString("adapt:skill/alchemist.lab/alchemy");
    assertThat(adaptation).hasToString("adapt:adaptation/alchemist.lab/alchemy/transmute");
    assertThat(AdaptAdvancement.isSkillKey(skill.path())).isTrue();
    assertThat(AdaptAdvancement.isSkillKey(adaptation.path())).isFalse();
  }

  @Test
  void twoAddonsDeclaringTheSameKeyGetTwoAdvancements() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    FakePlayer player = platform.player("Collector", 0.5D, 64D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform, new FirstAddon("one"), new FirstAddon("two"))) {
      platform.scheduler().advance(200);
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      Id one = Id.of("one", "collecting");
      Id two = Id.of("two", "collecting");

      List<String> oneKeys = childKeys(SkillHost.of(registry.getSkill(one)).buildAdvancements());
      List<String> twoKeys = childKeys(SkillHost.of(registry.getSkill(two)).buildAdvancements());
      assertThat(oneKeys).contains("content/one/first");
      assertThat(twoKeys).contains("content/two/first");
      assertThat(AdaptAdvancement.advancementId("content/one/first")).hasToString("adapt:content/one/first");
      assertThat(SkillHost.of(registry.getSkill(one)).getStatTrackers()).extracting(AdaptStatTracker::getAdvancement)
          .containsExactly("content/one/first");
      assertThat(adapt.core().getAdvancementManager().isCatalogReady(registry.getCatalogRevision()))
          .as("advancement catalog enabled").isTrue();
      assertThat(platform.log().errors()).as("errors logged at enable").isEmpty();

      AdaptPlayer joined = adapt.join(player);
      platform.scheduler().advance(200);
      PlayerProfile profile = AdaptApi.players().require(player);
      assertThat(profile.grant(one, "first")).isTrue();
      assertThat(profile.isGranted(one, "first")).isTrue();
      assertThat(profile.isGranted(two, "first")).isFalse();
      assertThat(joined.getData().isGranted("content/one/first")).isTrue();
      assertThat(joined.getData().isGranted("first")).isFalse();
    }
  }

  @Test
  void builtInContentKeepsItsKeys() {
    assertThat(AdaptAdvancement.contentKey(Id.adapt("swords"), "challenge_swords_flow_max"))
        .isEqualTo("challenge_swords_flow_max");
    assertThat(AdaptAdvancement.contentKey(Id.of("alchemist.lab", "alchemy"), "first_brew"))
        .isEqualTo("content/alchemist.lab/first_brew");
  }

  private static List<String> childKeys(AdaptAdvancement tree) {
    List<String> keys = new ArrayList<>();
    for (AdaptAdvancement child : tree.getChildren()) {
      keys.add(child.getKey());
    }
    return keys;
  }

  private record FirstAddon(String namespace) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
      Id skill = Id.of(namespace, "collecting");
      registrar.skill(skill, () -> new CollectingSkill(skill));
    }
  }

  /** Declares the advancement {@code first} and a milestone that grants it, as any addon might. */
  public static final class CollectingSkill extends SimpleSkill<SkillConfig> {
    public CollectingSkill(Id id) {
      super(id);
      registerConfiguration(SkillConfig.class);
      setColor(Rgb.of(0x5A7D9A));
      setIcon(Id.minecraft("chest"));
      registerAdvancement(art.arcane.adapt.api.advancement.AdaptAdvancement.builder()
          .key("first")
          .icon(Id.minecraft("chest"))
          .frame(AdvancementFrame.TASK)
          .visibility(AdvancementVisibility.VANILLA)
          .build());
      registerMilestone("first", "collecting.items", 10D, 5D);
    }
  }

  private static void collectInvalid(AdvancementNode node, List<String> invalid) {
    if (!NAMESPACE.matcher(node.key().namespace()).matches() || !PATH.matcher(node.key().path()).matches()) {
      invalid.add(node.key().toString());
    }
    for (AdvancementNode child : node.children()) {
      collectInvalid(child, invalid);
    }
  }
}
