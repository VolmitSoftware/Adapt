package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.testkit.MemoryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class SkillGolemancyRepairXpTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000e7a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000e7a2");

  private final SkillGolemancy.Config config = new SkillGolemancy.Config();
  private final List<Grant> grants = new ArrayList<>();
  private final List<Loc> effects = new ArrayList<>();
  private final PlayerRef healer = mock(PlayerRef.class);
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
        grants.add(new Grant((PlayerRef) arguments[0], (Loc) arguments[1], (Double) arguments[2], (String) arguments[3]));
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
    MobRef golem = bound(MobRef.class, GolemBody.COPPER, 0D);

    skill.payRepair(healer, golem, 4.8D);

    assertThat(grants).isEmpty();
    assertThat(effects).isEmpty();
    assertThat(pool(golem)).isZero();
  }

  @Test
  void aHealLargerThanThePoolPaysOnlyThePoolAndEmptiesIt() {
    MobRef golem = bound(MobRef.class, GolemBody.COPPER, 3D);

    skill.payRepair(healer, golem, 4.8D);

    assertThat(grants).singleElement().satisfies(grant -> {
      assertThat(grant.player()).isSameAs(healer);
      assertThat(grant.xp()).isCloseTo(3D * config.repairXpPerHealth, offset(1e-9));
      assertThat(grant.rewardKey()).isEqualTo("golemancy:repair");
      assertThat(grant.at()).isEqualTo(golem.location());
    });
    assertThat(effects).hasSize(1);
    assertThat(pool(golem)).isZero();
  }

  @Test
  void aHealSmallerThanThePoolPaysTheHealAndLeavesTheRest() {
    MobRef golem = bound(MobRef.class, GolemBody.FROST, 10D);

    skill.payRepair(healer, golem, 2D);

    assertThat(grants).singleElement().satisfies(grant -> assertThat(grant.xp()).isCloseTo(2D * config.repairXpPerHealth, offset(1e-9)));
    assertThat(pool(golem)).isCloseTo(8D, offset(1e-9));
  }

  private <T extends LivingRef> T bound(Class<T> type, GolemBody body, double pool) {
    T golem = mock(type);
    MemoryData data = new MemoryData();
    lenient().when(golem.data()).thenReturn(data);
    lenient().when(golem.uuid()).thenReturn(UUID.randomUUID());
    WorldRef world = mock(WorldRef.class);
    lenient().when(golem.location()).thenAnswer(invocation -> new Loc(world, 2.5D, 64D, -1.5D));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK, GolemAnchor.of(WORLD, 0, 64, 0)));
    JsonObject stored = new Gson().fromJson(state.toJson(), JsonObject.class);
    stored.addProperty("hostileDamagePool", pool);
    data.set(GolemState.KEY, stored.toString());
    return golem;
  }

  private static double pool(LivingRef golem) {
    return GolemState.read(golem).orElseThrow().hostileDamagePool();
  }

  private record Grant(PlayerRef player, Loc at, double xp, String rewardKey) {
  }
}
