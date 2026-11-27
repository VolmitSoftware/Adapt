package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.engine.skill.UsePermissions;
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

class AdaptationUsePermissionTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private Adaptation<?> adaptation;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Holder", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
    adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getId()).thenReturn(Id.adapt("hunter/luck"));
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void operatorsBypassAndUnsetNodesAllowAsOnBukkit() {
    String node = UsePermissions.useNode(Id.adapt("hunter/luck"));
    player.deny(node);
    assertThat(AdaptationRuntimeGuards.hasUsePermission(adaptation, player, null)).isTrue();

    player.setOp(false);
    assertThat(AdaptationRuntimeGuards.hasUsePermission(adaptation, player, null)).isFalse();

    player.grant(node);
    assertThat(AdaptationRuntimeGuards.hasUsePermission(adaptation, player, null)).isTrue();

    FakePlayer unset = adapt.platform().player("Unset", 2.5D, 61D, 0.5D);
    unset.setOp(false);
    assertThat(AdaptationRuntimeGuards.hasUsePermission(adaptation, unset, null)).isTrue();
  }
}
