package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemProtectionTest {
  private final WorldRef world = mock(WorldRef.class);
  private final PlayerRef owner = GolemTestEntities.entity(PlayerRef.class, Id.minecraft("player"));
  @SuppressWarnings("unchecked")
  private final Adaptation<Object> policy = mock(Adaptation.class);

  @BeforeEach
  void setUp() {
    lenient().when(world.uid()).thenReturn(UUID.randomUUID());
  }

  @Test
  void hostileMobsFollowThePolicyAdaptationsPveRule() {
    MobRef zombie = living(MobRef.class, "zombie");
    when(policy.canPVE(eq(owner), any(EntityRef.class))).thenReturn(true, false);

    assertThat(GolemProtection.canDamage(policy, owner, zombie)).isTrue();
    assertThat(GolemProtection.canDamage(policy, owner, zombie)).isFalse();
    verify(policy, never()).canPVP(any(PlayerRef.class), any(Loc.class));
  }

  @Test
  void playersFollowThePolicyAdaptationsPvpRule() {
    PlayerRef rival = living(PlayerRef.class, "player");
    when(policy.canPVP(eq(owner), any(Loc.class))).thenReturn(true, false);

    assertThat(GolemProtection.canDamage(policy, owner, rival)).isTrue();
    assertThat(GolemProtection.canDamage(policy, owner, rival)).isFalse();
    verify(policy, never()).canPVE(any(PlayerRef.class), any(EntityRef.class));
  }

  @Test
  void theOwnersPetsAreNeverDamagedWhateverThePolicySays() {
    UUID ownerId = owner.uuid();
    MobRef pet = living(MobRef.class, "wolf");
    when(pet.tameOwner()).thenReturn(ownerId);
    lenient().when(policy.canPVE(any(PlayerRef.class), any(EntityRef.class))).thenReturn(true);

    assertThat(GolemProtection.canDamage(policy, owner, pet)).isFalse();
  }

  @Test
  void withoutAPolicyAdaptationGolemsDamageNothing() {
    assertThat(GolemProtection.canDamage(null, owner, living(MobRef.class, "zombie"))).isFalse();
  }

  private <T extends LivingRef> T living(Class<T> type, String entityType) {
    T entity = GolemTestEntities.entity(type, Id.minecraft(entityType));
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.location()).thenReturn(new Loc(world, 4D, 64D, 4D));
    return entity;
  }
}
