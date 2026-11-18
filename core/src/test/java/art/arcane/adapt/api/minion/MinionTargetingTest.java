package art.arcane.adapt.api.minion;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.MemoryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MinionTargetingTest extends AdaptTestBase {
  private static final DataKey<String> SERVANT_KEY = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
  private static final DataKey<String> DECOY_KEY = DataKey.adapt("shadow_decoy_owner", DataCodecs.STRING);

  private final UUID ownerId = UUID.randomUUID();
  private final UUID worldId = UUID.randomUUID();
  private final WorldRef world = world(worldId);
  private final Set<UUID> shielded = ConcurrentHashMap.newKeySet();

  @BeforeEach
  void registerMinionKinds() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
    new StealthShadowDecoy();
    AdaptPorts.install(EntityGuardPort.class, new EntityGuardPort() {
      @Override
      public boolean isShielded(EntityRef entity) {
        return entity != null && (shielded.contains(entity.uuid()) || MinionIdentity.isMinion(entity));
      }
    });
  }

  @AfterEach
  void removeGuardPort() {
    AdaptPorts.uninstall(EntityGuardPort.class);
  }

  @Test
  void invalidOrDeadCandidatesAreNotCaptured() {
    MobRef invalid = candidate(MobRef.class);
    when(invalid.isValid()).thenReturn(false);
    MobRef dead = candidate(MobRef.class);
    when(dead.isDead()).thenReturn(true);

    assertThat(MinionTargeting.captureOwned(ownerId, invalid)).isNull();
    assertThat(MinionTargeting.captureOwned(ownerId, dead)).isNull();
  }

  @Test
  void captureRecordsIdentityLocationAndPlayerFlag() {
    MobRef zombie = candidate(MobRef.class);
    PlayerRef stranger = candidate(PlayerRef.class);

    MinionTargeting.Candidate mob = MinionTargeting.captureOwned(ownerId, zombie);
    MinionTargeting.Candidate player = MinionTargeting.captureOwned(ownerId, stranger);

    assertThat(mob.entity()).isSameAs(zombie);
    assertThat(mob.entityId()).isEqualTo(zombie.uuid());
    assertThat(mob.worldId()).isEqualTo(worldId);
    assertThat(mob.location().world()).isSameAs(world);
    assertThat(mob.player()).isFalse();
    assertThat(mob.protectedFriendly()).isFalse();
    assertThat(player.player()).isTrue();
    assertThat(player.protectedFriendly()).isFalse();
  }

  @Test
  void ownerMarkersInvulnerablesAndNpcsAreProtected() {
    PlayerRef owner = candidate(PlayerRef.class);
    when(owner.uuid()).thenReturn(ownerId);
    LivingRef marker = armorStand();
    shielded.add(marker.uuid());
    LivingRef plainStand = armorStand();
    MobRef invulnerable = candidate(MobRef.class);
    when(invulnerable.isInvulnerable()).thenReturn(true);
    PlayerRef npc = candidate(PlayerRef.class);
    shielded.add(npc.uuid());

    assertThat(protectedFriendly(owner)).isTrue();
    assertThat(protectedFriendly(marker)).isTrue();
    assertThat(protectedFriendly(plainStand)).isFalse();
    assertThat(protectedFriendly(invulnerable)).isTrue();
    assertThat(protectedFriendly(npc)).isTrue();
  }

  @Test
  void everyMinionIsProtectedWhateverItsOwner() {
    MobRef ownServant = tag(candidate(MobRef.class), SERVANT_KEY, ownerId);
    MobRef foreignServant = tag(candidate(MobRef.class), SERVANT_KEY, UUID.randomUUID());
    LivingRef ownDecoy = tag(armorStand(), DECOY_KEY, ownerId);
    MobRef wildSkeleton = candidate(MobRef.class);

    assertThat(protectedFriendly(ownServant)).isTrue();
    assertThat(protectedFriendly(foreignServant)).isTrue();
    assertThat(protectedFriendly(ownDecoy)).isTrue();
    assertThat(protectedFriendly(wildSkeleton)).isFalse();
  }

  @Test
  void onlyTheOwnersTamedPetsAreProtected() {
    MobRef ownPet = tamed(ownerId);
    MobRef foreignPet = tamed(UUID.randomUUID());
    MobRef strayWithoutOwner = candidate(MobRef.class);
    when(strayWithoutOwner.tameOwner()).thenReturn(null);
    MobRef wild = candidate(MobRef.class);

    assertThat(protectedFriendly(ownPet)).isTrue();
    assertThat(protectedFriendly(foreignPet)).isFalse();
    assertThat(protectedFriendly(strayWithoutOwner)).isFalse();
    assertThat(protectedFriendly(wild)).isFalse();
  }

  @Test
  void protectedCandidatesAndTheOwnerAreNeverDamageable() {
    Adaptation<?> policy = permissivePolicy();
    PlayerRef owner = owner();
    MinionTargeting.Candidate friendly = new MinionTargeting.Candidate(candidate(MobRef.class), UUID.randomUUID(), worldId,
        new Loc(world, 0, 64, 0), false, true);
    MinionTargeting.Candidate self = new MinionTargeting.Candidate(owner, ownerId, worldId,
        new Loc(world, 0, 64, 0), true, false);

    assertThat(MinionTargeting.canDamageOwned(policy, owner, friendly)).isFalse();
    assertThat(MinionTargeting.canDamageOwned(policy, owner, self)).isFalse();
    verify(policy, never()).canPVP(any(PlayerRef.class), any(Loc.class));
    verify(policy, never()).canPVE(any(PlayerRef.class), any(EntityRef.class));
  }

  @Test
  void playersUseThePvpPolicyAndMobsUseThePvePolicy() {
    Adaptation<?> policy = mock(Adaptation.class);
    PlayerRef owner = owner();
    Loc playerLocation = new Loc(world, 1, 64, 1);
    Loc mobLocation = new Loc(world, 2, 64, 2);
    MinionTargeting.Candidate player = new MinionTargeting.Candidate(candidate(PlayerRef.class), UUID.randomUUID(), worldId,
        playerLocation, true, false);
    MinionTargeting.Candidate mob = new MinionTargeting.Candidate(candidate(MobRef.class), UUID.randomUUID(), worldId,
        mobLocation, false, false);

    when(policy.canPVP(owner, playerLocation)).thenReturn(true);
    when(policy.canPVE(owner, (EntityRef) mob.entity())).thenReturn(false);
    assertThat(MinionTargeting.canDamageOwned(policy, owner, player)).isTrue();
    assertThat(MinionTargeting.canDamageOwned(policy, owner, mob)).isFalse();

    when(policy.canPVP(owner, playerLocation)).thenReturn(false);
    when(policy.canPVE(owner, (EntityRef) mob.entity())).thenReturn(true);
    assertThat(MinionTargeting.canDamageOwned(policy, owner, player)).isFalse();
    assertThat(MinionTargeting.canDamageOwned(policy, owner, mob)).isTrue();
  }

  @Test
  void aJudgedCaptureDecidesTheMobVerdictWhileCapturingAndTheJudgeReadsNoLiveEntity() {
    Adaptation<?> policy = mock(Adaptation.class);
    PlayerRef owner = owner();
    MobRef zombie = candidate(MobRef.class);
    when(policy.canPVE(owner, (EntityRef) zombie)).thenReturn(true);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, zombie);
    clearInvocations(zombie, policy);
    when(policy.canPVE(owner, (EntityRef) zombie)).thenReturn(false);

    assertThat(judged.pveAllowed()).isTrue();
    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isTrue();
    verifyNoInteractions(zombie);
    verify(policy, never()).canPVE(any(PlayerRef.class), any(EntityRef.class));
    verify(policy, never()).canPVE(any(PlayerRef.class), any(Loc.class));
  }

  @Test
  void aJudgedCaptureThatDeniesTheMobKeepsDenyingIt() {
    Adaptation<?> policy = mock(Adaptation.class);
    PlayerRef owner = owner();
    MobRef zombie = candidate(MobRef.class);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, zombie);
    when(policy.canPVE(owner, (EntityRef) zombie)).thenReturn(true);

    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isFalse();
  }

  @Test
  void aJudgedPlayerUsesThePvpPolicyAtItsCapturedLocation() {
    Adaptation<?> policy = mock(Adaptation.class);
    PlayerRef owner = owner();
    PlayerRef stranger = candidate(PlayerRef.class);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, stranger);
    clearInvocations(stranger);
    when(policy.canPVP(owner, judged.candidate().location())).thenReturn(true);

    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isTrue();
    verifyNoInteractions(stranger);
    verify(policy, never()).canPVE(any(PlayerRef.class), any(EntityRef.class));
  }

  @Test
  void friendlyJudgedCapturesNeverAskTheProtectorsAndInvalidOnesAreNotCaptured() {
    Adaptation<?> policy = permissivePolicy();
    PlayerRef owner = owner();
    MobRef pet = tamed(ownerId);
    MobRef invalid = candidate(MobRef.class);
    when(invalid.isValid()).thenReturn(false);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, pet);

    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isFalse();
    assertThat(MinionTargeting.captureJudgedOwned(policy, owner, invalid)).isNull();
    verify(policy, never()).canPVE(any(PlayerRef.class), any(EntityRef.class));
    verify(policy, never()).canPVP(any(PlayerRef.class), any(Loc.class));
  }

  private boolean protectedFriendly(LivingRef candidate) {
    return MinionTargeting.captureOwned(ownerId, candidate).protectedFriendly();
  }

  private PlayerRef owner() {
    PlayerRef owner = mock(PlayerRef.class);
    when(owner.uuid()).thenReturn(ownerId);
    return owner;
  }

  private MobRef tamed(UUID tamerId) {
    MobRef wolf = candidate(MobRef.class);
    when(wolf.tameOwner()).thenReturn(tamerId);
    return wolf;
  }

  private LivingRef armorStand() {
    LivingRef stand = candidate(LivingRef.class);
    lenient().when(stand.has(EntityTrait.ARMOR_STAND)).thenReturn(true);
    return stand;
  }

  private <T extends LivingRef> T candidate(Class<T> type) {
    T entity = mock(type);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.location()).thenReturn(new Loc(world, 0, 64, 0));
    lenient().when(entity.data()).thenReturn(new MemoryData());
    return entity;
  }

  private static <T extends LivingRef> T tag(T entity, DataKey<String> key, UUID owner) {
    entity.data().set(key, owner.toString());
    return entity;
  }

  private static Adaptation<?> permissivePolicy() {
    Adaptation<?> policy = mock(Adaptation.class);
    when(policy.canPVP(any(PlayerRef.class), any(Loc.class))).thenReturn(true);
    when(policy.canPVE(any(PlayerRef.class), any(EntityRef.class))).thenReturn(true);
    return policy;
  }

  private static WorldRef world(UUID id) {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(id);
    return world;
  }
}
