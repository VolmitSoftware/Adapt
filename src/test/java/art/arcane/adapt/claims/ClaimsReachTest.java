package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimsReachTest extends AdaptTestBase {
  private final World world = mock(World.class);
  private final Player player = mock(Player.class);
  private final Block chest = mock(Block.class);
  private ClaimsService service;
  private Location destination;

  @BeforeEach
  void prepareClaims() throws IOException {
    UUID worldId = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    SkillClaims skill = mock(SkillClaims.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getAnySkill("claims");
    when(skill.isEnabled()).thenReturn(true);
    when(skill.claimsConfig()).thenReturn(new ClaimsConfig());
    KList<Adaptation<?>> adaptations = new SkillClaims().getAdaptations();
    when(skill.getAdaptations()).thenReturn(adaptations);
    when(world.getUID()).thenReturn(worldId);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getEyeLocation()).thenReturn(new Location(world, 2008.5, 65.62, 2011.5));
    destination = new Location(world, 2008, 64, 2008);
    when(chest.getType()).thenReturn(Material.CHEST);
    when(chest.getX()).thenReturn(2008);
    when(chest.getY()).thenReturn(64);
    when(chest.getZ()).thenReturn(2008);
    when(world.getBlockAt(2008, 64, 2008)).thenReturn(chest);
    service = new ClaimsService(plugin);
    when(plugin.getClaimsService()).thenReturn(service);
    service.manager().found(UUID.randomUUID(), "Meadow", new ClaimBlock(worldId, 2008, 64, 2008),
        new ClaimRanks(true, 1, 0, 0, false), new ClaimWorldAccess("world", true));
  }

  @AfterEach
  void closeClaims() throws IOException {
    service.close();
  }

  @Test
  void nearbyClaimedChestBehindWallIsDeniedToRemoteAbilities() {
    Block wall = mock(Block.class);
    when(wall.getType()).thenReturn(Material.STONE);
    rayHits(wall);
    assertThat(service.canReach(player, destination)).isFalse();
    Adaptation<?> ability = mock(Adaptation.class, CALLS_REAL_METHODS);
    assertThat(ability.canAccessChest(player, destination)).isFalse();
    assertThat(ability.canInteract(player, destination)).isFalse();
  }

  @Test
  void visibleChestAndTheDoorBeingInteractedWithRemainReachable() {
    rayHits(chest);
    assertThat(service.canReach(player, destination)).isTrue();
    when(chest.getType()).thenReturn(Material.OAK_DOOR);
    assertThat(service.canReach(player, destination)).isTrue();
    when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
        eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(null);
    assertThat(service.canReach(player, destination)).isTrue();
  }

  @Test
  void nearHalfOfTheSameDoubleChestDoesNotObstructItsOtherHalf() {
    Chest left = mock(Chest.class);
    when(left.getType()).thenReturn(Chest.Type.LEFT);
    when(left.getFacing()).thenReturn(BlockFace.NORTH);
    when(chest.getBlockData()).thenReturn(left);
    Block other = mock(Block.class);
    Chest right = mock(Chest.class);
    when(right.getType()).thenReturn(Chest.Type.RIGHT);
    when(right.getFacing()).thenReturn(BlockFace.NORTH);
    when(other.getType()).thenReturn(Material.CHEST);
    when(other.getBlockData()).thenReturn(right);
    when(other.getX()).thenReturn(2009);
    when(other.getY()).thenReturn(64);
    when(other.getZ()).thenReturn(2008);
    when(player.getEyeLocation()).thenReturn(new Location(world, 2011.5, 65.62, 2008.5));
    rayHits(other);
    assertThat(service.canReach(player, destination)).isTrue();
    when(right.getType()).thenReturn(Chest.Type.SINGLE);
    assertThat(service.canReach(player, destination)).isFalse();
  }

  @Test
  void distantAndOtherWorldContainersFailBeforeRayTracing() {
    when(player.getEyeLocation()).thenReturn(new Location(world, 2020, 65, 2008));
    assertThat(service.canReach(player, destination)).isFalse();
    when(player.getEyeLocation()).thenReturn(new Location(mock(World.class), 2008, 65, 2008));
    assertThat(service.canReach(player, destination)).isFalse();
    verify(world, never()).rayTraceBlocks(any(), any(), anyDouble(), any(), eq(true));
  }

  @Test
  void unclaimedRemoteContainersAndExplicitBypassKeepTheirExistingAccess() {
    assertThat(service.canReach(player, new Location(world, 3000, 64, 3000))).isTrue();
    when(player.hasPermission(ClaimsService.BYPASS_PERMISSION)).thenReturn(true);
    assertThat(service.canReach(player, destination)).isTrue();
    verify(player, never()).getEyeLocation();
    verify(world, never()).rayTraceBlocks(any(), any(), anyDouble(), any(), eq(true));
  }

  @Test
  void foliaRejectsAnUnownedPlayerBeforeReadingEntityPosition() {
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      assertThat(service.canReach(player, destination)).isFalse();
      verify(player, never()).getEyeLocation();
      verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
  }

  @Test
  void foliaRequiresTheWholeRayAndCollisionMarginBeforeWorldReads() {
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      assertThat(service.canReach(player, destination)).isFalse();
      verify(world, never()).rayTraceBlocks(any(), any(), anyDouble(), any(), eq(true));
      verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
      scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class), anyDouble(), anyDouble())).thenReturn(true);
      rayHits(chest);
      assertThat(service.canReach(player, destination)).isTrue();
    }
  }

  private void rayHits(Block block) {
    RayTraceResult result = mock(RayTraceResult.class);
    when(result.getHitBlock()).thenReturn(block);
    when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
        eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(result);
  }
}
