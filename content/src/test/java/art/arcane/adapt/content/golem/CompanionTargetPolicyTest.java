package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class CompanionTargetPolicyTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000501");

  private final WorldRef world = mock(WorldRef.class);
  private final List<EntityRef> nearby = new ArrayList<>();
  private final List<Box> queries = new ArrayList<>();
  private final CompanionTargetPolicy policy = new CompanionTargetPolicy();
  private final PlayerRef owner = mock(PlayerRef.class);
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemProtection> protection;
  private GolemRunes.Augments augments;
  private MobRef body;
  private GolemHandle golem;

  @BeforeEach
  void setUp() {
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(GolemBrainSettings.defaults());
    protection = mockStatic(GolemProtection.class);
    protection.when(() -> GolemProtection.canDamage(any(PlayerRef.class), any(LivingRef.class))).thenReturn(true);
    lenient().when(world.entitiesIn(any(Box.class), any())).thenAnswer(invocation -> {
      Box box = invocation.getArgument(0);
      Predicate<EntityRef> filter = invocation.getArgument(1);
      queries.add(box);
      return nearby.stream().filter(filter).filter(entity -> box.contains(entity.location().position())).toList();
    });
    lenient().when(owner.uuid()).thenReturn(OWNER);
    body = mob(MobRef.class, 0D, null);
    golem = mock(GolemHandle.class);
    lenient().when(golem.entity()).thenReturn(body);
    lenient().when(golem.owner()).thenReturn(OWNER);
  }

  @AfterEach
  void tearDown() {
    if (augments != null) {
      GolemRunes.unregister(augments);
    }
    protection.close();
    runtime.close();
  }

  @Test
  void companionsAttackOnlyMobsThreateningTheOwnerWithinTwelveBlocks() {
    MobRef near = mob(MobRef.class, 11D, owner);
    MobRef far = mob(MobRef.class, 15D, owner);

    assertThat(policy.select(golem, owner)).isSameAs(near);
    assertThat(policy.holds(golem, owner, far)).isFalse();
    assertThat(queries.getFirst().widthX()).isEqualTo(CompanionTargetPolicy.RANGE * 2D);
  }

  @Test
  void aTideRuneWidensTheCompanionRange() {
    tide(8D);
    MobRef far = mob(MobRef.class, 15D, owner);
    MobRef beyond = mob(MobRef.class, 21D, owner);

    assertThat(policy.select(golem, owner)).isSameAs(far);
    assertThat(policy.holds(golem, owner, far)).isTrue();
    assertThat(policy.holds(golem, owner, beyond)).isFalse();
    assertThat(queries.getFirst().widthX()).isEqualTo(40D);
  }

  @Test
  void theCompanionRangeNeverReachesPastTheLeash() {
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 16D, 8, 10));
    tide(8D);
    MobRef inside = mob(MobRef.class, 15D, owner);
    MobRef outside = mob(MobRef.class, 17D, owner);

    assertThat(policy.holds(golem, owner, inside)).isTrue();
    assertThat(policy.holds(golem, owner, outside)).isFalse();
    assertThat(CompanionTargetPolicy.range(8D, 16D)).isEqualTo(16D);
    assertThat(CompanionTargetPolicy.range(0D, 32D)).isEqualTo(CompanionTargetPolicy.RANGE);
  }

  private void tide(double radius) {
    augments = new GolemRunes.Augments() {
      @Override
      public List<String> describe(GolemRune rune, GolemMastery.Perks perks) {
        return List.of();
      }

      @Override
      public GolemRuneLayout layout(GolemBody body, UUID owner, boolean masterwork) {
        return GolemRuneLayout.NONE;
      }

      @Override
      public GolemRuneEffects effects(GolemHandle handle) {
        return new GolemRuneEffects(radius, 0, 1D, 0D);
      }
    };
    GolemRunes.register(augments);
  }

  private <T extends MobRef> T mob(Class<T> type, double x, LivingRef target) {
    T mob = mock(type);
    lenient().when(mob.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(mob.location()).thenAnswer(invocation -> new Loc(world, x, 64D, 0D));
    lenient().when(mob.world()).thenReturn(world);
    lenient().when(mob.isValid()).thenReturn(true);
    lenient().when(mob.target()).thenReturn(target);
    nearby.add(mob);
    return mob;
  }
}
