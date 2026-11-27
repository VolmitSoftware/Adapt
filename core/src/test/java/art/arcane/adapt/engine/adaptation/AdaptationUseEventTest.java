package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.event.AdaptationUseEvent;
import art.arcane.adapt.content.adaptation.excavation.ExcavationClodHurler;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationUseEventTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private ExcavationClodHurler hurler;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Digger", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillExcavation.class));
    profile = adapt.join(player);
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("excavation");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation instanceof ExcavationClodHurler found) {
        hurler = found;
      }
    }
    profile.getData().getSkillLine("excavation").setAdaptation(hurler, 2);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aListenerSeesTheUseWithTheAdaptationSkillAndLevel() {
    List<AdaptationUseEvent> seen = new ArrayList<>();
    adapt.bus().register(this, AdaptationUseEvent.class, seen::add);

    assertThat(AdaptationRuntimeGuards.canUse(hurler, profile)).isTrue();
    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).adaptation()).isEqualTo(hurler.getId());
    assertThat(seen.get(0).skill()).isEqualTo(art.arcane.adapt.platform.value.Id.adapt("excavation"));
    assertThat(seen.get(0).level()).isEqualTo(2);
    assertThat(seen.get(0).player()).isEqualTo(player);
  }

  @Test
  void aCancellingListenerRefusesTheUse() {
    adapt.bus().register(this, AdaptationUseEvent.class, (AdaptationUseEvent event) -> event.setCancelled(true));

    assertThat(AdaptationRuntimeGuards.canUse(hurler, profile)).isFalse();
  }

  @Test
  void withoutListenersTheUseIsAllowed() {
    assertThat(AdaptationRuntimeGuards.canUse(hurler, profile)).isTrue();
  }
}
