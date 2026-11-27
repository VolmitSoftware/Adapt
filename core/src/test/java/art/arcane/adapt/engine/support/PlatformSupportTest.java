package art.arcane.adapt.engine.support;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.golem.plinth.PlinthStructure;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.GameVersion;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformSupportTest {
  @TempDir
  Path dataFolder;

  private TestAdapt adapt;

  @BeforeEach
  void reset() {
    PlatformSupport.reset();
  }

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
    PlatformSupport.reset();
  }

  @Test
  void versionsParseAndOrderByRelease() {
    assertThat(GameVersion.parse("1.20.1")).isEqualTo(new GameVersion(1, 20, 1));
    assertThat(GameVersion.parse("26.3")).isEqualTo(new GameVersion(26, 3, 0));
    assertThat(GameVersion.parse("26.1.2-rc1")).isEqualTo(new GameVersion(26, 1, 2));
    assertThat(GameVersion.parse("1.21.1-R0.1-SNAPSHOT")).isEqualTo(GameVersion.V1_21_1);
    assertThat(List.of(GameVersion.parse("26.3"), GameVersion.parse("1.21.1"), GameVersion.parse("26.1.2"), GameVersion.V1_20_1)
        .stream().sorted().map(GameVersion::toString).toList())
        .containsExactly("1.20.1", "1.21.1", "26.1.2", "26.3");
    assertThat(GameVersion.parse("26.3").isAtLeast(GameVersion.V1_21_1)).isTrue();
    assertThatThrownBy(() -> GameVersion.parse("snapshot")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renamedIdsResolveToTheNameTheOldVersionUses() {
    Set<Id> attributes1201 = Set.of(Id.minecraft("generic.max_health"), Id.minecraft("horse.jump_strength"));
    assertThat(ContentAliases.resolve(RegistryKind.ATTRIBUTE, Attributes.MAX_HEALTH, attributes1201::contains))
        .isEqualTo(Id.minecraft("generic.max_health"));
    assertThat(ContentAliases.resolve(RegistryKind.ATTRIBUTE, Attributes.JUMP_STRENGTH, attributes1201::contains))
        .as("players have no jump strength on 1.20.1").isNull();
    assertThat(ContentAliases.mountAttribute(Attributes.JUMP_STRENGTH)).isEqualTo(Id.minecraft("horse.jump_strength"));
    assertThat(ContentAliases.resolve(RegistryKind.ATTRIBUTE, Attributes.BLOCK_BREAK_SPEED,
        Set.of(Id.minecraft("player.block_break_speed"))::contains)).isEqualTo(Id.minecraft("player.block_break_speed"));
    assertThat(ContentAliases.resolve(RegistryKind.BLOCK, Id.minecraft("short_grass"), Set.of(Id.minecraft("grass"))::contains))
        .isEqualTo(Id.minecraft("grass"));
    assertThat(ContentAliases.resolve(RegistryKind.ITEM, Id.minecraft("stone"), Set.of(Id.minecraft("stone"))::contains))
        .isEqualTo(Id.minecraft("stone"));
    assertThat(ContentAliases.modern(RegistryKind.ENTITY_TYPE, Id.minecraft("potion")))
        .containsExactly(Id.minecraft("splash_potion"), Id.minecraft("lingering_potion"));
    assertThat(ContentAliases.resolveFx(RegistryKind.SOUND, Id.minecraft("item.lead.break"),
        Set.of(Id.minecraft("entity.leash_knot.break"))::contains)).isEqualTo(Id.minecraft("entity.leash_knot.break"));
    assertThat(ContentAliases.resolveFx(RegistryKind.PARTICLE, Id.minecraft("tinted_leaves"), Set.<Id>of()::contains)).isNull();
  }

  @Test
  void everyAttributeCoreNamesResolvesOrIsEmulatedOrIsReportedMissingOnEachVersion() {
    FakeGameContent v1201 = FakeGameContent.v1_20_1();
    FakeGameContent v1211 = FakeGameContent.v1_21_1();
    List<Id> missingOn1211 = new ArrayList<>();
    for (Id attribute : Attributes.ALL) {
      if (!v1211.exists(RegistryKind.ATTRIBUTE, attribute)) {
        missingOn1211.add(attribute);
      }
    }
    assertThat(missingOn1211).extracting(Id::path).containsExactlyInAnyOrder("air_drag_modifier", "below_name_distance",
        "bounciness", "camera_distance", "friction_modifier", "name_tag_distance", "tempt_range",
        "waypoint_receive_range", "waypoint_transmit_range");
    assertThat(v1201.exists(RegistryKind.ATTRIBUTE, Attributes.MAX_HEALTH)).isTrue();
    assertThat(v1201.exists(RegistryKind.ATTRIBUTE, Attributes.SCALE)).isFalse();
  }

  @Test
  void needsAreCheckedAgainstTheRunningServer() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());

    PlatformSupport.Verdict mace = PlatformSupport.check(List.of(PlatformNeed.item("mace")), platform.host());
    PlatformSupport.Verdict jump = PlatformSupport.check(List.of(PlatformNeed.attribute(Attributes.JUMP_STRENGTH)), platform.host());
    PlatformSupport.Verdict scale = PlatformSupport.check(List.of(PlatformNeed.attribute(Attributes.SCALE)), platform.host());
    PlatformSupport.Verdict bundles = PlatformSupport.check(List.of(PlatformNeed.feature(EngineFeature.BUNDLES)), platform.host());
    PlatformSupport.Verdict stone = PlatformSupport.check(List.of(PlatformNeed.block("stone")), platform.host());

    assertThat(mace.usable()).isFalse();
    assertThat(mace.missingText()).isEqualTo("item minecraft:mace");
    assertThat(jump.usable()).isTrue();
    assertThat(jump.isEmulated()).isTrue();
    assertThat(scale.usable()).isFalse();
    assertThat(bundles.missingText()).isEqualTo("engine feature bundles");
    assertThat(stone).isEqualTo(PlatformSupport.Verdict.AVAILABLE);
    assertThat(platform.host().gameVersion()).isEqualTo(GameVersion.V1_20_1);
  }

  @Test
  void emulatedAndPartialFeaturesKeepTheContent() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());
    PlatformNeed limits = PlatformNeed.emulatedFeature(EngineFeature.ITEM_STACK_LIMITS);
    PlatformNeed groups = PlatformNeed.partialFeature(EngineFeature.ITEM_COOLDOWN_GROUPS, "no cooldown sweep on the item");

    PlatformSupport.Verdict emulated = PlatformSupport.check(List.of(limits), platform.host());
    PlatformSupport.Verdict partial = PlatformSupport.check(List.of(limits, groups), platform.host());

    assertThat(emulated.isEmulated()).isTrue();
    assertThat(emulated.emulatedText()).isEqualTo("engine feature item stack limits");
    assertThat(partial.usable()).isTrue();
    assertThat(partial.isEmulated()).isFalse();
    assertThat(partial.isPartial()).isTrue();
    assertThat(partial.partialText())
        .isEqualTo("engine feature item cooldown groups (no cooldown sweep on the item), engine feature item stack limits");

    platform.host().runGame(FakeGameContent.v1_21_1());

    assertThat(PlatformSupport.check(List.of(limits), platform.host())).isEqualTo(PlatformSupport.Verdict.AVAILABLE);
    assertThat(PlatformSupport.check(List.of(groups), platform.host()).isPartial()).isTrue();
  }

  @Test
  void aPartialAttributeKeepsTheContentOnlyWhereTheAttributeIsMissing() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());
    PlatformNeed jumpLock = PlatformNeed.partialAttribute(Attributes.JUMP_STRENGTH, "creatures in the field can still jump");

    PlatformSupport.Verdict old = PlatformSupport.check(List.of(jumpLock), platform.host());

    assertThat(old.usable()).isTrue();
    assertThat(old.isEmulated()).isFalse();
    assertThat(old.isPartial()).isTrue();
    assertThat(old.partialText()).isEqualTo("attribute minecraft:jump_strength (creatures in the field can still jump)");

    platform.host().runGame(FakeGameContent.v1_21_1());

    assertThat(PlatformSupport.check(List.of(jumpLock), platform.host())).isEqualTo(PlatformSupport.Verdict.AVAILABLE);
  }

  @Test
  void anAdaptationWhoseNeedsAreMissingIsLeftOutOfItsSkillAndLoggedOnce() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_21_1());
    adapt = TestAdapt.start(platform, List.of(SkillKinetics.class));
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();

    adapt.addons().get(0).refresh();

    Skill<?> kinetics = registry.getSkill("kinetics");
    List<String> names = new ArrayList<>();
    for (Adaptation<?> adaptation : kinetics.getAdaptations()) {
      names.add(adaptation.getId().toString());
    }
    assertThat(names).contains("adapt:kinetics/moon_jump", "adapt:kinetics/windburst").doesNotContain("adapt:kinetics/impale_pin", "adapt:kinetics/charge_lance");
    assertThat(PlatformSupport.leftOut()).extracting(PlatformSupport.LeftOut::name).contains("adapt:kinetics/impale_pin");
    assertThat(platform.log().infos()).filteredOn((String line) -> line.startsWith("Disabled adaptation adapt:kinetics/impale_pin "))
        .containsExactly("Disabled adaptation adapt:kinetics/impale_pin on Minecraft 1.21.1: missing item minecraft:iron_spear.");
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aSkillWhoseNeedsAreMissingIsLeftOutAndLoggedOnce() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());
    adapt = TestAdapt.start(platform, List.of(SkillKinetics.class));
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();

    adapt.addons().get(0).refresh();

    assertThat(registry.getSkill("kinetics")).isNull();
    assertThat(registry.getSkills()).isEmpty();
    assertThat(platform.log().infos()).filteredOn((String line) -> line.startsWith("Disabled skill adapt:kinetics "))
        .containsExactly("Disabled skill adapt:kinetics on Minecraft 1.20.1: missing item minecraft:mace.");
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void itemFallbacksApplyOnlyWhereThePreferredItemIsMissing() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());
    adapt = TestAdapt.start(platform, List.of());

    assertThat(PlatformSupport.itemOr(Id.minecraft("mace"), Id.minecraft("anvil"))).isEqualTo(Id.minecraft("anvil"));
    assertThat(PlatformSupport.itemOr(Id.minecraft("stone"), Id.minecraft("anvil"))).isEqualTo(Id.minecraft("stone"));
  }

  @Test
  void configDefaultsUseEraSubstitutesOnlyWhereTheirContentIsMissing() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());
    adapt = TestAdapt.start(platform, List.of());

    assertThat(PlatformSupport.materialOr("COPPER_CHEST", "BARREL")).isEqualTo("BARREL");
    assertThat(PlatformSupport.materialOr("BREEZE_ROD", "PHANTOM_MEMBRANE")).isEqualTo("PHANTOM_MEMBRANE");
    assertThat(PlatformSupport.materialOr("CHEST", "BARREL")).isEqualTo("CHEST");
    assertThat(PlinthStructure.defaultPedestals()).containsExactly("CUT_COPPER", "EXPOSED_CUT_COPPER",
        "WEATHERED_CUT_COPPER", "OXIDIZED_CUT_COPPER", "WAXED_CUT_COPPER", "WAXED_EXPOSED_CUT_COPPER",
        "WAXED_WEATHERED_CUT_COPPER", "WAXED_OXIDIZED_CUT_COPPER");
    adapt.close();

    platform.host().runGame(FakeGameContent.v1_21_1());
    adapt = TestAdapt.start(platform, List.of());
    assertThat(PlatformSupport.materialOr("COPPER_CHEST", "BARREL")).isEqualTo("BARREL");
    assertThat(PlatformSupport.materialOr("BREEZE_ROD", "PHANTOM_MEMBRANE")).isEqualTo("BREEZE_ROD");
    assertThat(PlatformSupport.materialOr("BUNDLE", "HOPPER")).isEqualTo("HOPPER");
    assertThat(PlinthStructure.defaultPedestals()).containsExactlyElementsOf(PlinthStructure.DEFAULT_PEDESTALS);
  }
}
