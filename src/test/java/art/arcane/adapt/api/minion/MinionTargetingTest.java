package art.arcane.adapt.api.minion;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MinionTargetingTest extends AdaptTestBase {
  private static final NamespacedKey SERVANT_KEY = NamespacedKey.fromString("adapt:tragoul_servant_owner");
  private static final NamespacedKey DECOY_KEY = NamespacedKey.fromString("adapt:shadow_decoy_owner");

  private final UUID ownerId = UUID.randomUUID();
  private final UUID worldId = UUID.randomUUID();
  private final World world = world(worldId);

  @BeforeEach
  void registerMinionKinds() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
    new StealthShadowDecoy();
  }

  @Test
  void invalidOrDeadCandidatesAreNotCaptured() {
    Zombie invalid = candidate(Zombie.class);
    when(invalid.isValid()).thenReturn(false);
    Zombie dead = candidate(Zombie.class);
    when(dead.isDead()).thenReturn(true);

    assertThat(MinionTargeting.captureOwned(ownerId, invalid)).isNull();
    assertThat(MinionTargeting.captureOwned(ownerId, dead)).isNull();
  }

  @Test
  void captureRecordsIdentityLocationAndPlayerFlag() {
    Zombie zombie = candidate(Zombie.class);
    Player stranger = candidate(Player.class);

    MinionTargeting.Candidate mob = MinionTargeting.captureOwned(ownerId, zombie);
    MinionTargeting.Candidate player = MinionTargeting.captureOwned(ownerId, stranger);

    assertThat(mob.entity()).isSameAs(zombie);
    assertThat(mob.entityId()).isEqualTo(zombie.getUniqueId());
    assertThat(mob.worldId()).isEqualTo(worldId);
    assertThat(mob.location().getWorld()).isSameAs(world);
    assertThat(mob.player()).isFalse();
    assertThat(mob.protectedFriendly()).isFalse();
    assertThat(player.player()).isTrue();
    assertThat(player.protectedFriendly()).isFalse();
  }

  @Test
  void ownerMarkersInvulnerablesAndNpcsAreProtected() {
    Player owner = candidate(Player.class);
    when(owner.getUniqueId()).thenReturn(ownerId);
    ArmorStand marker = candidate(ArmorStand.class);
    when(marker.isMarker()).thenReturn(true);
    ArmorStand plainStand = candidate(ArmorStand.class);
    Zombie invulnerable = candidate(Zombie.class);
    when(invulnerable.isInvulnerable()).thenReturn(true);
    Player npc = candidate(Player.class);
    when(npc.hasMetadata("NPC")).thenReturn(true);

    assertThat(protectedFriendly(owner)).isTrue();
    assertThat(protectedFriendly(marker)).isTrue();
    assertThat(protectedFriendly(plainStand)).isFalse();
    assertThat(protectedFriendly(invulnerable)).isTrue();
    assertThat(protectedFriendly(npc)).isTrue();
  }

  @Test
  void everyMinionIsProtectedWhateverItsOwner() {
    Skeleton ownServant = tag(candidate(Skeleton.class), SERVANT_KEY, ownerId);
    Skeleton foreignServant = tag(candidate(Skeleton.class), SERVANT_KEY, UUID.randomUUID());
    ArmorStand ownDecoy = tag(candidate(ArmorStand.class), DECOY_KEY, ownerId);
    Skeleton wildSkeleton = candidate(Skeleton.class);

    assertThat(protectedFriendly(ownServant)).isTrue();
    assertThat(protectedFriendly(foreignServant)).isTrue();
    assertThat(protectedFriendly(ownDecoy)).isTrue();
    assertThat(protectedFriendly(wildSkeleton)).isFalse();
  }

  @Test
  void onlyTheOwnersTamedPetsAreProtected() {
    Wolf ownPet = tamed(ownerId);
    Wolf foreignPet = tamed(UUID.randomUUID());
    Wolf strayWithoutOwner = candidate(Wolf.class);
    when(strayWithoutOwner.isTamed()).thenReturn(true);
    Wolf wild = candidate(Wolf.class);

    assertThat(protectedFriendly(ownPet)).isTrue();
    assertThat(protectedFriendly(foreignPet)).isFalse();
    assertThat(protectedFriendly(strayWithoutOwner)).isFalse();
    assertThat(protectedFriendly(wild)).isFalse();
  }

  @Test
  void protectedCandidatesAndTheOwnerAreNeverDamageable() {
    Adaptation<?> policy = permissivePolicy();
    Player owner = owner();
    MinionTargeting.Candidate friendly = new MinionTargeting.Candidate(candidate(Zombie.class), UUID.randomUUID(), worldId,
        new Location(world, 0, 64, 0), false, true);
    MinionTargeting.Candidate self = new MinionTargeting.Candidate(owner, ownerId, worldId,
        new Location(world, 0, 64, 0), true, false);

    assertThat(MinionTargeting.canDamageOwned(policy, owner, friendly)).isFalse();
    assertThat(MinionTargeting.canDamageOwned(policy, owner, self)).isFalse();
    verify(policy, never()).canPVP(any(Player.class), any(Location.class));
    verify(policy, never()).canPVE(any(Player.class), any(Entity.class));
  }

  @Test
  void playersUseThePvpPolicyAndMobsUseThePvePolicy() {
    Adaptation<?> policy = mock(Adaptation.class);
    Player owner = owner();
    Location playerLocation = new Location(world, 1, 64, 1);
    Location mobLocation = new Location(world, 2, 64, 2);
    MinionTargeting.Candidate player = new MinionTargeting.Candidate(candidate(Player.class), UUID.randomUUID(), worldId,
        playerLocation, true, false);
    MinionTargeting.Candidate mob = new MinionTargeting.Candidate(candidate(Zombie.class), UUID.randomUUID(), worldId,
        mobLocation, false, false);

    when(policy.canPVP(owner, playerLocation)).thenReturn(true);
    when(policy.canPVE(owner, mob.entity())).thenReturn(false);
    assertThat(MinionTargeting.canDamageOwned(policy, owner, player)).isTrue();
    assertThat(MinionTargeting.canDamageOwned(policy, owner, mob)).isFalse();

    when(policy.canPVP(owner, playerLocation)).thenReturn(false);
    when(policy.canPVE(owner, mob.entity())).thenReturn(true);
    assertThat(MinionTargeting.canDamageOwned(policy, owner, player)).isFalse();
    assertThat(MinionTargeting.canDamageOwned(policy, owner, mob)).isTrue();
  }

  @Test
  void aJudgedCaptureDecidesTheMobVerdictWhileCapturingAndTheJudgeReadsNoLiveEntity() {
    Adaptation<?> policy = mock(Adaptation.class);
    Player owner = owner();
    Zombie zombie = candidate(Zombie.class);
    when(policy.canPVE(owner, zombie)).thenReturn(true);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, zombie);
    clearInvocations(zombie, policy);
    when(policy.canPVE(owner, zombie)).thenReturn(false);

    assertThat(judged.pveAllowed()).isTrue();
    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isTrue();
    verifyNoInteractions(zombie);
    verify(policy, never()).canPVE(any(Player.class), any(Entity.class));
    verify(policy, never()).canPVE(any(Player.class), any(Location.class));
  }

  @Test
  void aJudgedCaptureThatDeniesTheMobKeepsDenyingIt() {
    Adaptation<?> policy = mock(Adaptation.class);
    Player owner = owner();
    Zombie zombie = candidate(Zombie.class);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, zombie);
    when(policy.canPVE(owner, zombie)).thenReturn(true);

    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isFalse();
  }

  @Test
  void aJudgedPlayerUsesThePvpPolicyAtItsCapturedLocation() {
    Adaptation<?> policy = mock(Adaptation.class);
    Player owner = owner();
    Player stranger = candidate(Player.class);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, stranger);
    clearInvocations(stranger);
    when(policy.canPVP(owner, judged.candidate().location())).thenReturn(true);

    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isTrue();
    verifyNoInteractions(stranger);
    verify(policy, never()).canPVE(any(Player.class), any(Entity.class));
  }

  @Test
  void friendlyJudgedCapturesNeverAskTheProtectorsAndInvalidOnesAreNotCaptured() {
    Adaptation<?> policy = permissivePolicy();
    Player owner = owner();
    Wolf pet = tamed(ownerId);
    Zombie invalid = candidate(Zombie.class);
    when(invalid.isValid()).thenReturn(false);

    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(policy, owner, pet);

    assertThat(MinionTargeting.canDamageJudgedOwned(policy, owner, judged)).isFalse();
    assertThat(MinionTargeting.captureJudgedOwned(policy, owner, invalid)).isNull();
    verify(policy, never()).canPVE(any(Player.class), any(Entity.class));
    verify(policy, never()).canPVP(any(Player.class), any(Location.class));
  }

  private boolean protectedFriendly(LivingEntity candidate) {
    return MinionTargeting.captureOwned(ownerId, candidate).protectedFriendly();
  }

  private Player owner() {
    Player owner = mock(Player.class);
    when(owner.getUniqueId()).thenReturn(ownerId);
    return owner;
  }

  private Wolf tamed(UUID tamerId) {
    Wolf wolf = candidate(Wolf.class);
    AnimalTamer tamer = mock(AnimalTamer.class);
    when(tamer.getUniqueId()).thenReturn(tamerId);
    when(wolf.isTamed()).thenReturn(true);
    when(wolf.getOwner()).thenReturn(tamer);
    return wolf;
  }

  private <T extends LivingEntity> T candidate(Class<T> type) {
    T entity = mock(type);
    when(entity.isValid()).thenReturn(true);
    when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    return entity;
  }

  private static <T extends LivingEntity> T tag(T entity, NamespacedKey key, UUID owner) {
    PersistentDataContainer data = entity.getPersistentDataContainer();
    when(data.has(key, PersistentDataType.STRING)).thenReturn(true);
    when(data.get(key, PersistentDataType.STRING)).thenReturn(owner.toString());
    return entity;
  }

  private static Adaptation<?> permissivePolicy() {
    Adaptation<?> policy = mock(Adaptation.class);
    when(policy.canPVP(any(Player.class), any(Location.class))).thenReturn(true);
    when(policy.canPVE(any(Player.class), any(Entity.class))).thenReturn(true);
    return policy;
  }

  private static World world(UUID id) {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(id);
    return world;
  }
}
