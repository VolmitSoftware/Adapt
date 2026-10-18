package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
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
  private final World world = mock(World.class);
  private final Player owner = GolemTestEntities.entity(Player.class);
  @SuppressWarnings("unchecked")
  private final Adaptation<Object> policy = mock(Adaptation.class);

  @BeforeEach
  void setUp() {
    lenient().when(world.getUID()).thenReturn(UUID.randomUUID());
  }

  @Test
  void hostileMobsFollowThePolicyAdaptationsPveRule() {
    Zombie zombie = living(Zombie.class);
    when(policy.canPVE(eq(owner), any(Location.class))).thenReturn(true, false);

    assertThat(GolemProtection.canDamage(policy, owner, zombie)).isTrue();
    assertThat(GolemProtection.canDamage(policy, owner, zombie)).isFalse();
    verify(policy, never()).canPVP(any(Player.class), any(Location.class));
  }

  @Test
  void playersFollowThePolicyAdaptationsPvpRule() {
    Player rival = living(Player.class);
    when(policy.canPVP(eq(owner), any(Location.class))).thenReturn(true, false);

    assertThat(GolemProtection.canDamage(policy, owner, rival)).isTrue();
    assertThat(GolemProtection.canDamage(policy, owner, rival)).isFalse();
    verify(policy, never()).canPVE(any(Player.class), any(Location.class));
  }

  @Test
  void theOwnersPetsAreNeverDamagedWhateverThePolicySays() {
    Wolf pet = living(Wolf.class);
    when(pet.isTamed()).thenReturn(true);
    when(pet.getOwner()).thenReturn(owner);
    lenient().when(policy.canPVE(any(Player.class), any(Location.class))).thenReturn(true);

    assertThat(GolemProtection.canDamage(policy, owner, pet)).isFalse();
  }

  @Test
  void withoutAPolicyAdaptationGolemsDamageNothing() {
    assertThat(GolemProtection.canDamage(null, owner, living(Zombie.class))).isFalse();
  }

  private <T extends LivingEntity> T living(Class<T> type) {
    T entity = GolemTestEntities.entity(type);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.getLocation()).thenReturn(new Location(world, 4D, 64D, 4D));
    return entity;
  }
}
