package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.golem.BoundGolems;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.minion.MinionIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class TragoulSkeletalServantAlliesTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID RIVAL = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
  private static final NamespacedKey SERVANT_KEY = NamespacedKey.fromString("adapt:tragoul_servant_owner");

  @BeforeAll
  static void registerServants() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
  }

  @BeforeEach
  void registerGolems() {
    BoundGolems.register();
  }

  @AfterEach
  void unregisterGolems() {
    BoundGolems.unregister();
  }

  @Test
  void servantsNeverTargetTheirOwnersGolems() {
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), golem(OWNER), false)).isTrue();
  }

  @Test
  void servantsStillTargetOtherOwnersGolemsAndMobs() {
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), golem(RIVAL), false)).isFalse();
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), entity(Zombie.class, Map.of()), false)).isFalse();
  }

  @Test
  void trackedServantsAreAlwaysAllies() {
    assertThat(TragoulSkeletalServant.isAlliedTarget(servant(OWNER), servant(RIVAL), true)).isTrue();
  }

  private static Skeleton servant(UUID owner) {
    return entity(Skeleton.class, Map.of(SERVANT_KEY, owner.toString()));
  }

  private static IronGolem golem(UUID owner) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), owner, GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0)));
    return entity(IronGolem.class, Map.of(GolemState.KEY, state.toJson()));
  }

  private static <T extends LivingEntity> T entity(Class<T> type, Map<NamespacedKey, String> values) {
    T entity = mock(type);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.has(any(NamespacedKey.class))).thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(entity.getPersistentDataContainer()).thenReturn(data);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    return entity;
  }
}
