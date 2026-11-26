package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.content.adaptation.axe.upgraded.Timberfall;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static art.arcane.adapt.util.data.BlockFlag.VEIN_MINED;
import static org.assertj.core.api.Assertions.assertThat;

/** Axes and Architect, either leading: Timberfall's logs arrive stripped, or as planks by choice from level 2. */
class TimberFrameTest {
  private static final Id LOG = Id.minecraft("oak_log");
  private static final Id STRIPPED = Id.minecraft("stripped_oak_log");
  private static final Id PLANKS = Id.minecraft("oak_planks");

  @TempDir
  Path data;

  private SignatureBench bench;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillAxes.class, SkillArchitect.class));
    bench.player.holding(FakeItem.tool("iron_axe", 250));
    bench.player.setSneaking(true);
  }

  @AfterEach
  void stop() {
    bench.close();
  }

  @Test
  void timberfallsLogsArriveStripped() {
    bench.signature("axes", "architect", 1);
    bench.upgrade("axes", Timberfall.class, 1);

    fell();

    assertThat(bench.count(STRIPPED)).isEqualTo(5);
    assertThat(bench.count(LOG)).isZero();
  }

  @Test
  void eitherSkillMayLead() {
    bench.signature("architect", "axes", 1);
    bench.upgrade("axes", Timberfall.class, 1);

    fell();

    assertThat(bench.count(STRIPPED)).isEqualTo(5);
  }

  @Test
  void theFelledLogsChoiceShowsOnTheMutatedAdaptation() {
    MutationAdaptation adaptation = bench.signature("axes", "architect", 1);

    assertThat(adaptation.getPlayerPreferences()).contains(AxePreferences.FELLED_LOGS);
  }

  @Test
  void nothingIsShapedWithoutTheLearnedSignature() {
    bench.kit.mutate("axes", "architect");
    bench.upgrade("axes", Timberfall.class, 1);

    fell();

    assertThat(bench.count(LOG)).isEqualTo(5);
    assertThat(bench.count(STRIPPED)).isZero();
  }

  @Test
  void planksNeedLevelTwoAndComeAtTheTablesFourPerLog() {
    MutationAdaptation adaptation = bench.signature("axes", "architect", 1);
    bench.upgrade("axes", Timberfall.class, 1);
    bench.profile.getData().getPreferences().set(adaptation.getName(), "felled-logs", "PLANKS");

    fell();
    int strippedAtLevelOne = bench.count(STRIPPED);
    bench.kit.learn("axes", "architect", Recipe.CONVERGENCE, 2);
    fell();

    assertThat(strippedAtLevelOne).isEqualTo(5);
    assertThat(bench.count(PLANKS)).isEqualTo(20);
    assertThat(bench.count(LOG)).isZero();
  }

  @Test
  void aTreeShapesNoMoreThanTheLevelsLogsAndNeverMakesExtraWood() {
    bench.signature("axes", "architect", 1);
    bench.upgrade("axes", Timberfall.class, 1);
    bench.handler("axes", "architect", TimberFrame.class).getConfig().logsAtFirstLevel = 2;

    fell();

    assertThat(bench.count(STRIPPED)).isEqualTo(2);
    assertThat(bench.count(LOG)).isEqualTo(3);
  }

  @Test
  void aLogAnotherVeinminerBreaksIsNotATimberfallFell() {
    bench.signature("axes", "architect", 3);
    bench.upgrade("axes", Timberfall.class, 1);
    BlockRef origin = bench.world.blockAt(3, 61, 3);
    bench.world.set(3, 61, 3, "oak_log");

    VEIN_MINED.add(origin);
    try {
      bench.breakBlock(origin);
    } finally {
      VEIN_MINED.remove(origin);
    }
    bench.advance(6);

    assertThat(bench.count(STRIPPED)).isZero();
  }

  @Test
  void aSecondTreeInsideTheCooldownIsLeftAsItIs() {
    bench.signature("axes", "architect", 1);
    bench.upgrade("axes", Timberfall.class, 1);
    bench.handler("axes", "architect", TimberFrame.class).getConfig().cooldownMillis = 60_000L;

    fell();
    fell();

    assertThat(bench.count(STRIPPED)).isEqualTo(5);
    assertThat(bench.count(LOG)).isEqualTo(5);
  }

  private void fell() {
    for (int y = 61; y <= 66; y++) {
      bench.world.set(3, y, 3, "oak_log");
    }
    bench.breakBlock(bench.world.blockAt(3, 61, 3));
    bench.world.set(3, 61, 3, "air");
    bench.advance(6);
  }
}
