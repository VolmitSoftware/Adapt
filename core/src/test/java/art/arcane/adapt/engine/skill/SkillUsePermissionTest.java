package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Skill;
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

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillUsePermissionTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private Skill<?> skill;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Holder", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
    skill = mock(Skill.class);
    lenient().when(skill.getId()).thenReturn(Id.adapt("hunter"));
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void operatorsBypassAndUnsetNodesAllowAsOnBukkit() {
    String node = UsePermissions.useNode(Id.adapt("hunter"));
    player.deny(node);
    assertThat(SkillRuntimeGuards.hasUsePermission(player, skill)).isTrue();

    player.setOp(false);
    assertThat(SkillRuntimeGuards.hasUsePermission(player, skill)).isFalse();

    player.grant(node);
    assertThat(SkillRuntimeGuards.hasUsePermission(player, skill)).isTrue();

    FakePlayer unset = adapt.platform().player("Unset", 2.5D, 61D, 0.5D);
    unset.setOp(false);
    assertThat(SkillRuntimeGuards.hasUsePermission(unset, skill)).isTrue();
  }
}
