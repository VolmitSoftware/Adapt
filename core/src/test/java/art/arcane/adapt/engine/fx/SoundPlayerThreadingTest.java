package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundPlayerThreadingTest {
  @Test
  void directPlayerSoundRunsInsideViewerOwnerDispatch() {
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.Effects effects = mock(AdaptConfig.Effects.class);
    PlayerRef player = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    Id sound = Sounds.UI_BUTTON_CLICK;
    Loc location = new Loc(world, 3.0D, 65.0D, 7.0D);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(config.getEffects()).thenReturn(effects);
    when(effects.isSoundsEnabled()).thenReturn(true);
    FxViewers.reset();

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class);
         MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      configured.when(AdaptConfig::get).thenReturn(config);
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      SoundPlayer.of(player).play(location, sound, 0.8F, 1.2F);

      verify(player, never()).playSound(any(Loc.class), same(sound), any(SoundCategory.class), anyFloat(), anyFloat());
      ownerTask.get().run();
      verify(player).playSound(any(Loc.class), same(sound), any(SoundCategory.class), anyFloat(), anyFloat());
    }
  }
}
