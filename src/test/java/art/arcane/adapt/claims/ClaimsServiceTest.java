package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimsServiceTest extends AdaptTestBase {
  private final UUID founder = UUID.randomUUID();
  private final UUID worldId = UUID.randomUUID();
  private final World world = mock(World.class);
  private ClaimsService service;
  private AdaptServer server;
  private ClaimCity city;

  @BeforeEach
  void prepareClaims() throws IOException {
    server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    SkillClaims skill = mock(SkillClaims.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getAnySkill("claims");
    when(skill.isEnabled()).thenReturn(true);
    when(skill.claimsConfig()).thenReturn(new ClaimsConfig());
    doReturn(new SkillClaims().getAdaptations()).when(skill).getAdaptations();
    when(world.getUID()).thenReturn(worldId);
    service = new ClaimsService(plugin);
    when(plugin.getClaimsService()).thenReturn(service);
    city = service.manager().found(founder, "Meadow", new ClaimBlock(worldId, 2008, 64, 2008),
        new ClaimRanks(true, 4, 4, 4, true), new ClaimWorldAccess("world", true));
  }

  @AfterEach
  void closeClaims() throws IOException {
    service.close();
  }

  @Test
  void creativeAndOperatorStatusDoNotBypassResistance() {
    Player outsider = player(UUID.randomUUID());
    when(outsider.getGameMode()).thenReturn(GameMode.CREATIVE);
    when(outsider.isOp()).thenReturn(true);
    assertThat(service.canEdit(outsider, location(2009))).isFalse();
    when(outsider.hasPermission(ClaimsService.BYPASS_PERMISSION)).thenReturn(true);
    assertThat(service.canEdit(outsider, location(2009))).isTrue();
    assertThat(service.canTeleport(outsider, location(2009))).isTrue();
  }

  @Test
  void directAbilitiesCannotEditAnchorsEvenForTheirOwner() {
    Player owner = player(founder);
    assertThat(service.canEdit(owner, location(2009))).isTrue();
    assertThat(service.canEdit(owner, location(2008))).isFalse();
    assertThat(ClaimsService.permitsEdit(founder, location(2008))).isFalse();
    Adaptation<?> adaptation = mock(Adaptation.class, CALLS_REAL_METHODS);
    assertThat(adaptation.canBlockBreak(owner, location(2008))).isFalse();
    assertThat(adaptation.canBlockPlace(player(UUID.randomUUID()), location(2009))).isFalse();
  }

  @Test
  void offlineCleanupUsesPersistedOwnership() {
    assertThat(ClaimsService.permitsEdit(founder, location(2009))).isTrue();
    assertThat(ClaimsService.permitsEdit(UUID.randomUUID(), location(2009))).isFalse();
  }

  @Test
  void memberTeleportsAreBlockedBeforeTheAbilityStarts() {
    Player owner = player(founder);
    assertThat(PaperCompat.teleportAsync(owner, location(2009)).join()).isFalse();
    verify(owner, never()).teleportAsync(any(Location.class));
    assertThat(service.canTeleport(owner, new Location(world, 3000, 64, 3000))).isTrue();
  }

  @Test
  void remoteLootingRequiresPhysicalReach() {
    Player outsider = player(UUID.randomUUID());
    when(outsider.getLocation()).thenReturn(new Location(world, 2008, 64, 2008));
    when(outsider.getEyeLocation()).thenReturn(new Location(world, 2008, 65.62, 2008));
    assertThat(service.canReach(outsider, location(2010))).isTrue();
    assertThat(service.canReach(outsider, location(2020))).isFalse();
  }

  @Test
  void loadingProfilesCannotEraseFounderRanks() {
    Player owner = player(founder);
    AdaptPlayer profile = mock(AdaptPlayer.class);
    when(server.getOnlineAdaptPlayer(founder)).thenReturn(profile);
    service.refreshRanks(owner);
    assertThat(service.manager().snapshot().cities().get(city.id()).ranks().fortification()).isEqualTo(4);
  }

  @Test
  void administrativeResetRetainsLandWithReducedLimits() {
    service.resetRanks(founder);
    ClaimCity retained = service.manager().snapshot().cities().get(city.id());
    assertThat(retained.chunks()).isEqualTo(city.chunks());
    assertThat(retained.ranks()).isEqualTo(ClaimRanks.none());
  }

  private Player player(UUID id) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getWorld()).thenReturn(world);
    return player;
  }

  private Location location(int x) {
    return new Location(world, x, 64, 2008);
  }
}
