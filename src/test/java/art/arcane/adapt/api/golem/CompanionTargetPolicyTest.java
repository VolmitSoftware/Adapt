package art.arcane.adapt.api.golem;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
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

  private final World world = mock(World.class);
  private final List<Entity> nearby = new ArrayList<>();
  private final List<BoundingBox> queries = new ArrayList<>();
  private final CompanionTargetPolicy policy = new CompanionTargetPolicy();
  private final Player owner = mock(Player.class);
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemProtection> protection;
  private GolemRunes.Augments augments;
  private IronGolem body;
  private GolemHandle golem;

  @BeforeEach
  void setUp() {
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(GolemBrainSettings.defaults());
    protection = mockStatic(GolemProtection.class);
    protection.when(() -> GolemProtection.canDamage(any(Player.class), any(LivingEntity.class))).thenReturn(true);
    lenient().when(world.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      BoundingBox box = invocation.getArgument(0);
      Predicate<Entity> filter = invocation.getArgument(1);
      queries.add(box);
      return nearby.stream().filter(filter).filter(entity -> box.contains(entity.getLocation().toVector())).toList();
    });
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    body = mob(IronGolem.class, 0D, null);
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
    Zombie near = mob(Zombie.class, 11D, owner);
    Zombie far = mob(Zombie.class, 15D, owner);

    assertThat(policy.select(golem, owner)).isSameAs(near);
    assertThat(policy.holds(golem, owner, far)).isFalse();
    assertThat(queries.getFirst().getWidthX()).isEqualTo(CompanionTargetPolicy.RANGE * 2D);
  }

  @Test
  void aTideRuneWidensTheCompanionRange() {
    tide(8D);
    Zombie far = mob(Zombie.class, 15D, owner);
    Zombie beyond = mob(Zombie.class, 21D, owner);

    assertThat(policy.select(golem, owner)).isSameAs(far);
    assertThat(policy.holds(golem, owner, far)).isTrue();
    assertThat(policy.holds(golem, owner, beyond)).isFalse();
    assertThat(queries.getFirst().getWidthX()).isEqualTo(40D);
  }

  @Test
  void theCompanionRangeNeverReachesPastTheLeash() {
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 16D, 8, 10));
    tide(8D);
    Zombie inside = mob(Zombie.class, 15D, owner);
    Zombie outside = mob(Zombie.class, 17D, owner);

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

  private <T extends Mob> T mob(Class<T> type, double x, LivingEntity target) {
    T mob = mock(type);
    lenient().when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(mob.getLocation()).thenAnswer(invocation -> new Location(world, x, 64D, 0D));
    lenient().when(mob.getWorld()).thenReturn(world);
    lenient().when(mob.isValid()).thenReturn(true);
    lenient().when(mob.getTarget()).thenReturn(target);
    nearby.add(mob);
    return mob;
  }
}
