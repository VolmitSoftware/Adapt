package art.arcane.adapt.engine.attribute;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import org.mockito.invocation.InvocationOnMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptAttributeServiceTest {
  private Map<UUID, Map<Id, FakeAttribute>> fakes;
  private RecordingScheduler scheduler;
  private Id attributeA;
  private Id attributeB;
  private PlayerRef player;
  private UUID playerId;

  @BeforeEach
  void setUp() {
    fakes = new HashMap<>();
    scheduler = new RecordingScheduler();
    attributeA = Id.of("test", "attribute-a");
    attributeB = Id.of("test", "attribute-b");
    player = living(PlayerRef.class);
    playerId = UUID.randomUUID();
    when(player.uuid()).thenReturn(playerId);
  }

  private <T extends LivingRef> T living(Class<T> type) {
    T entity = mock(type);
    when(entity.hasAttribute(any())).thenAnswer((InvocationOnMock invocation) -> attributeOf(entity, invocation) != null);
    when(entity.modifier(any(), any())).thenAnswer((InvocationOnMock invocation) -> {
      FakeAttribute attribute = attributeOf(entity, invocation);
      return attribute == null ? null : attribute.modifiers.get(invocation.<Id>getArgument(1));
    });
    return entity;
  }

  private FakeAttribute attributeOf(LivingRef entity, InvocationOnMock invocation) {
    Map<Id, FakeAttribute> byAttribute = fakes.get(entity.uuid());
    return byAttribute == null ? null : byAttribute.get(invocation.<Id>getArgument(0));
  }

  private AdaptAttributeService service(Id... registryAttributes) {
    AdaptAttributeResolver resolver = new AdaptAttributeResolver() {
      @Override
      public boolean setTransientModifier(LivingRef entity, Id attribute, AttributeMod modifier) {
        FakeAttribute fake = find(entity, attribute);
        if (fake == null) {
          return false;
        }
        fake.modifiers.remove(modifier.key());
        fake.addTransientModifier(modifier.key(), modifier.amount(), modifier.operation());
        return true;
      }

      @Override
      public void removeModifier(LivingRef entity, Id attribute, Id key) {
        FakeAttribute fake = find(entity, attribute);
        if (fake != null) {
          fake.modifiers.remove(key);
        }
      }

      @Override
      public int removeAllInNamespace(LivingRef entity, Id attribute, String namespace) {
        FakeAttribute fake = find(entity, attribute);
        return fake == null ? 0 : fake.removeAllInNamespace(namespace);
      }
    };
    return new AdaptAttributeService(resolver, scheduler, Arrays.asList(registryAttributes));
  }

  private FakeAttribute find(LivingRef entity, Id attribute) {
    Map<Id, FakeAttribute> byAttribute = fakes.get(entity.uuid());
    return byAttribute == null ? null : byAttribute.get(attribute);
  }

  private FakeAttribute fake(LivingRef entity, Id attribute) {
    return fakes.computeIfAbsent(entity.uuid(), (UUID id) -> new HashMap<>())
        .computeIfAbsent(attribute, (Id a) -> new FakeAttribute());
  }

  @Test
  void applyPutsTransientAdaptModifierOnTheAttribute() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "wind-up", "boost", attributeA, 0.25, AttributeOperation.ADD_MULTIPLIED_TOTAL);

    assertThat(fake.modifiers).hasSize(1);
    Id key = fake.modifiers.keySet().iterator().next();
    assertThat(key.namespace()).isEqualTo("adaptbuff");
    assertThat(key.path()).isEqualTo("wind-up_boost");
    assertThat(fake.modifiers.get(key).amount()).isEqualTo(0.25);
    assertThat(fake.modifiers.get(key).operation()).isEqualTo(AttributeOperation.ADD_MULTIPLIED_TOTAL);
    assertThat(fake.persistentUsed).isFalse();
  }

  @Test
  void applyTwiceReplacesInsteadOfStacking() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "wind-up", null, attributeA, 0.1, AttributeOperation.ADD_VALUE);
    service.apply(player, "wind-up", null, attributeA, 0.7, AttributeOperation.ADD_VALUE);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().amount()).isEqualTo(0.7);
  }

  @Test
  void hostileAdaptationNamesAreSanitizedThroughTheService() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "Wind Up!", null, attributeA, 1, AttributeOperation.ADD_VALUE);

    assertThat(fake.modifiers.keySet().iterator().next().path()).isEqualTo("wind_up_");
  }

  @Test
  void nullTargetOrAttributeIsANoOp() {
    AdaptAttributeService service = service(attributeA);

    service.apply(null, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    service.apply(player, "wind-up", null, null, 1, AttributeOperation.ADD_VALUE);
    service.remove(player, "wind-up", null, null);
    service.removeAll(null, "wind-up");

    assertThat(fakes).isEmpty();
    assertThat(scheduler.delayedTasks).isEmpty();
  }

  @Test
  void nonFiniteModifierAmountsAreIgnored() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "wind-up", "nan", attributeA, Double.NaN, AttributeOperation.ADD_VALUE);
    service.apply(player, "wind-up", "positive-infinity", attributeA, Double.POSITIVE_INFINITY, AttributeOperation.ADD_VALUE);
    service.applyTimed(player, "wind-up", "negative-infinity", attributeA, Double.NEGATIVE_INFINITY, AttributeOperation.ADD_VALUE, 100);

    assertThat(fake.modifiers).isEmpty();
    assertThat(scheduler.delayedTasks).isEmpty();
  }

  @Test
  void removeStripsOnlyThatSlot() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(player, "armor-up", "chest", attributeA, 2, AttributeOperation.ADD_VALUE);
    service.apply(player, "armor-up", "legs", attributeA, 1, AttributeOperation.ADD_VALUE);

    service.remove(player, "armor-up", "chest", attributeA);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.keySet().iterator().next().path()).isEqualTo("armor-up_legs");
  }

  @Test
  void removeAllStripsOnlyThatAdaptationsEntries() {
    FakeAttribute fakeA = fake(player, attributeA);
    FakeAttribute fakeB = fake(player, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "steady-hands", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    service.apply(player, "steady-hands", "off", attributeB, 2, AttributeOperation.ADD_VALUE);
    service.apply(player, "adapt:hunter/luck", null, attributeA, 3, AttributeOperation.ADD_VALUE);

    service.removeAll(player, "steady-hands");

    assertThat(fakeA.modifiers).hasSize(1);
    assertThat(fakeA.modifiers.keySet().iterator().next().path()).isEqualTo("adapt/hunter/luck");
    assertThat(fakeB.modifiers).isEmpty();
  }

  @Test
  void clearAllAdaptSweepsRegistryAttributesAndReportsCount() {
    FakeAttribute fakeA = fake(player, attributeA);
    FakeAttribute fakeB = fake(player, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    service.apply(player, "adapt:hunter/luck", null, attributeB, 2, AttributeOperation.ADD_VALUE);
    Id foreign = Id.of("other", "plugin-modifier");
    fakeA.addTransientModifier(foreign, 5, AttributeOperation.ADD_VALUE);

    int removed = service.clearAllAdapt(player);

    assertThat(removed).isEqualTo(2);
    assertThat(fakeA.modifiers).containsOnlyKeys(foreign);
    assertThat(fakeB.modifiers).isEmpty();
  }

  @Test
  void clearAllAdaptSkipsNullRegistryEntriesAndMissingAttributes() {
    fake(player, attributeA);
    AdaptAttributeService service = service(attributeA, null, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);

    int removed = service.clearAllAdapt(player);

    assertThat(removed).isEqualTo(1);
  }

  @Test
  void timedRemovalExpiresModifier() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE, 100);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(scheduler.delayedTasks).hasSize(1);
    assertThat(scheduler.delays).containsExactly(100L);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).isEmpty();
  }

  @Test
  void reapplyTimedResetsTimerInsteadOfStacking() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE, 100);
    service.applyTimed(player, "wind-up", null, attributeA, 2, AttributeOperation.ADD_VALUE, 100);

    assertThat(scheduler.delayedTasks).hasSize(2);

    scheduler.delayedTasks.get(0).run();
    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().amount()).isEqualTo(2.0);

    scheduler.delayedTasks.get(1).run();
    assertThat(fake.modifiers).isEmpty();
  }

  @Test
  void untimedApplySupersedesPendingTimedRemoval() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE, 100);
    service.apply(player, "wind-up", null, attributeA, 3, AttributeOperation.ADD_VALUE);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().amount()).isEqualTo(3.0);
  }

  @Test
  void staleTimedRemovalCannotStripAFreshModifier() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE, 100);
    service.remove(player, "wind-up", null, attributeA);
    service.apply(player, "wind-up", null, attributeA, 9, AttributeOperation.ADD_VALUE);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().amount()).isEqualTo(9.0);
  }

  @Test
  void applyTimedWithNonPositiveDurationActsAsPermanentApply() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE, 0);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(scheduler.delayedTasks).isEmpty();
  }

  @Test
  void unregisterAdaptationScrubsTrackedEntities() {
    LivingRef zombie = living(LivingRef.class);
    when(zombie.uuid()).thenReturn(UUID.randomUUID());
    FakeAttribute playerFake = fake(player, attributeA);
    FakeAttribute zombieFake = fake(zombie, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    service.apply(zombie, "wind-up", null, attributeB, 2, AttributeOperation.ADD_VALUE);
    service.apply(player, "adapt:hunter/luck", null, attributeA, 3, AttributeOperation.ADD_VALUE);

    service.unregisterAdaptation("wind-up");

    assertThat(playerFake.modifiers).hasSize(1);
    assertThat(playerFake.modifiers.keySet().iterator().next().path()).isEqualTo("adapt/hunter/luck");
    assertThat(zombieFake.modifiers).isEmpty();
  }

  @Test
  void quitPrunesTrackingWithoutTouchingModifiers() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);

    PlayerQuitEvent quit = new PlayerQuitEvent(player);
    service.on(quit);

    service.unregisterAdaptation("wind-up");

    assertThat(fake.modifiers).hasSize(1);
  }

  @Test
  void quitCancelsPendingTimedRemovals() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE, 100);

    PlayerQuitEvent quit = new PlayerQuitEvent(player);
    service.on(quit);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).hasSize(1);
  }

  @Test
  void deathSweepsEveryAdaptModifier() {
    FakeAttribute fakeA = fake(player, attributeA);
    FakeAttribute fakeB = fake(player, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    service.apply(player, "adapt:hunter/luck", null, attributeB, 2, AttributeOperation.ADD_VALUE);

    PlayerDeathEvent death = mock(PlayerDeathEvent.class);
    when(death.player()).thenReturn(player);
    service.on(death);

    assertThat(fakeA.modifiers).isEmpty();
    assertThat(fakeB.modifiers).isEmpty();
  }

  @Test
  void joinSweepsStaleAdaptModifiers() {
    FakeAttribute fake = fake(player, attributeA);
    Id stale = Id.of("adaptbuff", "old-version-modifier");
    fake.addModifier(stale, 4, AttributeOperation.ADD_VALUE);
    AdaptAttributeService service = service(attributeA);

    PlayerJoinEvent join = new PlayerJoinEvent(player);
    service.on(join);

    assertThat(fake.modifiers).isEmpty();
  }

  @Test
  void entityDeathPrunesNonPlayerTracking() {
    LivingRef zombie = living(LivingRef.class);
    when(zombie.uuid()).thenReturn(UUID.randomUUID());
    FakeAttribute fake = fake(zombie, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(zombie, "taming-boost", null, attributeA, 1, AttributeOperation.ADD_VALUE);

    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(zombie);
    service.on(death);

    service.unregisterAdaptation("taming-boost");

    assertThat(fake.modifiers).hasSize(1);
  }

  @Test
  void shutdownSweepWaitsForScheduledEntityWork() throws Exception {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    scheduler.deferImmediate = true;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> cleanup = executor.submit(
          () -> service.sweepEverything(List.of(player), 1_000L));
      Runnable action = scheduler.immediateTasks.poll(10L, TimeUnit.SECONDS);

      assertThat(action).isNotNull();
      assertThat(cleanup.isDone()).isFalse();
      action.run();

      assertThat(cleanup.get(10L, TimeUnit.SECONDS)).isTrue();
      assertThat(fake.modifiers).isEmpty();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void retiredCleanupTaskCannotStripModifierFromALaterRuntimeGeneration() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeOperation.ADD_VALUE);
    scheduler.deferImmediate = true;

    service.remove(player, "wind-up", null, attributeA);
    Runnable delayedRemoval = scheduler.immediateTasks.remove();
    service.retireScheduledTasks();
    delayedRemoval.run();

    assertThat(fake.modifiers).hasSize(1);
  }

  private static final class RecordingScheduler implements AdaptAttributeScheduler {
    private final List<Runnable> delayedTasks = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final BlockingQueue<Runnable> immediateTasks = new LinkedBlockingQueue<>();
    private boolean deferImmediate;

    @Override
    public boolean runOnEntity(LivingRef entity, Runnable action) {
      if (deferImmediate) {
        return immediateTasks.add(action);
      }
      action.run();
      return true;
    }

    @Override
    public void runOnEntityLater(LivingRef entity, Runnable action, long delayTicks) {
      delayedTasks.add(action);
      delays.add(delayTicks);
    }
  }

  private static final class FakeAttribute {
    private final Map<Id, AttributeMod> modifiers = new LinkedHashMap<>();
    private boolean persistentUsed;

    void addModifier(Id key, double amount, AttributeOperation operation) {
      persistentUsed = true;
      put(key, amount, operation);
    }

    void addTransientModifier(Id key, double amount, AttributeOperation operation) {
      put(key, amount, operation);
    }

    private void put(Id key, double amount, AttributeOperation operation) {
      if (modifiers.containsKey(key)) {
        throw new IllegalArgumentException("Modifier is already applied on this attribute: " + key);
      }
      modifiers.put(key, new AttributeMod(key, amount, operation));
    }

    int removeAllInNamespace(String namespace) {
      int before = modifiers.size();
      modifiers.keySet().removeIf((Id key) -> key.namespace().equals(namespace));
      return before - modifiers.size();
    }
  }
}
