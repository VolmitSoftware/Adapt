package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxEmitterTest extends AdaptTestBase {
  @Test
  void colorBackedParticlesUseEmitterColorAndExplicitDataRemainsUnchanged() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerData playerData = new PlayerData();
    Player player = mock(Player.class);
    World world = mock(World.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(playerData);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0.0D, 64.0D, 0.0D));

    FxViewers.reset();
    FxViewers.bumpTick();
    FxBudget.resetTick();
    Color emitterColor = Color.fromRGB(64, 32, 128);
    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 128, 32), 1.0F);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      FxEmitter emitter = FxEmitter.create(world, 0.0D, 64.0D, 0.0D, FxPriority.GAMEPLAY, 24.0D, true, false, emitterColor);
      emitter.particle(Particle.FLASH, 1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
      ownerTask.get().run();
      emitter.burst(Particle.FLASH, 1, 0.0D);
      ownerTask.get().run();
      emitter.particle(Particle.DUST, 1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, dust);
      ownerTask.get().run();

      verify(player, times(2)).spawnParticle(Particle.FLASH, 0.0D, 64.0D, 0.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D, emitterColor);
      verify(player).spawnParticle(Particle.DUST, 0.0D, 64.0D, 0.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
    } finally {
      FxViewers.reset();
      FxBudget.resetTick();
    }
  }
}
