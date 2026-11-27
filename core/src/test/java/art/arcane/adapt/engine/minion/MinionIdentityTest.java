package art.arcane.adapt.engine.minion;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.testkit.MemoryData;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class MinionIdentityTest extends AdaptTestBase {
  private static final DataKey<String> SERVANT_KEY = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
  private static final DataKey<String> DECOY_KEY = DataKey.adapt("shadow_decoy_owner", DataCodecs.STRING);

  @Test
  void registeredKindIdentifiesItsEntitiesAndTheirOwner() {
    UUID ownerId = UUID.randomUUID();
    EntityRef minion = plain();
    EntityRef stranger = plain();
    FixedKind kind = new FixedKind();
    kind.add(minion, ownerId);

    assertThat(MinionIdentity.isMinion(minion)).isFalse();

    MinionIdentity.registerKind(kind);

    assertThat(MinionIdentity.isMinion(minion)).isTrue();
    assertThat(MinionIdentity.ownerOf(minion)).isEqualTo(ownerId);
    assertThat(MinionIdentity.isMinion(stranger)).isFalse();
    assertThat(MinionIdentity.ownerOf(stranger)).isNull();
  }

  @Test
  void minionWithUnknownOwnerIsStillAMinion() {
    EntityRef orphan = plain();
    FixedKind kind = new FixedKind();
    kind.add(orphan, null);
    MinionIdentity.registerKind(kind);

    assertThat(MinionIdentity.isMinion(orphan)).isTrue();
    assertThat(MinionIdentity.ownerOf(orphan)).isNull();
  }

  @Test
  void nullEntityIsNeverAMinion() {
    assertThat(MinionIdentity.isMinion(null)).isFalse();
    assertThat(MinionIdentity.ownerOf(null)).isNull();
    assertThat(MinionIdentity.sameOwner(null, null)).isFalse();
  }

  @Test
  void sameOwnerRequiresTwoMinionsWithTheSameKnownOwner() {
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    EntityRef aliceServant = plain();
    EntityRef aliceGolem = plain();
    EntityRef bobServant = plain();
    EntityRef firstOrphan = plain();
    EntityRef secondOrphan = plain();
    EntityRef bystander = plain();
    FixedKind servants = new FixedKind();
    servants.add(aliceServant, alice);
    servants.add(bobServant, bob);
    servants.add(firstOrphan, null);
    FixedKind golems = new FixedKind();
    golems.add(aliceGolem, alice);
    golems.add(secondOrphan, null);
    MinionIdentity.registerKind(servants);
    MinionIdentity.registerKind(golems);

    assertThat(MinionIdentity.sameOwner(aliceServant, aliceGolem)).isTrue();
    assertThat(MinionIdentity.sameOwner(aliceGolem, aliceServant)).isTrue();
    assertThat(MinionIdentity.sameOwner(aliceServant, bobServant)).isFalse();
    assertThat(MinionIdentity.sameOwner(firstOrphan, secondOrphan)).isFalse();
    assertThat(MinionIdentity.sameOwner(aliceServant, bystander)).isFalse();
    assertThat(MinionIdentity.sameOwner(bystander, bystander)).isFalse();
    assertThat(MinionIdentity.sameOwner(aliceServant, null)).isFalse();
  }

  @Test
  void registeringTheSameKindAgainDoesNotDuplicateIt() {
    CountingKind kind = new CountingKind();
    MinionIdentity.registerKind(kind);
    MinionIdentity.registerKind(kind);

    MinionIdentity.isMinion(plain());

    assertThat(kind.checks()).isEqualTo(1);
  }

  @Test
  void servantAndDecoyKindsResolveOwnersFromTheirOwnerKeys() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
    TestContent.adaptation(Id.adapt("stealth"), StealthShadowDecoy::new);
    UUID ownerId = UUID.randomUUID();
    EntityRef servant = tagged(MobRef.class, false, SERVANT_KEY, ownerId.toString());
    EntityRef decoy = tagged(EntityRef.class, true, DECOY_KEY, ownerId.toString());
    EntityRef decoyKeyOnNonStand = tagged(MobRef.class, false, DECOY_KEY, ownerId.toString());
    EntityRef corruptServant = tagged(MobRef.class, false, SERVANT_KEY, "not-a-uuid");
    EntityRef untagged = tagged(MobRef.class, false, null, null);

    assertThat(MinionIdentity.isMinion(servant)).isTrue();
    assertThat(MinionIdentity.ownerOf(servant)).isEqualTo(ownerId);
    assertThat(MinionIdentity.isMinion(decoy)).isTrue();
    assertThat(MinionIdentity.ownerOf(decoy)).isEqualTo(ownerId);
    assertThat(MinionIdentity.sameOwner(servant, decoy)).isTrue();
    assertThat(MinionIdentity.isMinion(decoyKeyOnNonStand)).isFalse();
    assertThat(MinionIdentity.isMinion(corruptServant)).isTrue();
    assertThat(MinionIdentity.ownerOf(corruptServant)).isNull();
    assertThat(MinionIdentity.isMinion(untagged)).isFalse();
  }

  private static EntityRef plain() {
    return tagged(EntityRef.class, false, null, null);
  }

  private static <T extends EntityRef> T tagged(Class<T> type, boolean armorStand, DataKey<String> key, String owner) {
    T entity = mock(type);
    MemoryData data = new MemoryData();
    lenient().when(entity.data()).thenReturn(data);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.has(EntityTrait.ARMOR_STAND)).thenReturn(armorStand);
    if (key != null) {
      data.set(key, owner);
    }
    return entity;
  }

  private static final class FixedKind implements MinionKind {
    private final Map<EntityRef, UUID> owners = new IdentityHashMap<>();

    private void add(EntityRef entity, UUID ownerId) {
      owners.put(entity, ownerId);
    }

    @Override
    public boolean matches(EntityRef entity) {
      return owners.containsKey(entity);
    }

    @Override
    public UUID ownerOf(EntityRef entity) {
      return owners.get(entity);
    }
  }

  private static final class CountingKind implements MinionKind {
    private final AtomicInteger checks = new AtomicInteger();

    private int checks() {
      return checks.get();
    }

    @Override
    public boolean matches(EntityRef entity) {
      checks.incrementAndGet();
      return false;
    }

    @Override
    public UUID ownerOf(EntityRef entity) {
      return null;
    }
  }
}
