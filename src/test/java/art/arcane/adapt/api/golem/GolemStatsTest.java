package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Snowman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GolemStatsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

  private final List<GolemStatSource> registered = new ArrayList<>();
  private FakeGolemPlatform platform;
  private Snowman golem;
  private AtomicReference<Double> health;
  private GolemHandle handle;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    platform.join(OWNER, new GolemRoster());
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    golem = GolemTestEntities.entity(Snowman.class);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    health = new AtomicReference<>(4D);
    lenient().when(golem.getHealth()).thenAnswer(invocation -> health.get());
    lenient().doAnswer(invocation -> {
      health.set(invocation.getArgument(0));
      return null;
    }).when(golem).setHealth(anyDouble());
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.FROST, GolemCoreType.BLANK,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    handle = new GolemHandle(golem, state, platform);
  }

  @AfterEach
  void tearDown() {
    for (GolemStatSource source : registered) {
      GolemStats.unregister(source);
    }
  }

  @Test
  void modifierKeysNameTheSourceAndTheAttribute() {
    assertThat(GolemStats.key("frost_frame", GolemStat.MAX_HEALTH)).isEqualTo(new NamespacedKey("adapt", "golem_frost_frame_max_health"));
    assertThat(GolemStats.key("iron_frame", GolemStat.ARMOR_TOUGHNESS).toString()).isEqualTo("adapt:golem_iron_frame_armor_toughness");
    assertThat(GolemStats.key("rune", GolemStat.KNOCKBACK_RESISTANCE).toString()).isEqualTo("adapt:golem_rune_knockback_resistance");
    assertThat(GolemStat.values()).extracting(GolemStat::id)
        .containsExactly("max_health", "armor", "armor_toughness", "attack_damage", "movement_speed", "knockback_resistance");
    assertThatThrownBy(() -> GolemStats.key("Frost Frame", GolemStat.ARMOR)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void statKeysAreEveryGolemKeyExceptTheScaleKeys() {
    assertThat(GolemStats.isStatKey(GolemStats.key("frost_frame", GolemStat.MAX_HEALTH))).isTrue();
    assertThat(GolemStats.isStatKey(new NamespacedKey("adapt", "golem_retired_source_armor"))).isTrue();
    assertThat(GolemStats.isStatKey(GolemScale.MODIFIER_KEY)).isFalse();
    assertThat(GolemStats.isStatKey(GolemScale.RITUAL_KEY)).isFalse();
    assertThat(GolemStats.isStatKey(new NamespacedKey("adapt", "frost_frame_max_health"))).isFalse();
    assertThat(GolemStats.isStatKey(new NamespacedKey("other", "golem_frost_frame_max_health"))).isFalse();
    assertThat(GolemStats.isStatKey(null)).isFalse();
  }

  @Test
  void aPlanSumsEachSourcesDeltasPerAttributeAndDropsZeroes() {
    Map<String, List<GolemStatDelta>> deltas = new LinkedHashMap<>();
    deltas.put("iron_frame", List.of(GolemStatDelta.add(GolemStat.ARMOR, 2D), GolemStatDelta.add(GolemStat.ARMOR, 3D),
        GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 0D)));
    deltas.put("colossus", List.of(GolemStatDelta.multiplyBase(GolemStat.ATTACK_DAMAGE, 0.2D)));
    deltas.put("rune", List.of(GolemStatDelta.add(GolemStat.MOVEMENT_SPEED, Double.NaN)));

    GolemStatPlan plan = GolemStatPlan.of(deltas);

    assertThat(plan.modifiers()).containsOnlyKeys(GolemStats.key("iron_frame", GolemStat.ARMOR), GolemStats.key("colossus", GolemStat.ATTACK_DAMAGE));
    assertThat(plan.modifiers().get(GolemStats.key("iron_frame", GolemStat.ARMOR)))
        .isEqualTo(new GolemStatDelta(GolemStat.ARMOR, AttributeModifier.Operation.ADD_NUMBER, 5D));
    assertThat(plan.modifiers().get(GolemStats.key("colossus", GolemStat.ATTACK_DAMAGE)))
        .isEqualTo(new GolemStatDelta(GolemStat.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_SCALAR, 0.2D));
    assertThat(plan.modifiers(GolemStat.ARMOR)).containsOnlyKeys(GolemStats.key("iron_frame", GolemStat.ARMOR));
    assertThat(plan.modifiers(GolemStat.MAX_HEALTH)).isEmpty();
    assertThat(GolemStatPlan.of(Map.of())).isEqualTo(GolemStatPlan.EMPTY);
    assertThat(GolemStatPlan.of(deltas)).isEqualTo(plan);
  }

  @Test
  void aSourceCannotMixOperationsOnOneAttribute() {
    Map<String, List<GolemStatDelta>> deltas = Map.of("iron_frame",
        List.of(GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 1.5D), GolemStatDelta.multiplyBase(GolemStat.ATTACK_DAMAGE, 0.2D)));

    assertThatThrownBy(() -> GolemStatPlan.of(deltas)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("iron_frame");
  }

  @Test
  void raisingMaximumHealthHealsByTheGainAboveTheHighestMaximumAndLoweringItClamps() {
    assertThat(GolemStats.healthAfter(10D, 24D, 28D, 24D)).isCloseTo(14D, offset(1e-9));
    assertThat(GolemStats.healthAfter(4D, 4D, 24D, 0D)).isCloseTo(24D, offset(1e-9));
    assertThat(GolemStats.healthAfter(23D, 24D, 28D, 0D)).isCloseTo(27D, offset(1e-9));
    assertThat(GolemStats.healthAfter(4D, 4D, 24D, 24D)).isCloseTo(4D, offset(1e-9));
    assertThat(GolemStats.healthAfter(4D, 4D, 30D, 24D)).isCloseTo(10D, offset(1e-9));
    assertThat(GolemStats.healthAfter(20D, 24D, 8D, 24D)).isCloseTo(8D, offset(1e-9));
    assertThat(GolemStats.healthAfter(3D, 24D, 8D, 24D)).isCloseTo(3D, offset(1e-9));
    assertThat(GolemStats.healthAfter(10D, 24D, 24D, 24D)).isCloseTo(10D, offset(1e-9));
    assertThat(GolemStats.healthAfter(0D, 4D, 24D, 0D)).isZero();
    assertThat(GolemStats.healthAfter(10D, Double.NaN, 24D, 0D)).isCloseTo(10D, offset(1e-9));
  }

  @Test
  void relearningALevelDoesNotHealAgainButANewHighestMaximumHealsTheDifference() {
    FakeAttribute maxHealth = platform.stat(golem, GolemStat.MAX_HEALTH, 4D);
    AtomicReference<Double> bonus = new AtomicReference<>(20D);
    register(new GolemStatSource() {
      @Override
      public String id() {
        return "frost_frame";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle golemHandle) {
        return List.of(GolemStatDelta.add(GolemStat.MAX_HEALTH, bonus.get()));
      }
    });
    GolemStats.recompute(handle);
    assertThat(health.get()).isEqualTo(24D);
    assertThat(GolemState.read(golem).orElseThrow().maxHealthMark()).isEqualTo(24D);
    health.set(10D);

    bonus.set(0D);
    GolemStats.recompute(handle);
    assertThat(health.get()).isEqualTo(4D);

    bonus.set(20D);
    GolemStats.recompute(handle);
    assertThat(maxHealth.value()).isEqualTo(24D);
    assertThat(health.get()).isEqualTo(4D);

    bonus.set(26D);
    GolemStats.recompute(handle);
    assertThat(health.get()).isEqualTo(10D);
    assertThat(handle.state().maxHealthMark()).isEqualTo(30D);
  }

  @Test
  void recomputeAppliesPersistentKeyedModifiersAndHealsTheGolem() {
    FakeAttribute maxHealth = platform.stat(golem, GolemStat.MAX_HEALTH, 4D);
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    register(source("frost_frame", GolemStatDelta.add(GolemStat.MAX_HEALTH, 20D), GolemStatDelta.add(GolemStat.ARMOR, 2D)));

    GolemStats.recompute(handle);

    assertThat(maxHealth.modifier(GolemStats.key("frost_frame", GolemStat.MAX_HEALTH))).hasValueSatisfying(modifier -> {
      assertThat(modifier.getAmount()).isEqualTo(20D);
      assertThat(modifier.getOperation()).isEqualTo(AttributeModifier.Operation.ADD_NUMBER);
    });
    assertThat(armor.value()).isEqualTo(2D);
    assertThat(maxHealth.value()).isEqualTo(24D);
    assertThat(health.get()).isEqualTo(24D);
    verify(maxHealth.instance, never()).addTransientModifier(any(AttributeModifier.class));
  }

  @Test
  void anUnchangedPlanTouchesNothingAndAChangedPlanReplacesOnlyOurModifiers() {
    FakeAttribute maxHealth = platform.stat(golem, GolemStat.MAX_HEALTH, 4D);
    AttributeModifier foreign = new AttributeModifier(new NamespacedKey("other", "bonus"), 1D, AttributeModifier.Operation.ADD_NUMBER);
    AttributeModifier scale = new AttributeModifier(GolemScale.MODIFIER_KEY, -0.5D, AttributeModifier.Operation.ADD_NUMBER);
    maxHealth.modifiers.add(foreign);
    maxHealth.modifiers.add(scale);
    AtomicReference<Double> bonus = new AtomicReference<>(8D);
    AtomicInteger calls = new AtomicInteger();
    register(new GolemStatSource() {
      @Override
      public String id() {
        return "frost_frame";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle golemHandle) {
        calls.incrementAndGet();
        return List.of(GolemStatDelta.add(GolemStat.MAX_HEALTH, bonus.get()));
      }
    });
    GolemStats.recompute(handle);
    health.set(9D);
    List<AttributeModifier> before = List.copyOf(maxHealth.modifiers);

    GolemStats.recompute(handle);

    assertThat(maxHealth.modifiers).containsExactlyElementsOf(before);
    assertThat(health.get()).isEqualTo(9D);
    assertThat(calls).hasValue(2);

    bonus.set(12D);
    GolemStats.recompute(handle);

    assertThat(maxHealth.modifiers).contains(foreign, scale).hasSize(3);
    assertThat(maxHealth.modifier(GolemStats.key("frost_frame", GolemStat.MAX_HEALTH))).hasValueSatisfying(modifier ->
        assertThat(modifier.getAmount()).isEqualTo(12D));
    assertThat(health.get()).isEqualTo(13D);

    bonus.set(4D);
    GolemStats.recompute(handle);

    assertThat(maxHealth.value()).isEqualTo(8.5D);
    assertThat(health.get()).isEqualTo(8.5D);
  }

  @Test
  void theFirstRecomputeOfAnAdoptedGolemKeepsMatchingModifiersAndDropsStaleOnes() {
    FakeAttribute maxHealth = platform.stat(golem, GolemStat.MAX_HEALTH, 4D);
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AttributeModifier persisted = new AttributeModifier(GolemStats.key("frost_frame", GolemStat.MAX_HEALTH), 20D, AttributeModifier.Operation.ADD_NUMBER);
    AttributeModifier stale = new AttributeModifier(GolemStats.key("retired", GolemStat.ARMOR), 3D, AttributeModifier.Operation.ADD_NUMBER);
    maxHealth.modifiers.add(persisted);
    armor.modifiers.add(stale);
    health.set(15D);
    register(source("frost_frame", GolemStatDelta.add(GolemStat.MAX_HEALTH, 20D)));

    GolemStats.recompute(handle);

    assertThat(maxHealth.modifiers).containsExactly(persisted);
    verify(maxHealth.instance, never()).removeModifier(any(AttributeModifier.class));
    verify(maxHealth.instance, never()).addModifier(any(AttributeModifier.class));
    assertThat(armor.modifiers).isEmpty();
    assertThat(health.get()).isEqualTo(15D);
  }

  @Test
  void recomputeWaitsForTheOwnersDataAndKeepsModifiersWhenASourceFails() {
    FakeAttribute maxHealth = platform.stat(golem, GolemStat.MAX_HEALTH, 4D);
    register(source("frost_frame", GolemStatDelta.add(GolemStat.MAX_HEALTH, 20D)));
    platform.quit(OWNER);

    GolemStats.recompute(handle);

    assertThat(maxHealth.modifiers).isEmpty();

    platform.join(OWNER, new GolemRoster());
    GolemStats.recompute(handle);
    register(new GolemStatSource() {
      @Override
      public String id() {
        return "broken";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle golemHandle) {
        throw new IllegalStateException("broken source");
      }
    });

    GolemStats.recompute(handle);

    assertThat(maxHealth.value()).isEqualTo(24D);
  }

  @Test
  void recomputeReadsTheCurrentPatinaStageOfCopperGolemsOnly() {
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    CopperGolem copper = GolemTestEntities.entity(CopperGolem.class);
    lenient().when(copper.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    GolemHandle copperHandle = new GolemHandle(copper, GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.COPPER,
        GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 64, 0))), platform);
    FakeAttribute armor = platform.stat(copper, GolemStat.ARMOR, 0D);
    platform.patina.put(copper.getUniqueId(), 3);
    platform.patina.put(golem.getUniqueId(), 3);
    AtomicInteger seen = new AtomicInteger(-1);
    register(new GolemStatSource() {
      @Override
      public String id() {
        return "patina";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle target) {
        if (target == handle) {
          seen.set(target.patinaStage());
        }
        return List.of(GolemStatDelta.add(GolemStat.ARMOR, target.patinaStage() * 2D));
      }
    });

    GolemStats.recompute(copperHandle);
    GolemStats.recompute(handle);

    assertThat(copperHandle.patinaStage()).isEqualTo(3);
    assertThat(armor.value()).isEqualTo(6D);
    assertThat(seen.get()).isZero();
  }

  @Test
  void clearingRemovesEveryGolemStatModifierAndClampsHealth() {
    FakeAttribute maxHealth = platform.stat(golem, GolemStat.MAX_HEALTH, 4D);
    FakeAttribute speed = platform.stat(golem, GolemStat.MOVEMENT_SPEED, 0.2D);
    AttributeModifier foreign = new AttributeModifier(new NamespacedKey("other", "slow"), -0.1D, AttributeModifier.Operation.ADD_SCALAR);
    speed.modifiers.add(foreign);
    speed.modifiers.add(new AttributeModifier(GolemStats.key("retired", GolemStat.MOVEMENT_SPEED), 0.1D, AttributeModifier.Operation.ADD_SCALAR));
    register(source("frost_frame", GolemStatDelta.add(GolemStat.MAX_HEALTH, 20D)));
    GolemStats.recompute(handle);

    GolemStats.clear(handle);

    assertThat(maxHealth.modifiers).isEmpty();
    assertThat(speed.modifiers).containsExactly(foreign);
    assertThat(health.get()).isEqualTo(4D);
  }

  @Test
  void registeringReplacesASourceWithTheSameIdAndUnregisteringNeedsTheSameSource() {
    GolemStatSource first = source("frost_frame", GolemStatDelta.add(GolemStat.MAX_HEALTH, 4D));
    GolemStatSource second = source("frost_frame", GolemStatDelta.add(GolemStat.MAX_HEALTH, 8D));
    register(first);
    register(second);

    GolemStats.unregister(first);

    assertThat(GolemStats.sources()).containsExactly(second);
    assertThatThrownBy(() -> GolemStats.register(source("Bad Id"))).isInstanceOf(IllegalArgumentException.class);
  }

  private void register(GolemStatSource source) {
    registered.add(source);
    GolemStats.register(source);
  }

  private static GolemStatSource source(String id, GolemStatDelta... deltas) {
    List<GolemStatDelta> list = List.of(deltas);
    return new GolemStatSource() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle golemHandle) {
        return list;
      }
    };
  }
}
