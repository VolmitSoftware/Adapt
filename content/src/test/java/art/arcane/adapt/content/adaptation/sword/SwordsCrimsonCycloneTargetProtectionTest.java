package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.AdaptTestBase;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwordsCrimsonCycloneTargetProtectionTest extends AdaptTestBase {
  @Test
  void protectionDefaultsOffForTheAreaAttack() throws ReflectiveOperationException {
    Object config = newConfiguration();
    Field protection = config.getClass().getDeclaredField("ignorePassiveMobs");
    protection.setAccessible(true);
    assertThat(protection.getBoolean(config)).as(SwordsCrimsonCyclone.class.getSimpleName()).isFalse();
  }

  @Test
  void cycloneSkipsProtectedMobsBeforeItsCandidateLimit() {
    SwordCyclone cyclone = new SwordCyclone(mock(SwordsCrimsonCyclone.class));
    PlayerRef owner = mock(PlayerRef.class);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    LivingRef primary = passive();
    WorldRef world = mock(WorldRef.class);
    Loc center = new Loc(world, 0, 64, 0);
    LivingRef hostile = enemy("husk");
    List<EntityRef> candidates = crowdedCandidates(hostile);
    candidates.add(0, primary);
    when(world.nearbyEntities(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(candidates);
    assertThat(cyclone.collectCandidates(owner, primary, center, 5D, SwordCyclone.candidateLimit(16), true))
        .isEqualTo(List.of(hostile));
  }

  private static List<EntityRef> crowdedCandidates(LivingRef hostile) {
    List<EntityRef> candidates = new ArrayList<>(130);
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

  private static Object newConfiguration() throws ReflectiveOperationException {
    Class<?> configType = Class.forName(SwordsCrimsonCyclone.class.getName() + "$Config");
    Constructor<?> constructor = configType.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
