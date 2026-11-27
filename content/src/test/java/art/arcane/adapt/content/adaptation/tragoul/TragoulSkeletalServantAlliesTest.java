package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.engine.minion.MinionIdentity;
import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.MemoryData;
import art.arcane.adapt.testkit.MockedEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class TragoulSkeletalServantAlliesTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID RIVAL = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
  private static final DataKey<String> SERVANT_KEY = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
  private static final DataKey<String> GOLEM_KEY = DataKey.adapt("golem", DataCodecs.STRING);
  private static final MinionKind GOLEMS = new MinionKind() {
    @Override
    public boolean matches(EntityRef entity) {
      return entity.data().has(GOLEM_KEY);
    }

    @Override
    public UUID ownerOf(EntityRef entity) {
      String owner = entity.data().get(GOLEM_KEY);
      return owner == null ? null : UUID.fromString(owner);
    }
  };

  @BeforeAll
  static void registerServants() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
  }

  private MockedEngine engine;

  @BeforeEach
  void registerGolems() {
    engine = new MockedEngine(TestCores.install());
    MinionIdentity.registerKind(GOLEMS);
  }

  @AfterEach
  void unregisterGolems() {
    MinionIdentity.unregisterKind(GOLEMS);
    engine.close();
    TestCores.clear();
  }

  @Test
  void servantsNeverTargetTheirOwnersGolems() {
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), golem(OWNER), false)).isTrue();
  }

  @Test
  void servantsStillTargetOtherOwnersGolemsAndMobs() {
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), golem(RIVAL), false)).isFalse();
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), entity(MobRef.class, Id.minecraft("zombie"), Map.of()), false)).isFalse();
  }

  @Test
  void trackedServantsAreAlwaysAllies() {
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), servant(RIVAL), true)).isTrue();
  }

  private static MobRef servant(UUID owner) {
    return entity(MobRef.class, Id.minecraft("skeleton"), Map.of(SERVANT_KEY, owner.toString()));
  }

  private static LivingRef golem(UUID owner) {
    return entity(MobRef.class, Id.minecraft("iron_golem"), Map.of(GOLEM_KEY, owner.toString()));
  }

  private static <T extends LivingRef> T entity(Class<T> type, Id id, Map<DataKey<String>, String> values) {
    T entity = mock(type);
    MemoryData data = new MemoryData();
    values.forEach(data::set);
    lenient().when(entity.data()).thenReturn(data);
    lenient().when(entity.type()).thenReturn(id);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    return entity;
  }
}
