package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.fx.ViewerDisplayPort;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.RuntimeSpy;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoverySixthSenseCueTest extends AdaptTestBase {
  @AfterEach
  void removeDisplays() {
    AdaptPorts.uninstall(ViewerDisplayPort.class);
  }

  @Test
  void firstCueIsImmediateAndNearerHudUpdatesWaitForTheNextCueInterval() throws ReflectiveOperationException {
    AtomicLong now = new AtomicLong(1000L);
    PlayerRef player = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    when(player.uuid()).thenReturn(playerId);
    when(world.uid()).thenReturn(worldId);
    when(player.location()).thenAnswer(call -> new Loc(world, 0D, 100D, 0D));
    when(player.eyeLocation()).thenAnswer(call -> new Loc(world, 0D, 101.6D, 0D));
    when(AdaptCore.platform().host().items()).thenReturn(mock(ItemOps.class));
    AdaptPorts.install(ViewerDisplayPort.class, mock(ViewerDisplayPort.class));

    FxEmitter effects = mock(FxEmitter.class, RETURNS_SELF);
    DiscoverySixthSense original = TestContent.adaptation(Id.adapt("discovery"), DiscoverySixthSense::new);
    ComponentHost.of(original).useFixedConfig(new DiscoverySixthSense.Config());
    DiscoverySixthSense sense = spy(original);
    AdaptationHost runtime = RuntimeSpy.install(original, sense);
    doReturn(effects).when(runtime).fx(any(Loc.class), any(FxPriority.class));
    doReturn(effects).when(runtime).fx(any(EntityRef.class), any(FxPriority.class));
    doReturn(5).when(sense).getActiveLevel(player);
    doReturn(true).when(sense).preferenceEnabled(player, DiscoverySixthSense.HUD);
    doReturn(false).when(sense).preferenceEnabled(player, DiscoverySixthSense.FULL);
    doReturn(CommonPreferences.Scale.FULL).when(sense).preference(player, DiscoverySixthSense.FREQUENCY);
    Method apply = DiscoverySixthSense.class.getDeclaredMethod("acceptCandidateOwned", PlayerRef.class,
        StructureSense.Sensed.class);
    apply.setAccessible(true);

    try (MockedStatic<M> clock = mockStatic(M.class, CALLS_REAL_METHODS);
         MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class);
         MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      clock.when(M::ms).thenAnswer(call -> now.get());
      language.when(() -> AdaptLanguage.key(anyString())).thenAnswer(call -> TextKey.of(call.getArgument(0), ""));
      pulseThrottle(sense).mark(playerId);

      apply.invoke(sense, player, candidate(worldId, 200D));
      verify(effects).sound(any(Id.class), anyFloat(), anyFloat());
      verify(runtime).addStat(player, "discovery.sixth-sense.senses", 1D);

      now.set(1200L);
      apply.invoke(sense, player, candidate(worldId, 100D));
      hud.verify(() -> AdaptHud.ambientStatus(player, "discovery-sixth-sense", "E 100"));
      verify(effects, times(1)).sound(any(Id.class), anyFloat(), anyFloat());
      verify(runtime, times(1)).addStat(player, "discovery.sixth-sense.senses", 1D);

      now.set(5000L);
      apply.invoke(sense, player, candidate(worldId, 50D));
      hud.verify(() -> AdaptHud.ambientStatus(player, "discovery-sixth-sense", "E 50"));
      verify(effects, times(2)).sound(any(Id.class), anyFloat(), anyFloat());
      verify(runtime, times(2)).addStat(player, "discovery.sixth-sense.senses", 1D);
    }
  }

  private StructureSense.Sensed candidate(UUID worldId, double x) {
    return new StructureSense.Sensed(worldId, x, 0D, "Synthetic Structure", "",
        new Box(x - 1D, 0D, -1D, x + 1D, 70D, 1D));
  }

  private Cooldowns pulseThrottle(DiscoverySixthSense sense) throws ReflectiveOperationException {
    Field field = DiscoverySixthSense.class.getDeclaredField("pulseThrottle");
    field.setAccessible(true);
    return (Cooldowns) field.get(sense);
  }
}
