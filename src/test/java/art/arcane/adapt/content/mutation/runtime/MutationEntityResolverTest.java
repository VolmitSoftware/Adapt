package art.arcane.adapt.content.mutation.runtime;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MutationEntityResolverTest {
  @Test
  void ordinaryAttributionDoesNotPromoteOwnedPetsToPlayers() {
    MutationEntityResolver resolver = new MutationEntityResolver();
    UUID ownerId = UUID.randomUUID();
    Tameable pet = mock(Tameable.class);
    when(pet.getOwnerUniqueId()).thenReturn(ownerId);
    when(pet.getUniqueId()).thenReturn(UUID.randomUUID());

    assertThat(resolver.playerSource(pet)).isNull();
    assertThat(resolver.packOwnerId(pet)).isEqualTo(ownerId);
    assertThat(resolver.packContributorId(pet)).isEqualTo(pet.getUniqueId());
    assertThat(resolver.packOwnedBy(pet, ownerId)).isTrue();
  }

  @Test
  void petProjectilesRemainPackSpecificContributors() {
    MutationEntityResolver resolver = new MutationEntityResolver();
    UUID ownerId = UUID.randomUUID();
    Tameable pet = mock(Tameable.class);
    Projectile projectile = mock(Projectile.class);
    when(pet.getOwnerUniqueId()).thenReturn(ownerId);
    when(pet.getUniqueId()).thenReturn(UUID.randomUUID());
    when(projectile.getShooter()).thenReturn(pet);

    assertThat(resolver.playerSource(projectile)).isNull();
    assertThat(resolver.packOwnerId(projectile)).isEqualTo(ownerId);
    assertThat(resolver.packContributorId(projectile)).isEqualTo(pet.getUniqueId());
    assertThat(resolver.packOwnedBy(projectile, ownerId)).isTrue();
  }

  @Test
  void playerProjectilesUseIdAttribution() {
    MutationEntityResolver resolver = new MutationEntityResolver();
    Player player = mock(Player.class);
    Projectile projectile = mock(Projectile.class);
    UUID playerId = UUID.randomUUID();
    when(projectile.getShooter()).thenReturn(player);
    when(player.getUniqueId()).thenReturn(playerId);

    assertThat(resolver.playerSource(projectile)).isNull();
    assertThat(resolver.projectilePlayerSourceId(projectile)).isEqualTo(playerId);
    assertThat(resolver.packOwnerId(projectile)).isEqualTo(playerId);
    assertThat(resolver.sourceEntity(projectile)).isSameAs(projectile);
  }
}
