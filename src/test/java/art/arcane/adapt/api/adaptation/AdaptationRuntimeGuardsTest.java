package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AdaptationRuntimeGuardsTest {
  @Test
  void unavailablePlayersCannotOpenAdaptationMenus() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    Player player = mock(Player.class);
    when(adaptation.getPlayer(player)).thenReturn(null);

    assertThat(AdaptationGuiSupport.openGui(adaptation, player, true)).isFalse();
    verify(adaptation, never()).hasUsePermission(player, adaptation);
  }

  @Test
  void unreadyProfilesCannotExposeAdaptationState() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(player.isRuntimeReady()).thenReturn(false);

    assertThat(AdaptationRuntimeGuards.getLevel(adaptation, player)).isZero();
    assertThat(AdaptationRuntimeGuards.getStorage(adaptation, player, "key", "fallback"))
        .isEqualTo("fallback");
    assertThat(AdaptationRuntimeGuards.setStorage(adaptation, player, "key", "value")).isFalse();
    assertThat(AdaptationRuntimeGuards.canUse(adaptation, player)).isFalse();

    verify(player, times(4)).isRuntimeReady();
    verifyNoMoreInteractions(player);
  }

  @Test
  void readyProfilesRetainLearnedAdaptationLevels() {
    Adaptation<Object> adaptation = mock(Adaptation.class);
    Skill<Object> skill = mock(Skill.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    PlayerSkillLine skillLine = mock(PlayerSkillLine.class);
    when(player.isRuntimeReady()).thenReturn(true);
    when(player.getData()).thenReturn(data);
    when(adaptation.isEnabled()).thenReturn(true);
    doReturn(skill).when(adaptation).getSkill();
    when(adaptation.getName()).thenReturn("vault");
    when(skill.isEnabled()).thenReturn(true);
    when(skill.getName()).thenReturn("agility");
    when(data.getSkillLineNullable("agility")).thenReturn(skillLine);
    when(skillLine.getAdaptationLevel("vault")).thenReturn(3);

    assertThat(AdaptationRuntimeGuards.getLevel(adaptation, player)).isEqualTo(3);
  }

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
