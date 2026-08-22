package art.arcane.adapt.api.mutation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MutationManagerSelectionTest extends AdaptTestBase {
  private AdaptPlayer adaptPlayer;
  private PlayerData data;
  private Player player;
  private SkillRegistry registry;
  private World world;

  @BeforeEach
  void setUpMutationPlayer() {
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    adaptPlayer = mock(AdaptPlayer.class);
    data = new PlayerData();
    data.getMutationData().setSlotUnlockedOverride(1, true);
    data.getMutationData().setSlotUnlockedOverride(2, true);
    player = mock(Player.class);
    world = mock(World.class);
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();

    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getPlayer(player)).thenReturn(adaptPlayer);
    when(server.getSkillRegistry()).thenReturn(registry);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getData()).thenReturn(data);
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    when(player.hasPermission(anyString())).thenReturn(true);
    when(world.getUID()).thenReturn(worldId);
    when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
    when(world.getName()).thenReturn("world");
  }

  @Test
  void ordinarySelectionRequiresFreshBookshelfAuthorization() {
    MutationManager manager = new MutationManager(enabledConfig());

    MutationSelectionResult result = manager.select(player, 1, MutationType.GALE_LUNG, false);

    assertThat(result.success()).isFalse();
    assertThat(result.message()).contains("bookshelf");
  }

  @Test
  void slotCooldownAndCombatLockRejectBeforeQualificationWork() {
    MutationManager manager = new MutationManager(enabledConfig());
    authorize(manager);
    data.getMutationData().setSlotReadyAt(1, System.currentTimeMillis() + 60_000L);

    MutationSelectionResult cooldown = manager.select(player, 1, MutationType.GALE_LUNG, false);

    assertThat(cooldown.success()).isFalse();
    assertThat(cooldown.cooldownRemainingMillis()).isPositive();

    data.getMutationData().setSlotReadyAt(1, 0L);
    manager.getCombatLock().tag(player.getUniqueId());
    MutationSelectionResult combat = manager.select(player, 1, MutationType.GALE_LUNG, false);

    assertThat(combat.success()).isFalse();
    assertThat(combat.message()).contains("combat");
  }

  @Test
  void staleConfirmationAndDuplicateAdministrativeSelectionFailClosed() {
    MutationManager manager = new MutationManager(enabledConfig());
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());

    MutationSelectionResult stale = manager.select(
        player,
        1,
        MutationType.BASTION_SPINE,
        false,
        ""
    );

    assertThat(stale.success()).isFalse();
    assertThat(stale.message()).contains("changed while confirmation was open");

    data.getMutationData().setSlotTwoId(MutationType.VERDANT_MOLT.id());
    MutationSelectionResult otherSlotChanged = manager.select(
        player,
        1,
        MutationType.GALE_LUNG,
        false,
        MutationType.GALE_LUNG.id(),
        ""
    );

    assertThat(otherSlotChanged.success()).isFalse();
    assertThat(otherSlotChanged.message()).contains("loadout changed");

    data.getMutationData().setSlotOneId("");
    data.getMutationData().setSlotTwoId(MutationType.GALE_LUNG.id());
    MutationSelectionResult duplicate = manager.select(player, 1, MutationType.GALE_LUNG, true);

    assertThat(duplicate.success()).isFalse();
    assertThat(duplicate.message()).contains("same Mutation");
  }

  @Test
  void administrativeSelectionStillRejectsAnExplicitPairConflict() {
    MutationConfig config = Json.fromJson(
        "{\"galeLung\":{\"conflicts\":[\"bastion-spine\"]}}",
        MutationConfig.class
    );
    config.normalize();
    MutationManager manager = new MutationManager(config);
    data.getMutationData().setSlotTwoId(MutationType.BASTION_SPINE.id());

    MutationSelectionResult result = manager.select(player, 1, MutationType.GALE_LUNG, true);

    assertThat(result.success()).isFalse();
    assertThat(result.message()).contains("conflicts with Bastion Spine");
  }

  @Test
  void basePermissionAndWorldRestrictionsCannotBeBypassedThroughTheManager() {
    MutationManager manager = new MutationManager(enabledConfig());
    authorize(manager);
    when(player.hasPermission("adapt.mutations")).thenReturn(false);

    MutationSelectionResult permission = manager.select(player, 1, MutationType.GALE_LUNG, false);

    assertThat(permission.success()).isFalse();
    assertThat(permission.message()).contains("adapt.mutations");

    when(player.hasPermission("adapt.mutations")).thenReturn(true);
    MutationConfig restrictedConfig = Json.fromJson(
        "{\"enabled\":true,\"worldBlacklist\":[\"minecraft:overworld\"]}",
        MutationConfig.class
    );
    restrictedConfig.normalize();
    MutationManager restricted = new MutationManager(restrictedConfig);
    authorize(restricted);

    MutationSelectionResult worldResult = restricted.select(player, 1, MutationType.GALE_LUNG, false);

    assertThat(worldResult.success()).isFalse();
    assertThat(worldResult.message()).contains("does not work");
  }

  @Test
  void basePermissionRevocationInvalidatesAnExpressedSnapshot() {
    qualify("agility", "movement-proof");
    qualify("swords", "hunt-proof");
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());
    MutationManager manager = new MutationManager(enabledConfig());

    assertThat(manager.reconcile(adaptPlayer).expressed()).containsExactly(MutationType.GALE_LUNG);

    when(player.hasPermission("adapt.mutations")).thenReturn(false);
    MutationSnapshot revoked = manager.snapshot(player);

    assertThat(revoked.expressed()).isEmpty();
    assertThat(revoked.state(MutationType.GALE_LUNG)).isEqualTo(MutationState.DORMANT);
    assertThat(revoked.reason(MutationType.GALE_LUNG)).contains("adapt.mutations");
  }

  @Test
  void qualifyingAdaptationPermissionRevocationInvalidatesAnExpressedSnapshot() {
    Adaptation<?> movement = qualify("agility", "movement-proof");
    qualify("swords", "hunt-proof");
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());
    MutationManager manager = new MutationManager(enabledConfig());

    assertThat(manager.reconcile(adaptPlayer).expressed()).containsExactly(MutationType.GALE_LUNG);

    when(player.isPermissionSet("adapt.use.movementproof")).thenReturn(true);
    when(player.hasPermission("adapt.use.movementproof")).thenReturn(false);
    when(movement.hasUsePermission(player, movement)).thenReturn(false);
    MutationSnapshot revoked = manager.snapshot(player);

    assertThat(revoked.expressed()).isEmpty();
    assertThat(revoked.state(MutationType.GALE_LUNG)).isEqualTo(MutationState.DORMANT);
    assertThat(revoked.reason(MutationType.GALE_LUNG)).contains("Body");
  }

  @Test
  void qualifyingAdaptationPermissionGrantInvalidatesADormantSnapshot() throws InterruptedException {
    Adaptation<?> movement = qualify("agility", "movement-proof");
    qualify("swords", "hunt-proof");
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());
    when(player.isPermissionSet("adapt.use.movementproof")).thenReturn(true);
    when(player.hasPermission("adapt.use.movementproof")).thenReturn(false);
    when(movement.hasUsePermission(player, movement)).thenReturn(false);
    MutationManager manager = new MutationManager(enabledConfig());

    MutationSnapshot denied = manager.reconcile(adaptPlayer);
    assertThat(denied.state(MutationType.GALE_LUNG)).isEqualTo(MutationState.DORMANT);

    when(player.hasPermission("adapt.use.movementproof")).thenReturn(true);
    when(movement.hasUsePermission(player, movement)).thenReturn(true);
    Thread.sleep(1_050L);
    MutationSnapshot granted = manager.snapshot(player);

    assertThat(granted.expressed()).containsExactly(MutationType.GALE_LUNG);
  }

  @Test
  void duplicatePersistedSlotsBecomeConflictWithoutPruningRawIds() {
    MutationManager manager = new MutationManager(enabledConfig());
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());
    data.getMutationData().setSlotTwoId(MutationType.GALE_LUNG.id());

    MutationSnapshot snapshot = manager.reconcile(adaptPlayer);

    assertThat(snapshot.slotOneId()).isEqualTo(MutationType.GALE_LUNG.id());
    assertThat(snapshot.slotTwoId()).isEqualTo(MutationType.GALE_LUNG.id());
    assertThat(snapshot.state(MutationType.GALE_LUNG)).isEqualTo(MutationState.CONFLICT);
    assertThat(snapshot.expressed()).doesNotContain(MutationType.GALE_LUNG);
    assertThat(snapshot.qualified(MutationType.GALE_LUNG)).isFalse();
    assertThat(snapshot.qualifyingAdaptations(MutationType.GALE_LUNG)).isEmpty();
  }

  @Test
  void reconciliationRepairsSelectedDiscoveryWithoutChangingTheSelection() {
    MutationManager manager = new MutationManager(enabledConfig());
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());

    MutationSnapshot snapshot = manager.reconcile(adaptPlayer);

    assertThat(snapshot.slotOneId()).isEqualTo(MutationType.GALE_LUNG.id());
    assertThat(data.getMutationData().getDiscovered()).containsExactly(MutationType.GALE_LUNG.id());
    verify(adaptPlayer).saveNow();
  }

  @Test
  void reconciliationClampsDurableResourcesToTheActiveConfig() {
    MutationConfig config = Json.fromJson(
        "{\"deepblood\":{\"maximumIchor\":40},\"livingLattice\":{\"maximumRootCharge\":5}}",
        MutationConfig.class
    );
    config.normalize();
    MutationManager manager = new MutationManager(config);
    data.getMutationData().setDeepbloodIchor(9_999D);
    data.getMutationData().setLivingLatticeRootCharge(63D);

    manager.reconcile(adaptPlayer);

    assertThat(data.getMutationData().getDeepbloodIchor()).isEqualTo(40D);
    assertThat(data.getMutationData().getLivingLatticeRootCharge()).isEqualTo(5D);
    verify(adaptPlayer).saveNow();
  }

  @Test
  void successfulSelectionPersistsDiscoveryCooldownAndExpression() {
    qualify("agility", "movement-proof");
    qualify("swords", "hunt-proof");
    MutationManager manager = new MutationManager(enabledConfig());
    authorize(manager);

    MutationSelectionResult result = manager.select(player, 1, MutationType.GALE_LUNG, false);

    assertThat(result.success()).isTrue();
    assertThat(data.getMutationData().getSlotOneId()).isEqualTo(MutationType.GALE_LUNG.id());
    assertThat(data.getMutationData().getDiscovered()).containsExactly(MutationType.GALE_LUNG.id());
    assertThat(data.getMutationData().getSlotOneReadyAt()).isGreaterThan(System.currentTimeMillis());
    MutationSnapshot snapshot = manager.snapshot(player);
    assertThat(snapshot.expressed()).containsExactly(MutationType.GALE_LUNG);
    assertThat(snapshot.qualified(MutationType.GALE_LUNG)).isTrue();
    assertThat(snapshot.qualifyingAdaptations(MutationType.GALE_LUNG))
        .containsExactly("movement-proof", "hunt-proof");
    assertThat(snapshot.qualificationReason(MutationType.GALE_LUNG)).isEqualTo("Ready");
    verify(adaptPlayer).saveNow();
  }

  @Test
  void defaultConfigKeepsSavedSelectionsInactiveAndRejectsBookshelfAccess() {
    data.getMutationData().setSlotOneId(MutationType.GALE_LUNG.id());
    MutationManager manager = new MutationManager(MutationConfig.defaults());

    authorize(manager);
    MutationSnapshot snapshot = manager.reconcile(adaptPlayer);

    assertThat(manager.hasValidBookshelfAccess(player)).isFalse();
    assertThat(snapshot.expressed()).isEmpty();
    assertThat(snapshot.perfect()).isFalse();
    assertThat(snapshot.state(MutationType.GALE_LUNG)).isEqualTo(MutationState.DORMANT);
    assertThat(snapshot.reason(MutationType.GALE_LUNG)).contains("turned off");
  }

  private void authorize(MutationManager manager) {
    manager.authorizeBookshelf(player, new Location(world, 0D, 64D, 0D));
  }

  private MutationConfig enabledConfig() {
    MutationConfig config = Json.fromJson("{\"enabled\":true}", MutationConfig.class);
    config.normalize();
    return config;
  }

  private Adaptation<?> qualify(String skillId, String adaptationId) {
    Skill<?> skill = mock(Skill.class);
    Adaptation<?> adaptation = mock(Adaptation.class);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(adaptation);
    doReturn(skill).when(registry).getSkill(skillId);
    when(skill.getName()).thenReturn(skillId);
    when(skill.isEnabled()).thenReturn(true);
    when(skill.hasUsePermission(player, skill)).thenReturn(true);
    when(skill.getAdaptations()).thenReturn(adaptations);
    when(adaptation.getName()).thenReturn(adaptationId);
    when(adaptation.isEnabled()).thenReturn(true);
    when(adaptation.hasUsePermission(player, adaptation)).thenReturn(true);

    PlayerSkillLine line = data.getSkillLine(skillId);
    PlayerAdaptation learned = new PlayerAdaptation();
    learned.setId(adaptationId);
    learned.setLevel(1);
    line.getAdaptations().put(adaptationId, learned);
    return adaptation;
  }
}
