package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcavationEarthMoverTargetTest {
  @Test
  void protectionDefaultsOffForEveryAreaAttack() throws ReflectiveOperationException {
    Object config = newConfiguration();
    Field protection = config.getClass().getDeclaredField("ignorePassiveMobs");
    protection.setAccessible(true);
    assertThat(protection.getBoolean(config)).as(ExcavationEarthMover.class.getSimpleName()).isFalse();
  }

  @Test
  void earthMoverSkipsNeutralEnemiesBeforeItsCandidateLimit() throws ReflectiveOperationException {
    ExcavationEarthMover adaptation = configured(true);
    WorldRef world = mock(WorldRef.class);
    Loc center = new Loc(world, 0, 64, 0);
    LivingRef hostile = enemy("husk");
    List<EntityRef> candidates = crowdedCandidates(hostile);
    when(world.nearbyEntities(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(new ArrayList<>(candidates));
    Method collect = ExcavationEarthMover.class.getDeclaredMethod("collectCandidates", PlayerRef.class, Loc.class, double.class);
    collect.setAccessible(true);

    assertThat((List<?>) collect.invoke(adaptation, mock(PlayerRef.class), center, 5D)).isEqualTo(List.of(hostile));
  }

  @Test
  void earthMoverPersonalProtectionFiltersBeforeItsCandidateLimit() throws ReflectiveOperationException {
    ExcavationEarthMover adaptation = configured(false);
    PlayerRef owner = mock(PlayerRef.class);
    doReturn(true).when(adaptation).preferenceEnabled(owner, ExcavationEarthMover.IGNORE_PASSIVE);
    WorldRef world = mock(WorldRef.class);
    Loc center = new Loc(world, 0, 64, 0);
    LivingRef hostile = enemy("husk");
    List<EntityRef> candidates = crowdedCandidates(hostile);
    when(world.nearbyEntities(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(new ArrayList<>(candidates));
    Method collect = ExcavationEarthMover.class.getDeclaredMethod("collectCandidates", PlayerRef.class, Loc.class, double.class);
    collect.setAccessible(true);

    assertThat((List<?>) collect.invoke(adaptation, owner, center, 5D)).isEqualTo(List.of(hostile));
  }

  private static List<EntityRef> crowdedCandidates(LivingRef hostile) {
    List<EntityRef> candidates = new ArrayList<>(129);
    for (int index = 0; index < 64; index++) {
      candidates.add(passive());
      candidates.add(enemy("enderman"));
    }
    candidates.add(hostile);
    return candidates;
  }

  private static LivingRef passive() {
    LivingRef passive = mock(LivingRef.class);
    when(passive.uuid()).thenReturn(UUID.randomUUID());
    when(passive.type()).thenReturn(Id.minecraft("cow"));
    return passive;
  }

  private static LivingRef enemy(String type) {
    LivingRef enemy = mock(LivingRef.class);
    when(enemy.uuid()).thenReturn(UUID.randomUUID());
    when(enemy.type()).thenReturn(Id.minecraft(type));
    when(enemy.has(EntityTrait.ENEMY)).thenReturn(true);
    return enemy;
  }

  private static ExcavationEarthMover configured(boolean enabled) throws ReflectiveOperationException {
    Object config = newConfiguration();
    Field protection = config.getClass().getDeclaredField("ignorePassiveMobs");
    protection.setAccessible(true);
    protection.setBoolean(config, enabled);
    ExcavationEarthMover adaptation = mock(ExcavationEarthMover.class, CALLS_REAL_METHODS);
    doReturn(config).when(adaptation).getConfig();
    return adaptation;
  }

  private static Object newConfiguration() throws ReflectiveOperationException {
    Class<?> configType = Class.forName(ExcavationEarthMover.class.getName() + "$Config");
    Constructor<?> constructor = configType.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
