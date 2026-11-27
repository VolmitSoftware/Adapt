package art.arcane.adapt.api.content;

import art.arcane.adapt.api.TestStubs;
import art.arcane.adapt.api.TestHost;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.api.internal.SkillRuntime;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XpSourceTest {
  private static final Text MACE = Text.key("adapt.xp_guide.kinetics.mace");
  private static final Text FARMED = Text.key("adapt.xp_guide.farmed_mobs");

  private final List<Grant> grants = new ArrayList<>();
  private EngineBridge bridge;

  @BeforeEach
  void start() {
    SkillRuntime runtime = TestStubs.stub(SkillRuntime.class, Map.of("xp", (Object[] args) -> {
      grants.add(new Grant((Loc) args[1], (Double) args[2], (String) args[3], (Boolean) args[4], (Boolean) args[5]));
      return null;
    }));
    Map<String, Function<Object[], Object>> answers = Map.of("attach", (Object[] args) -> runtime);
    bridge = TestStubs.stub(EngineBridge.class, answers);
    Engine.start(TestHost.key(), bridge);
  }

  @AfterEach
  void stop() {
    Engine.stop(TestHost.key(), bridge);
  }

  @Test
  void sourcesSharingAGuideTextShowOneLineAndNotesKeepDeclarationOrder() {
    TestSkill skill = new TestSkill();

    assertThat(skill.getXpGuide()).containsExactly(MACE, Text.key("alchemist.skill.alchemy.xp.plain"), FARMED);
    assertThat(skill.getXpSources()).extracting(XpSource::key).containsExactly("smash", "mace-hit", "plain");
    assertThat(skill.smash.guide()).isEqualTo(MACE);
  }

  @Test
  void rewardKeysCarryTheSkillIdAndAnOptionalDetail() {
    TestSkill skill = new TestSkill();

    assertThat(skill.smash.rewardKey()).isEqualTo("alchemist:alchemy:smash");
    assertThat(skill.smash.rewardKey("minecraft:zombie")).isEqualTo("alchemist:alchemy:smash:minecraft:zombie");
    assertThat(skill.smash.rewardKey(" ")).isEqualTo("alchemist:alchemy:smash");
    assertThat(skill.smash.rewardKey(null)).isEqualTo("alchemist:alchemy:smash");
  }

  @Test
  void awardVariantsReachTheRuntimeWithTheirKeyAndVisibility() {
    TestSkill skill = new TestSkill();
    PlayerRef player = TestStubs.stub(PlayerRef.class, Map.of());
    Loc at = new Loc(TestStubs.stub(WorldRef.class, Map.of()), 1, 2, 3);

    skill.smash.award(player, 1);
    skill.smash.award(player, 2, "zombie");
    skill.smash.award(player, at, 3, "husk");
    skill.smash.awardSilent(player, at, 4);
    skill.smash.awardSilent(player, 5, "drowned");

    assertThat(grants).containsExactly(
        new Grant(null, 1D, "alchemist:alchemy:smash", false, true),
        new Grant(null, 2D, "alchemist:alchemy:smash:zombie", false, true),
        new Grant(at, 3D, "alchemist:alchemy:smash:husk", false, true),
        new Grant(at, 4D, "alchemist:alchemy:smash", true, true),
        new Grant(null, 5D, "alchemist:alchemy:smash:drowned", true, false));
  }

  @Test
  void aSourceKeyDeclaredTwiceIsRefused() {
    assertThatThrownBy(DuplicateSkill::new).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("twice");
  }

  private record Grant(Loc at, double amount, String rewardKey, boolean silent, boolean visual) {
  }

  private static final class TestSkill extends SimpleSkill<SkillConfig> {
    private final XpSource smash = xpSource("smash", MACE);
    private final XpSource maceHit = xpSource("mace-hit", MACE);
    private final XpSource plain = xpSource("plain");

    private TestSkill() {
      super(Id.of("alchemist", "alchemy"));
      xpNote(FARMED);
      xpNote(MACE);
    }
  }

  private static final class DuplicateSkill extends SimpleSkill<SkillConfig> {
    private DuplicateSkill() {
      super(Id.of("alchemist", "alchemy"));
      xpSource("smash");
      xpSource("smash", MACE);
    }
  }
}
