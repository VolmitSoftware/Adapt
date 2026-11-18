package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxEmitterTest {
  private AdaptCore core;
  private FxHost fx;

  @BeforeEach
  void installCore() {
    core = TestCores.install();
    fx = core.getPlatform().fx();
  }

  @AfterEach
  void clearCore() {
    FxViewers.reset();
    FxBudget.resetTick();
    TestCores.clear();
  }

  @ParameterizedTest
  @CsvSource({"true,1", "false,1", "false,10"})
  void filteredSoundCannotReachAnExcludedViewer(boolean only, double secondDistance) {
    AdaptServer server = mock(AdaptServer.class);
    WorldRef world = world();
    PlayerRef first = player();
    PlayerRef second = player();
    AdaptPlayer firstRuntime = mock(AdaptPlayer.class);
    AdaptPlayer secondRuntime = mock(AdaptPlayer.class);
    when(firstRuntime.getData()).thenReturn(new PlayerData());
    when(secondRuntime.getData()).thenReturn(new PlayerData());
    when(firstRuntime.getPlayer()).thenReturn(first);
    when(secondRuntime.getPlayer()).thenReturn(second);
    when(firstRuntime.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0, 64, 0));
    when(secondRuntime.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, secondDistance, 64, 0));
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(firstRuntime, secondRuntime));
    Id sound = Sounds.UI_BUTTON_CLICK;
    FxViewers.reset();
    FxViewers.bumpTick();
    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(any(PlayerRef.class), any(Runnable.class))).thenAnswer(call -> {
        call.<Runnable>getArgument(1).run();
        return true;
      });
      FxEmitter emitter = FxEmitter.create(world, 0, 64, 0, FxPriority.GAMEPLAY, 4, false, true, Fx.WHITE);
      FxEmitter filtered = only ? emitter.only(first) : emitter.except(second);
      filtered.sound(sound, 0.3F, 1F);
      verify(first).playSound(new Loc(world, 0, 64, 0), sound, SoundCategory.MASTER, 0.3F, 1F);
      scheduling.verify(() -> Tasks.runEntity(same(second), any(Runnable.class)), never());
    }
  }

  @ParameterizedTest
  @CsvSource({"true,true,1", "false,true,1", "true,false,0"})
  void audienceFiltersKeepParticlesOnOnlyTheChosenOwnerThread(boolean only, boolean firstEnabled, int expected) {
    AdaptServer server = mock(AdaptServer.class);
    WorldRef world = world();
    PlayerRef first = player();
    PlayerRef second = player();
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
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(firstRuntime, secondRuntime));
    PlayerRef recipient = only ? first : second;
    PlayerRef excluded = only ? second : first;
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxViewers.reset();
    FxViewers.bumpTick();
    FxBudget.resetTick();
    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      scheduler.when(() -> Tasks.runEntity(same(recipient), any(Runnable.class))).thenAnswer(call -> {
        ownerTask.set(call.getArgument(1));
        return true;
      });
      FxEmitter original = FxEmitter.create(world, 0, 64, 0, FxPriority.GAMEPLAY, 24, true, false, Fx.WHITE);
      FxEmitter filtered = only ? original.only(first) : original.except(first);
      assertThat(filtered.viewerCount()).isEqualTo(expected);
      filtered.particle(Particles.FLAME, 1, 0, 0, 0, 0, 0);
      scheduler.verify(() -> Tasks.runEntity(same(excluded), any(Runnable.class)), never());
      if (ownerTask.get() != null) ownerTask.get().run();
      verify(fx, times(expected)).particle(recipient, Particles.FLAME, 0D, 64D, 0D, 1, 0D, 0D, 0D, 0D);
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
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(data);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, distance, 64D, 0D));
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    AtomicInteger received = new AtomicInteger();
    FxViewers.reset();
    FxViewers.bumpTick();
    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(call -> {
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
      scheduling.verify(() -> Tasks.runEntity(same(player), any(Runnable.class)), times(expected));
    }
  }

  @Test
  void colorBackedParticlesUseEmitterColorAndExplicitDataRemainsUnchanged() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerData playerData = new PlayerData();
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(playerData);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0.0D, 64.0D, 0.0D));

    FxViewers.reset();
    FxViewers.bumpTick();
    FxBudget.resetTick();
    Rgb emitterColor = new Rgb(64, 32, 128);
    ParticleData.Dust dust = new ParticleData.Dust(new Rgb(255, 128, 32), 1.0F);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      FxEmitter emitter = FxEmitter.create(world, 0.0D, 64.0D, 0.0D, FxPriority.GAMEPLAY, 24.0D, true, false, emitterColor);
      emitter.particle(Particles.FLASH, 1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
      ownerTask.get().run();
      emitter.burst(Particles.FLASH, 1, 0.0D);
      ownerTask.get().run();
      emitter.particle(Particles.DUST, 1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, dust);
      ownerTask.get().run();

      verify(fx, times(2)).particle(player, new ParticleSpec(Particles.FLASH.type(), new ParticleData.Color(emitterColor)),
          0.0D, 64.0D, 0.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
      verify(fx).particle(player, new ParticleSpec(Particles.DUST.type(), dust), 0.0D, 64.0D, 0.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
  }

  @Test
  void dustParticlesWithoutDataUseEmitterColorAtUnitSize() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(new PlayerData());
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0.0D, 64.0D, 0.0D));

    FxViewers.reset();
    FxViewers.bumpTick();
    FxBudget.resetTick();
    Rgb emitterColor = new Rgb(40, 200, 90);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      FxEmitter emitter = FxEmitter.create(world, 0.0D, 64.0D, 0.0D, FxPriority.GAMEPLAY, 24.0D, true, false, emitterColor);
      emitter.line(Particles.DUST, 0.0D, 66.0D, 0.0D, 1);
      ownerTask.get().run();
      emitter.particle(Particles.DUST_COLOR_TRANSITION, 1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
      ownerTask.get().run();

      ArgumentCaptor<ParticleSpec> spec = ArgumentCaptor.forClass(ParticleSpec.class);
      ArgumentCaptor<Double> ys = ArgumentCaptor.forClass(Double.class);
      verify(fx, times(2)).particle(same(player), spec.capture(), eq(0.0D), ys.capture(), eq(0.0D), eq(1), eq(0.0D), eq(0.0D), eq(0.0D), eq(0.0D));

      assertThat(spec.getAllValues().get(0).type()).isEqualTo(Particles.DUST.type());
      assertThat(ys.getAllValues().get(0)).isEqualTo(65.0D);
      ParticleData.Dust dust = (ParticleData.Dust) spec.getAllValues().get(0).data();
      assertThat(dust.color()).isEqualTo(emitterColor);
      assertThat(dust.size()).isEqualTo(1.0F);

      assertThat(spec.getAllValues().get(1).type()).isEqualTo(Particles.DUST_COLOR_TRANSITION.type());
      assertThat(ys.getAllValues().get(1)).isEqualTo(64.0D);
      ParticleData.DustTransition transition = (ParticleData.DustTransition) spec.getAllValues().get(1).data();
      assertThat(transition.from()).isEqualTo(emitterColor);
      assertThat(transition.to()).isEqualTo(emitterColor);
      assertThat(transition.size()).isEqualTo(1.0F);
    }
  }

  @Test
  void orbitSpawnsOneParticlePerArmEvenlyAroundTheAnchor() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(new PlayerData());
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 10.0D, 64.0D, -4.0D));

    FxViewers.reset();
    FxViewers.bumpTick();
    FxBudget.resetTick();
    ParticleData.Dust dust = new ParticleData.Dust(new Rgb(10, 20, 30), 1.2F);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      FxEmitter emitter = FxEmitter.create(world, 10.0D, 64.0D, -4.0D, FxPriority.GAMEPLAY, 24.0D, true, false, Fx.WHITE);
      double start = Math.PI / 6.0D;
      emitter.orbit(Particles.DUST, 4, 2.0D, start, 1.5D, dust);
      ownerTask.get().run();

      ArgumentCaptor<ParticleSpec> specs = ArgumentCaptor.forClass(ParticleSpec.class);
      ArgumentCaptor<Double> xs = ArgumentCaptor.forClass(Double.class);
      ArgumentCaptor<Double> ys = ArgumentCaptor.forClass(Double.class);
      ArgumentCaptor<Double> zs = ArgumentCaptor.forClass(Double.class);
      verify(fx, times(4)).particle(same(player), specs.capture(), xs.capture(), ys.capture(), zs.capture(), eq(1), eq(0.0D), eq(0.0D), eq(0.0D), eq(0.0D));
      for (int arm = 0; arm < 4; arm++) {
        double angle = start + (arm * Math.PI / 2.0D);
        assertThat(specs.getAllValues().get(arm).type()).isEqualTo(Particles.DUST.type());
        assertThat(specs.getAllValues().get(arm).data()).isSameAs(dust);
        assertThat(xs.getAllValues().get(arm)).as("arm %d", arm).isCloseTo(10.0D + (2.0D * Math.cos(angle)), within(1.0E-9D));
        assertThat(ys.getAllValues().get(arm)).as("arm %d", arm).isEqualTo(65.5D);
        assertThat(zs.getAllValues().get(arm)).as("arm %d", arm).isCloseTo(-4.0D + (2.0D * Math.sin(angle)), within(1.0E-9D));
      }
    }
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    return player;
  }

  private static WorldRef world() {
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(UUID.randomUUID());
    return world;
  }
}
