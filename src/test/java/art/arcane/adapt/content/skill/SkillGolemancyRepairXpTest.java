package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class SkillGolemancyRepairXpTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000e7a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000e7a2");

  private final SkillGolemancy.Config config = new SkillGolemancy.Config();
  private final List<Grant> grants = new ArrayList<>();
  private final List<Location> effects = new ArrayList<>();
  private final Player healer = mock(Player.class);
  private SkillGolemancy skill;

  @BeforeEach
  void setUp() {
    skill = mock(SkillGolemancy.class, invocation -> switch (invocation.getMethod().getName()) {
      case "getConfig" -> config;
      case "shouldReturnForPlayer" -> {
        ((Runnable) invocation.getArgument(invocation.getArguments().length - 1)).run();
        yield null;
      }
      case "xp" -> {
        Object[] arguments = invocation.getArguments();
        grants.add(new Grant((Player) arguments[0], (Location) arguments[1], (Double) arguments[2], (String) arguments[3]));
        yield null;
      }
      case "repairFx" -> {
        effects.add(invocation.getArgument(0));
        yield null;
      }
      default -> invocation.callRealMethod();
    });
  }

  @Test
  void aHealOnAGolemWithAnEmptyHostilePoolPaysNothing() {
    CopperGolem golem = bound(CopperGolem.class, GolemBody.COPPER, 0D);

    skill.payRepair(healer, golem, 4.8D);

    assertThat(grants).isEmpty();
    assertThat(effects).isEmpty();
    assertThat(pool(golem)).isZero();
  }

  @Test
  void aHealLargerThanThePoolPaysOnlyThePoolAndEmptiesIt() {
    CopperGolem golem = bound(CopperGolem.class, GolemBody.COPPER, 3D);

    skill.payRepair(healer, golem, 4.8D);

    assertThat(grants).singleElement().satisfies(grant -> {
      assertThat(grant.player()).isSameAs(healer);
      assertThat(grant.xp()).isCloseTo(3D * config.repairXpPerHealth, offset(1e-9));
      assertThat(grant.rewardKey()).isEqualTo("golemancy:repair");
      assertThat(grant.at()).isEqualTo(golem.getLocation());
    });
    assertThat(effects).hasSize(1);
    assertThat(pool(golem)).isZero();
  }

  @Test
  void aHealSmallerThanThePoolPaysTheHealAndLeavesTheRest() {
    Snowman golem = bound(Snowman.class, GolemBody.FROST, 10D);

    skill.payRepair(healer, golem, 2D);

    assertThat(grants).singleElement().satisfies(grant -> assertThat(grant.xp()).isCloseTo(2D * config.repairXpPerHealth, offset(1e-9)));
    assertThat(pool(golem)).isCloseTo(8D, offset(1e-9));
  }

  private <T extends LivingEntity> T bound(Class<T> type, GolemBody body, double pool) {
    T golem = mock(type);
    Map<NamespacedKey, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().doAnswer(invocation -> {
      values.put(invocation.getArgument(0), invocation.getArgument(2));
      return null;
    }).when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    lenient().when(golem.getPersistentDataContainer()).thenReturn(data);
    lenient().when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    World world = mock(World.class);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> new Location(world, 2.5D, 64D, -1.5D));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 64, 0)));
    JsonObject stored = new Gson().fromJson(state.toJson(), JsonObject.class);
    stored.addProperty("hostileDamagePool", pool);
    values.put(GolemState.KEY, stored.toString());
    return golem;
  }

  private static double pool(LivingEntity golem) {
    return GolemState.read(golem).orElseThrow().hostileDamagePool();
  }

  private record Grant(Player player, Location at, double xp, String rewardKey) {
  }
}
