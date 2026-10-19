package art.arcane.adapt.api.minion;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinionIdentityTest extends AdaptTestBase {
  private static final NamespacedKey SERVANT_KEY = NamespacedKey.fromString("adapt:tragoul_servant_owner");
  private static final NamespacedKey DECOY_KEY = NamespacedKey.fromString("adapt:shadow_decoy_owner");

  @Test
  void registeredKindIdentifiesItsEntitiesAndTheirOwner() {
    UUID ownerId = UUID.randomUUID();
    Entity minion = plain();
    Entity stranger = plain();
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
    Entity orphan = plain();
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
    Entity aliceServant = plain();
    Entity aliceGolem = plain();
    Entity bobServant = plain();
    Entity firstOrphan = plain();
    Entity secondOrphan = plain();
    Entity bystander = plain();
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
    new StealthShadowDecoy();
    UUID ownerId = UUID.randomUUID();
    Skeleton servant = tagged(Skeleton.class, SERVANT_KEY, ownerId.toString());
    ArmorStand decoy = tagged(ArmorStand.class, DECOY_KEY, ownerId.toString());
    Zombie decoyKeyOnNonStand = tagged(Zombie.class, DECOY_KEY, ownerId.toString());
    Skeleton corruptServant = tagged(Skeleton.class, SERVANT_KEY, "not-a-uuid");
    Zombie untagged = tagged(Zombie.class, null, null);

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

  private static Entity plain() {
    return tagged(Entity.class, null, null);
  }

  private static <T extends Entity> T tagged(Class<T> type, NamespacedKey key, String owner) {
    T entity = mock(type);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(entity.getPersistentDataContainer()).thenReturn(data);
    if (key != null) {
      when(data.has(key, PersistentDataType.STRING)).thenReturn(true);
      when(data.get(key, PersistentDataType.STRING)).thenReturn(owner);
    }
    return entity;
  }

  private static final class FixedKind implements MinionKind {
    private final Map<Entity, UUID> owners = new IdentityHashMap<>();

    private void add(Entity entity, UUID ownerId) {
      owners.put(entity, ownerId);
    }

    @Override
    public boolean matches(Entity entity) {
      return owners.containsKey(entity);
    }

    @Override
    public UUID ownerOf(Entity entity) {
      return owners.get(entity);
    }
  }

  private static final class CountingKind implements MinionKind {
    private final AtomicInteger checks = new AtomicInteger();

    private int checks() {
      return checks.get();
    }

    @Override
    public boolean matches(Entity entity) {
      checks.incrementAndGet();
      return false;
    }

    @Override
    public UUID ownerOf(Entity entity) {
      return null;
    }
  }
}
