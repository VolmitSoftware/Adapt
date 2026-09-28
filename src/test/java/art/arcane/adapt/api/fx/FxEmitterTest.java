package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.Registry;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class FxEmitterTest extends AdaptTestBase {
  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.UI_BUTTON_CLICK).isNotNull();
    }
  }

  @ParameterizedTest
  @CsvSource({"true,1", "false,1", "false,10"})
  void filteredSoundCannotReachAnExcludedViewer(boolean only, double secondDistance) {
    AdaptServer server = mock(AdaptServer.class);
    World world = mock(World.class);
    Player first = mock(Player.class);
    Player second = mock(Player.class);
    AdaptPlayer firstRuntime = mock(AdaptPlayer.class);
    AdaptPlayer secondRuntime = mock(AdaptPlayer.class);
    when(firstRuntime.getData()).thenReturn(new PlayerData());
    when(secondRuntime.getData()).thenReturn(new PlayerData());
    when(firstRuntime.getPlayer()).thenReturn(first);
    when(secondRuntime.getPlayer()).thenReturn(second);
    when(firstRuntime.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0, 64, 0));
    when(secondRuntime.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, secondDistance, 64, 0));
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(firstRuntime, secondRuntime));
    Sound sound = mock(Sound.class);
    FxViewers.reset();
    FxViewers.bumpTick();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class))).thenAnswer(call -> {
        call.<Runnable>getArgument(1).run();
        return true;
      });
      FxEmitter emitter = FxEmitter.create(world, 0, 64, 0, FxPriority.GAMEPLAY, 4, false, true, Color.WHITE);
      FxEmitter filtered = only ? emitter.only(first) : emitter.except(second);
      filtered.sound(sound, 0.3F, 1F);
      verify(first).playSound(new Location(world, 0, 64, 0), sound, 0.3F, 1F);
      scheduling.verify(() -> J.runEntity(same(second), any(Runnable.class)), never());
    } finally {
      FxViewers.reset();
    }
  }

  @ParameterizedTest
  @CsvSource({"true,true,1", "false,true,1", "true,false,0"})
  void audienceFiltersKeepParticlesOnOnlyTheChosenOwnerThread(boolean only, boolean firstEnabled, int expected) {
    AdaptServer server = mock(AdaptServer.class);
    World world = mock(World.class);
    Player first = mock(Player.class);
    Player second = mock(Player.class);
    AdaptPlayer firstRuntime = mock(AdaptPlayer.class);
    AdaptPlayer secondRuntime = mock(AdaptPlayer.class);
    PlayerData firstData = new PlayerData();
    firstData.setEffectsEnabled(firstEnabled);
    when(firstRuntime.getData()).thenReturn(firstData);
    when(secondRuntime.getData()).thenReturn(new PlayerData());
    when(firstRuntime.getPlayer()).thenReturn(first);
    when(secondRuntime.getPlayer()).thenReturn(second);
    when(firstRuntime.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0, 64, 0));
    when(secondRuntime.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 1, 64, 0));
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(firstRuntime, secondRuntime));
    Player recipient = only ? first : second;
    Player excluded = only ? second : first;
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxViewers.reset();
    FxViewers.bumpTick();
    FxBudget.resetTick();
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runEntity(same(recipient), any(Runnable.class))).thenAnswer(call -> {
        ownerTask.set(call.getArgument(1));
        return true;
      });
      FxEmitter original = FxEmitter.create(world, 0, 64, 0, FxPriority.GAMEPLAY, 24, true, false, Color.WHITE);
      FxEmitter filtered = only ? original.only(first) : original.except(first);
      assertThat(filtered.viewerCount()).isEqualTo(expected);
      filtered.particle(Particle.FLAME, 1, 0, 0, 0, 0, 0);
      scheduler.verify(() -> J.runEntity(same(excluded), any(Runnable.class)), never());
      if (ownerTask.get() != null) ownerTask.get().run();
      verify(recipient, times(expected)).spawnParticle(Particle.FLAME, 0D, 64D, 0D, 1, 0D, 0D, 0D, 0D, null);
    } finally {
      FxViewers.reset();
      FxBudget.resetTick();
    }
  }

  @ParameterizedTest
  @CsvSource({
      "0.4,12,true,1", "0.5,16,true,1", "0.4,16.01,true,0", "0.4,12,false,0",
      "2,32,true,1", "2,32.01,true,0", "4,48,true,1", "4,48.01,true,0"
  })
  void soundDispatchUsesNativeQuietRangeAndRetainsOptOutAndLoudCap(
      float volume, double distance, boolean enabled, int expected
  ) {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerData data = new PlayerData();
    data.setEffectsEnabled(enabled);
    Player player = mock(Player.class);
    World world = mock(World.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(data);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, distance, 64D, 0D));
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    AtomicInteger received = new AtomicInteger();
    FxViewers.reset();
    FxViewers.bumpTick();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(call -> {
        ownerTask.set(call.getArgument(1));
        return true;
      });
      FxViewers.current().dispatch(world, 0D, 64D, 0D, FxEmitter.soundRadius(volume),
          FxDispatch.emission(viewer -> received.incrementAndGet(), null));
      assertThat(received.get()).isZero();
      if (ownerTask.get() != null) {
        ownerTask.get().run();
      }
      assertThat(received.get()).isEqualTo(expected);
      scheduling.verify(() -> J.runEntity(same(player), any(Runnable.class)), times(expected));
    } finally {
      FxViewers.reset();
    }
  }

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
