package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeChopProtectionTest {
  private AdaptCore previous;

  @AfterEach
  void restore() {
    TestCores.use(previous);
  }

  @Test
  void chopPropagatesTheNativePlayerBreakResult() {
    previous = AdaptCore.current();
    HostPlatform platform = TestCores.mockPlatform();
    TestCores.install(platform);
    AxeChop adaptation = mock(AxeChop.class, CALLS_REAL_METHODS);
    PlayerRef player = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    BlockRef block = mock(BlockRef.class);
    Loc location = new Loc(world, 2, 64, 3);
    when(world.uid()).thenReturn(UUID.randomUUID());
    when(block.world()).thenReturn(world);
    when(block.x()).thenReturn(2);
    when(block.y()).thenReturn(64);
    when(block.z()).thenReturn(3);
    when(block.type()).thenReturn(Id.minecraft("oak_log"));
    when(block.location()).thenReturn(location);
    when(world.blockAt(2, 64, 3)).thenReturn(block);
    doReturn(true).when(adaptation).canBlockBreak(player, location);
    List<Boolean> flaggedDuringBreak = new ArrayList<>();
    when(platform.protection().attemptBreak(player, block)).thenAnswer(invocation -> {
      flaggedDuringBreak.add(VEIN_MINED.get(block));
      return flaggedDuringBreak.size() > 1;
    });

    assertThat(breakStuff(adaptation, block, player)).isFalse();
    assertThat(VEIN_MINED.get(block)).isFalse();
    assertThat(breakStuff(adaptation, block, player)).isTrue();
    assertThat(VEIN_MINED.get(block)).isFalse();

    verify(platform.protection(), times(2)).attemptBreak(player, block);
    assertThat(flaggedDuringBreak).containsExactly(true, true);
  }

  @Test
  void chopObservesProtectionAtMonitorBeforeMutating() throws NoSuchMethodException {
    Handle handler = AxeChop.class.getDeclaredMethod("on", PlayerInteractEvent.class).getAnnotation(Handle.class);

    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(handler.ignoreCancelled()).isTrue();
  }

  private boolean breakStuff(AxeChop adaptation, BlockRef block, PlayerRef player) {
    return AxeFelling.chopTop(adaptation, block, 1, player);
  }
}
