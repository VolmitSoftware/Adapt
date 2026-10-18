package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FrostbiteFreezerTest {
  private final FrostbiteFreezer freezer = new FrostbiteFreezer();
  private MockedStatic<J> scheduling;

  @BeforeEach
  void scheduler() {
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt(), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
  }

  @AfterEach
  void close() {
    scheduling.close();
  }

  @Test
  void freezingLocksAFullFreezeMarksTheTargetAndUnlocksAfterTheDuration() {
    Zombie victim = victim();

    freezer.freeze(victim, 140);

    verify(victim).lockFreezeTicks(true);
    verify(victim).setFreezeTicks(140);
    assertThat(marked(victim)).isTrue();
    Runnable expiry = scheduled(victim, 140).getFirst();

    expiry.run();

    verify(victim).lockFreezeTicks(false);
    assertThat(marked(victim)).isFalse();
  }

  @Test
  void aNewerFreezeOutlivesTheEarlierUnlock() {
    Zombie victim = victim();
    freezer.freeze(victim, 140);
    freezer.freeze(victim, 200);
    Runnable earlier = scheduled(victim, 140).getFirst();
    Runnable later = scheduled(victim, 200).getFirst();

    earlier.run();

    verify(victim, never()).lockFreezeTicks(false);
    assertThat(marked(victim)).isTrue();

    later.run();

    verify(victim).lockFreezeTicks(false);
    assertThat(marked(victim)).isFalse();
  }

  @Test
  void aTargetThatUnloadsOrQuitsWhileFrozenIsThawedFirst() {
    Zombie frozen = victim();
    Zombie idle = victim();
    freezer.freeze(frozen, 140);

    freezer.thaw(frozen);
    freezer.thaw(idle);

    verify(frozen).lockFreezeTicks(false);
    assertThat(marked(frozen)).isFalse();
    verify(idle, never()).lockFreezeTicks(anyBoolean());
  }

  @Test
  void onlyStaleMarkedTargetsAreRecoveredAndLiveFreezesAreKept() {
    Zombie stale = victim();
    stale.getPersistentDataContainer().set(FrostbiteFreezer.KEY, PersistentDataType.BYTE, (byte) 1);
    Zombie live = victim();
    freezer.freeze(live, 140);

    freezer.recover(stale);
    freezer.recover(live);

    verify(stale).lockFreezeTicks(false);
    assertThat(marked(stale)).isFalse();
    verify(live, never()).lockFreezeTicks(false);
    assertThat(marked(live)).isTrue();
  }

  @Test
  void shuttingDownThawsEveryFrozenTargetAndARetiredUnlockLeavesTheMarkerForRecovery() {
    Zombie owned = victim();
    Zombie retiring = victim();
    freezer.freeze(owned, 140);
    freezer.freeze(retiring, 140);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(retiring), any(Runnable.class), eq(140), retired.capture()));

    retired.getValue().run();
    freezer.thawAll();

    verify(owned).lockFreezeTicks(false);
    assertThat(marked(owned)).isFalse();
    verify(retiring, never()).lockFreezeTicks(false);
    assertThat(marked(retiring)).isTrue();
  }

  private List<Runnable> scheduled(Zombie victim, int ticks) {
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(victim), task.capture(), eq(ticks), any(Runnable.class)), times(1));
    return task.getAllValues();
  }

  private static boolean marked(Zombie victim) {
    return victim.getPersistentDataContainer().get(FrostbiteFreezer.KEY, PersistentDataType.BYTE) != null;
  }

  private static Zombie victim() {
    Map<NamespacedKey, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().doAnswer(invocation -> values.put(invocation.getArgument(0), invocation.getArgument(2)))
        .when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    lenient().doAnswer(invocation -> values.remove(invocation.<NamespacedKey>getArgument(0)))
        .when(data).remove(any(NamespacedKey.class));
    Zombie victim = mock(Zombie.class);
    lenient().when(victim.getPersistentDataContainer()).thenReturn(data);
    lenient().when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(victim.getMaxFreezeTicks()).thenReturn(140);
    return victim;
  }
}
