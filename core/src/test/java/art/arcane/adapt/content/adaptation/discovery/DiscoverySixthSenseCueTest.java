package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
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
    TestSense sense = spy(new TestSense(effects));
    sense.setConfig(new DiscoverySixthSense.Config());
    doReturn(5).when(sense).getActiveLevel(player);
    doReturn(true).when(sense).preferenceEnabled(player, DiscoverySixthSense.HUD);
    doReturn(false).when(sense).preferenceEnabled(player, DiscoverySixthSense.FULL);
    doReturn(CommonPreferences.Scale.FULL).when(sense).preference(player, DiscoverySixthSense.FREQUENCY);
    AdaptPlayer owner = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    doReturn(owner).when(sense).getPlayer(player);
    when(owner.getData()).thenReturn(data);
    Method apply = DiscoverySixthSense.class.getDeclaredMethod("acceptCandidateOwned", PlayerRef.class,
        StructureSense.Sensed.class);
    apply.setAccessible(true);

    try (MockedStatic<M> clock = mockStatic(M.class, CALLS_REAL_METHODS);
         MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class);
         MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      clock.when(M::ms).thenAnswer(call -> now.get());
      pulseThrottle(sense).mark(playerId);

      apply.invoke(sense, player, candidate(worldId, 200D));
      verify(effects).sound(any(Id.class), anyFloat(), anyFloat());
      verify(data).addStat("discovery.sixth-sense.senses", 1D);

      now.set(1200L);
      apply.invoke(sense, player, candidate(worldId, 100D));
      hud.verify(() -> AdaptHud.ambientStatus(player, "discovery-sixth-sense", "E 100"));
      verify(effects, times(1)).sound(any(Id.class), anyFloat(), anyFloat());
      verify(data, times(1)).addStat("discovery.sixth-sense.senses", 1D);

      now.set(5000L);
      apply.invoke(sense, player, candidate(worldId, 50D));
      hud.verify(() -> AdaptHud.ambientStatus(player, "discovery-sixth-sense", "E 50"));
      verify(effects, times(2)).sound(any(Id.class), anyFloat(), anyFloat());
      verify(data, times(2)).addStat("discovery.sixth-sense.senses", 1D);
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

  private static final class TestSense extends DiscoverySixthSense {
    private final FxEmitter effects;

    private TestSense(FxEmitter effects) {
      this.effects = effects;
    }

    @Override
    protected FxEmitter fx(Loc location, FxPriority priority) {
      return effects;
    }

    @Override
    protected FxEmitter fx(EntityRef entity, FxPriority priority) {
      return effects;
    }
  }
}
