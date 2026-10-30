package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.content.skill.SkillClaims;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
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
  void outsiderTownDestinationsRequireRedirection() {
    Player outsider = player(UUID.randomUUID());
    assertThat(service.canTeleport(outsider, location(2009))).isFalse();
    assertThat(service.canTeleport(outsider, new Location(world, 3000, 64, 3000))).isTrue();
  }

  @Test
  void foundersCanTeleportIntoTheirTownWithoutGainingAnchorEditPermission() {
    Player owner = player(founder);
    assertThat(service.canTeleport(owner, location(2008))).isTrue();
    assertThat(service.canEdit(owner, location(2008))).isFalse();
  }

  @Test
  void membersCanTeleportIntoTheirOwnTownButNotAnotherTown() {
    UUID member = UUID.randomUUID();
    service.manager().invite(founder, member, 1000);
    service.manager().accept(member, founder, 1001);
    service.manager().found(UUID.randomUUID(), "Birch", new ClaimBlock(worldId, 3000, 64, 3000),
        new ClaimRanks(true, 4, 4, 4, true), new ClaimWorldAccess("world", true));
    assertThat(service.canTeleport(player(member), location(2009))).isTrue();
    assertThat(service.canTeleport(player(member), new Location(world, 3000, 64, 3000))).isFalse();
  }

  @Test
  void leavingTheTownRevokesArrivalPermissionImmediately() {
    UUID member = UUID.randomUUID();
    service.manager().invite(founder, member, 1000);
    service.manager().accept(member, founder, 1001);
    Player resident = player(member);
    assertThat(service.canTeleport(resident, location(2009))).isTrue();
    service.manager().leave(member);
    assertThat(service.canTeleport(resident, location(2009))).isFalse();
  }

  @Test
  void arrivalRequiresAPlayerAndAValidDestination() {
    assertThat(service.canTeleport(null, new Location(world, 3000, 64, 3000))).isFalse();
    assertThat(service.canTeleport(player(founder), null)).isFalse();
    assertThat(service.canTeleport(player(founder), new Location(null, 0, 64, 0))).isFalse();
  }

  @Test
  void arrivalPreparationRejectsAWholeVolumeWhenOnlyItsFarEdgeEntersATown() {
    Player outsider = player(UUID.randomUUID());
    BoundingBox outside = new BoundingBox(1970, 10, 2000, 1984, 90, 2010);
    BoundingBox crossing = new BoundingBox(1970, 10, 2000, 1985, 90, 2010);
    assertThat(service.canPrepareArrival(outsider, world, outside)).isTrue();
    assertThat(service.canPrepareArrival(outsider, world, crossing)).isFalse();
  }

  @Test
  void arrivalPreparationChecksAnchorsInsideTheVolumeRatherThanOnlyItsCorners() {
    Player owner = player(founder);
    assertThat(service.canPrepareArrival(owner, world,
        new BoundingBox(2000, 10, 2000, 2016, 64, 2016))).isTrue();
    assertThat(service.canPrepareArrival(owner, world,
        new BoundingBox(2000, 10, 2000, 2016, 65, 2016))).isFalse();
    assertThat(service.canPrepareArrival(owner, world,
        new BoundingBox(2000, 65, 2000, 2016, 90, 2016))).isTrue();
  }

  @Test
  void arrivalPreparationIncludesEveryPersonalPlotTouchedByTheVolume() {
    UUID citizen = UUID.randomUUID();
    service.manager().invite(founder, citizen, 1000);
    service.manager().accept(citizen, founder, 1001);
    for (int x = 127; x <= 129; x++) {
      for (int z = 124; z <= 126; z++) {
        service.manager().expand(founder, new ClaimChunk(worldId, x, z), new ClaimWorldAccess("world", true));
      }
    }
    service.manager().createPlot(citizen, new ClaimBlock(worldId, 2056, 64, 2008),
        new ClaimRanks(false, 0, 0, 0, true));
    BoundingBox footprint = new BoundingBox(2028, 10, 2000, 2033, 60, 2010);
    assertThat(service.canPrepareArrival(player(founder), world, footprint)).isFalse();
    assertThat(service.canPrepareArrival(player(citizen), world, footprint)).isTrue();
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
