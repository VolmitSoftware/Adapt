package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.value.Id;
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

/** A skill that registers no kit gets the generated one and mutates with a built-in and with another such skill. */
class DefaultKitMutationTest {
  private static final Id HASTE = Id.minecraft("haste");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, Weaving.class, Pottery.class));
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aSkillWithoutAKitGetsTheGeneratedOne() {
    Skill<?> weaving = registry.getSkill(Weaving.ID);
    MutationKit kit = Kits.of(weaving);

    assertThat(weaving.getMutationKit()).isEmpty();
    assertThat(Kits.of(weaving)).isSameAs(kit);
    assertThat(Kits.mergeable(weaving)).isTrue();
    assertThat(kit.triggerKind()).isEqualTo(TriggerKind.EITHER);
    assertThat(kit.upgrades()).isEmpty();
    assertThat(kit.reservedGestures()).isEmpty();
    assertThat(kit.tint()).isEmpty();
    assertThat(kit.noun()).isEmpty();
    assertThat(kit.sounds()).isEqualTo(DefaultKit.SOUNDS);
    assertThat(SkillHost.of(weaving).getBlocks()).extracting(MutationBlock::getKey).extracting(BlockKey::id)
        .extracting(Id::toString)
        .containsExactlyInAnyOrder("kitless:weaving/block/trigger", "kitless:weaving/block/strike_fight",
            "kitless:weaving/block/strike_work", "kitless:weaving/block/boon", "kitless:weaving/block/fuel",
            "kitless:weaving/block/state");
    assertThat(SkillHost.of(weaving).getBlocks()).allSatisfy((MutationBlock<?> block) ->
        assertThat(block.getSkill()).isSameAs(weaving));
  }

  @Test
  void aSkillWithoutAKitMutatesWithABuiltIn() {
    art.arcane.adapt.testkit.MutationKit kit = kit("Weaver");
    kit.mutate("kitless:weaving", "swords");
    MutatedSkill mutated = kit.mutated("kitless:weaving", "swords");

    assertThat(mutated.getMutationAdaptations()).extracting(MutationAdaptation::getRecipe)
        .containsExactly(Recipe.values());
    assertThat(mutated.getAdaptations())
        .containsAll(SkillHost.of(registry.getSkill(SkillSwords.ID)).getUpgrades());
  }

  @Test
  void twoSkillsWithoutKitsMutateAndEarningXpSetsOffTheGeneratedTrigger() {
    FakePlayer player = platform.player("Potter", 0.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    art.arcane.adapt.testkit.MutationKit kit = new art.arcane.adapt.testkit.MutationKit(adapt, profile);
    kit.mutate("kitless:weaving", "kitless:pottery");
    MutatedSkill mutated = kit.mutated("kitless:weaving", "kitless:pottery");

    assertThat(mutated.getMutationAdaptations()).extracting(MutationAdaptation::getRecipe)
        .containsExactly(Recipe.values());
    kit.learn("kitless:weaving", "kitless:pottery", Recipe.GRAFT, 1);
    XP.award(profile, registry.getSkill(Weaving.ID),
        XpAward.gameplay(40D, "kitless:weaving/test"));

    assertThat(player.hasEffect(HASTE)).as("the pottery work Strike set off by earning weaving XP").isTrue();
  }

  private art.arcane.adapt.testkit.MutationKit kit(String name) {
    FakePlayer player = platform.player(name, 0.5D, 61D, 0.5D);
    return new art.arcane.adapt.testkit.MutationKit(adapt, adapt.join(player));
  }

  abstract static class PlainSkill extends SimpleSkill<PlainSkill.Config> {
    PlainSkill(Id id) {
      super(id);
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("string"));
      registerAdaptation(Id.of(id.namespace(), id.path() + "/knack"), () -> new Knack(id));
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Weaving extends PlainSkill {
    public static final Id ID = Id.of("kitless", "weaving");

    public Weaving() {
      super(ID);
    }
  }

  public static final class Pottery extends PlainSkill {
    public static final Id ID = Id.of("kitless", "pottery");

    public Pottery() {
      super(ID);
    }
  }

  static final class Knack extends SimpleAdaptation<AdaptationConfig> {
    Knack(Id skill) {
      super(Id.of(skill.namespace(), skill.path() + "/knack"));
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
