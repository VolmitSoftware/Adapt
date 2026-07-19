package art.arcane.adapt.api.attribute;

import art.arcane.adapt.api.version.IAttribute;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptAttributeServiceTest {
  private Map<UUID, Map<Attribute, FakeAttribute>> fakes;
  private RecordingScheduler scheduler;
  private Attribute attributeA;
  private Attribute attributeB;
  private Player player;
  private UUID playerId;

  @BeforeEach
  void setUp() {
    fakes = new HashMap<>();
    scheduler = new RecordingScheduler();
    attributeA = new StubAttribute("attribute-a");
    attributeB = new StubAttribute("attribute-b");
    player = mock(Player.class);
    playerId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
  }

  private AdaptAttributeService service(Attribute... registryAttributes) {
    AdaptAttributeResolver resolver = (entity, attribute) -> {
      Map<Attribute, FakeAttribute> byAttribute = fakes.get(entity.getUniqueId());
      return byAttribute == null ? null : byAttribute.get(attribute);
    };
    return new AdaptAttributeService(resolver, scheduler, Arrays.asList(registryAttributes));
  }

  private FakeAttribute fake(LivingEntity entity, Attribute attribute) {
    return fakes.computeIfAbsent(entity.getUniqueId(), id -> new HashMap<>())
        .computeIfAbsent(attribute, a -> new FakeAttribute());
  }

  @Test
  void applyPutsTransientAdaptModifierOnTheAttribute() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "wind-up", "boost", attributeA, 0.25, AttributeModifier.Operation.MULTIPLY_SCALAR_1);

    assertThat(fake.modifiers).hasSize(1);
    NamespacedKey key = fake.modifiers.keySet().iterator().next();
    assertThat(key.getNamespace()).isEqualTo("adaptbuff");
    assertThat(key.getKey()).isEqualTo("wind-up_boost");
    assertThat(fake.modifiers.get(key).getAmount()).isEqualTo(0.25);
    assertThat(fake.modifiers.get(key).getOperation()).isEqualTo(AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    assertThat(fake.persistentUsed).isFalse();
  }

  @Test
  void applyTwiceReplacesInsteadOfStacking() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "wind-up", null, attributeA, 0.1, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "wind-up", null, attributeA, 0.7, AttributeModifier.Operation.ADD_NUMBER);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().getAmount()).isEqualTo(0.7);
  }

  @Test
  void hostileAdaptationNamesAreSanitizedThroughTheService() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.apply(player, "Wind Up!", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);

    assertThat(fake.modifiers.keySet().iterator().next().getKey()).isEqualTo("wind_up_");
  }

  @Test
  void nullTargetOrAttributeIsANoOp() {
    AdaptAttributeService service = service(attributeA);

    service.apply(null, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "wind-up", null, null, 1, AttributeModifier.Operation.ADD_NUMBER);
    service.remove(player, "wind-up", null, null);
    service.removeAll(null, "wind-up");

    assertThat(fakes).isEmpty();
    assertThat(scheduler.delayedTasks).isEmpty();
  }

  @Test
  void removeStripsOnlyThatSlot() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(player, "armor-up", "chest", attributeA, 2, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "armor-up", "legs", attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);

    service.remove(player, "armor-up", "chest", attributeA);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.keySet().iterator().next().getKey()).isEqualTo("armor-up_legs");
  }

  @Test
  void removeAllStripsOnlyThatAdaptationsEntries() {
    FakeAttribute fakeA = fake(player, attributeA);
    FakeAttribute fakeB = fake(player, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "steady-hands", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "steady-hands", "off", attributeB, 2, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "hunter-luck", null, attributeA, 3, AttributeModifier.Operation.ADD_NUMBER);

    service.removeAll(player, "steady-hands");

    assertThat(fakeA.modifiers).hasSize(1);
    assertThat(fakeA.modifiers.keySet().iterator().next().getKey()).isEqualTo("hunter-luck");
    assertThat(fakeB.modifiers).isEmpty();
  }

  @Test
  void clearAllAdaptSweepsRegistryAttributesAndReportsCount() {
    FakeAttribute fakeA = fake(player, attributeA);
    FakeAttribute fakeB = fake(player, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "hunter-luck", null, attributeB, 2, AttributeModifier.Operation.ADD_NUMBER);
    NamespacedKey foreign = new NamespacedKey("other", "plugin-modifier");
    fakeA.addTransientModifier(UUID.randomUUID(), foreign, 5, AttributeModifier.Operation.ADD_NUMBER);

    int removed = service.clearAllAdapt(player);

    assertThat(removed).isEqualTo(2);
    assertThat(fakeA.modifiers).containsOnlyKeys(foreign);
    assertThat(fakeB.modifiers).isEmpty();
  }

  @Test
  void clearAllAdaptSkipsNullRegistryEntriesAndMissingAttributes() {
    fake(player, attributeA);
    AdaptAttributeService service = service(attributeA, null, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);

    int removed = service.clearAllAdapt(player);

    assertThat(removed).isEqualTo(1);
  }

  @Test
  void timedRemovalExpiresModifier() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER, 100);

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

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER, 100);
    service.applyTimed(player, "wind-up", null, attributeA, 2, AttributeModifier.Operation.ADD_NUMBER, 100);

    assertThat(scheduler.delayedTasks).hasSize(2);

    scheduler.delayedTasks.get(0).run();
    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().getAmount()).isEqualTo(2.0);

    scheduler.delayedTasks.get(1).run();
    assertThat(fake.modifiers).isEmpty();
  }

  @Test
  void untimedApplySupersedesPendingTimedRemoval() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER, 100);
    service.apply(player, "wind-up", null, attributeA, 3, AttributeModifier.Operation.ADD_NUMBER);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().getAmount()).isEqualTo(3.0);
  }

  @Test
  void staleTimedRemovalCannotStripAFreshModifier() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER, 100);
    service.remove(player, "wind-up", null, attributeA);
    service.apply(player, "wind-up", null, attributeA, 9, AttributeModifier.Operation.ADD_NUMBER);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).hasSize(1);
    assertThat(fake.modifiers.values().iterator().next().getAmount()).isEqualTo(9.0);
  }

  @Test
  void applyTimedWithNonPositiveDurationActsAsPermanentApply() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);

    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER, 0);

    assertThat(fake.modifiers).hasSize(1);
    assertThat(scheduler.delayedTasks).isEmpty();
  }

  @Test
  void unregisterAdaptationScrubsTrackedEntities() {
    LivingEntity zombie = mock(LivingEntity.class);
    when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
    FakeAttribute playerFake = fake(player, attributeA);
    FakeAttribute zombieFake = fake(zombie, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(zombie, "wind-up", null, attributeB, 2, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "hunter-luck", null, attributeA, 3, AttributeModifier.Operation.ADD_NUMBER);

    service.unregisterAdaptation("wind-up");

    assertThat(playerFake.modifiers).hasSize(1);
    assertThat(playerFake.modifiers.keySet().iterator().next().getKey()).isEqualTo("hunter-luck");
    assertThat(zombieFake.modifiers).isEmpty();
  }

  @Test
  void quitPrunesTrackingWithoutTouchingModifiers() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);

    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.getPlayer()).thenReturn(player);
    service.on(quit);

    service.unregisterAdaptation("wind-up");

    assertThat(fake.modifiers).hasSize(1);
  }

  @Test
  void quitCancelsPendingTimedRemovals() {
    FakeAttribute fake = fake(player, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.applyTimed(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER, 100);

    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.getPlayer()).thenReturn(player);
    service.on(quit);

    scheduler.delayedTasks.get(0).run();

    assertThat(fake.modifiers).hasSize(1);
  }

  @Test
  void deathSweepsEveryAdaptModifier() {
    FakeAttribute fakeA = fake(player, attributeA);
    FakeAttribute fakeB = fake(player, attributeB);
    AdaptAttributeService service = service(attributeA, attributeB);
    service.apply(player, "wind-up", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);
    service.apply(player, "hunter-luck", null, attributeB, 2, AttributeModifier.Operation.ADD_NUMBER);

    PlayerDeathEvent death = mock(PlayerDeathEvent.class);
    when(death.getEntity()).thenReturn(player);
    service.on(death);

    assertThat(fakeA.modifiers).isEmpty();
    assertThat(fakeB.modifiers).isEmpty();
  }

  @Test
  void joinSweepsStaleAdaptModifiers() {
    FakeAttribute fake = fake(player, attributeA);
    NamespacedKey stale = new NamespacedKey("adaptbuff", "old-version-modifier");
    fake.addModifier(UUID.randomUUID(), stale, 4, AttributeModifier.Operation.ADD_NUMBER);
    AdaptAttributeService service = service(attributeA);

    PlayerJoinEvent join = mock(PlayerJoinEvent.class);
    when(join.getPlayer()).thenReturn(player);
    service.on(join);

    assertThat(fake.modifiers).isEmpty();
  }

  @Test
  void entityDeathPrunesNonPlayerTracking() {
    LivingEntity zombie = mock(LivingEntity.class);
    when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
    FakeAttribute fake = fake(zombie, attributeA);
    AdaptAttributeService service = service(attributeA);
    service.apply(zombie, "taming-boost", null, attributeA, 1, AttributeModifier.Operation.ADD_NUMBER);

    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.getEntity()).thenReturn(zombie);
    service.on(death);

    service.unregisterAdaptation("taming-boost");

    assertThat(fake.modifiers).hasSize(1);
  }

  private static final class RecordingScheduler implements AdaptAttributeScheduler {
    private final List<Runnable> delayedTasks = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();

    @Override
    public void runOnEntity(LivingEntity entity, Runnable action) {
      action.run();
    }

    @Override
    public void runOnEntityLater(LivingEntity entity, Runnable action, long delayTicks) {
      delayedTasks.add(action);
      delays.add(delayTicks);
    }
  }

  private static final class FakeAttribute implements IAttribute {
    private final Map<NamespacedKey, Modifier> modifiers = new LinkedHashMap<>();
    private boolean persistentUsed;

    @Override
    public double getValue() {
      return 0;
    }

    @Override
    public double getDefaultValue() {
      return 0;
    }

    @Override
    public double getBaseValue() {
      return 0;
    }

    @Override
    public void setBaseValue(double baseValue) {
    }

    @Override
    public void addModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
      persistentUsed = true;
      put(uuid, key, amount, operation);
    }

    @Override
    public void addTransientModifier(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
      put(uuid, key, amount, operation);
    }

    private void put(UUID uuid, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
      if (modifiers.containsKey(key)) {
        throw new IllegalArgumentException("Modifier is already applied on this attribute: " + key);
      }
      modifiers.put(key, new Modifier(uuid, key, amount, operation));
    }

    @Override
    public boolean hasModifier(UUID uuid, NamespacedKey key) {
      return modifiers.containsKey(key);
    }

    @Override
    public void removeModifier(UUID uuid, NamespacedKey key) {
      modifiers.remove(key);
    }

    @Override
    public KList<Modifier> getModifier(UUID uuid, NamespacedKey key) {
      KList<Modifier> matched = new KList<>();
      Modifier modifier = modifiers.get(key);
      if (modifier != null) {
        matched.add(modifier);
      }
      return matched;
    }

    @Override
    public KList<Modifier> getAllModifiers() {
      return new KList<>(modifiers.values());
    }

    @Override
    public int removeAllInNamespace(String namespace) {
      int before = modifiers.size();
      modifiers.keySet().removeIf(key -> key.getNamespace().equals(namespace));
      return before - modifiers.size();
    }
  }
}
