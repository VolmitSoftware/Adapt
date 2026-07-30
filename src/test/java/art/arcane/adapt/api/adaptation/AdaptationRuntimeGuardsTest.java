package art.arcane.adapt.api.adaptation;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptationRuntimeGuardsTest {
  @Test
  void creativeModeFollowsTheConfiguredAdaptationGate() {
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.CREATIVE, false)).isFalse();
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.CREATIVE, true)).isTrue();
  }

  @Test
  void spectatorRemainsBlockedAndGameplayModesRemainAllowed() {
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.SPECTATOR, true)).isFalse();
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.SURVIVAL, false)).isTrue();
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.ADVENTURE, false)).isTrue();
  }

  @Test
  void onlyDeathSafeEvaluationAcceptsADeadPlayer() {
    Player player = mock(Player.class);
    when(player.isDead()).thenReturn(true);

    assertThat(AdaptationRuntimeGuards.canEvaluateActiveLevel(player, false)).isFalse();
    assertThat(AdaptationRuntimeGuards.canEvaluateActiveLevel(player, true)).isTrue();
    assertThat(AdaptationRuntimeGuards.canEvaluateActiveLevel(null, true)).isFalse();
  }

  @Test
  void uuidFriendlyCheckPreservesProtectedEntityRules() {
    UUID ownerId = UUID.randomUUID();
    Display display = mock(Display.class);
    Interaction interaction = mock(Interaction.class);
    ArmorStand marker = mock(ArmorStand.class);
    Entity invulnerable = mock(Entity.class);
    Entity npc = mock(Entity.class);
    Entity servant = mock(Entity.class);
    PersistentDataContainer servantData = mock(PersistentDataContainer.class);
    when(marker.isMarker()).thenReturn(true);
    when(invulnerable.isInvulnerable()).thenReturn(true);
    when(npc.hasMetadata("NPC")).thenReturn(true);
    when(servant.getPersistentDataContainer()).thenReturn(servantData);
    when(servantData.has(any(NamespacedKey.class), same(PersistentDataType.STRING)))
        .thenReturn(true);

    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, display)).isTrue();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, interaction)).isTrue();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, marker)).isTrue();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, invulnerable)).isTrue();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, npc)).isTrue();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, servant)).isTrue();
  }

  @Test
  void uuidFriendlyCheckProtectsOnlyTheActorsOwnTameable() {
    UUID ownerId = UUID.randomUUID();
    AnimalTamer owner = mock(AnimalTamer.class);
    Tameable tameable = mock(Tameable.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(owner.getUniqueId()).thenReturn(ownerId);
    when(tameable.isTamed()).thenReturn(true);
    when(tameable.getOwner()).thenReturn(owner);
    when(tameable.getPersistentDataContainer()).thenReturn(data);

    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(ownerId, tameable)).isTrue();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(UUID.randomUUID(), tameable)).isFalse();
    assertThat(AdaptationRuntimeGuards.isProtectedFriendlyOwned(null, tameable)).isFalse();
  }
}
