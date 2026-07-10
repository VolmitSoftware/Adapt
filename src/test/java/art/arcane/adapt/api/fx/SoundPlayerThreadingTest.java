package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundPlayerThreadingTest extends AdaptTestBase {
  @Test
  void directPlayerSoundRunsInsideViewerOwnerDispatch() {
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.Effects effects = mock(AdaptConfig.Effects.class);
    Player player = mock(Player.class);
    World world = mock(World.class);
    Sound sound = null;
    Location location = new Location(world, 3.0D, 65.0D, 7.0D);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    when(config.getEffects()).thenReturn(effects);
    when(effects.isSoundsEnabled()).thenReturn(true);
    FxViewers.reset();

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class);
         MockedStatic<J> scheduling = mockStatic(J.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      SoundPlayer.of(player).play(location, sound, 0.8F, 1.2F);

      verify(player, never()).playSound(any(Location.class), same(sound), anyFloat(), anyFloat());
      ownerTask.get().run();
      verify(player).playSound(any(Location.class), same(sound), anyFloat(), anyFloat());
    }
  }
}
