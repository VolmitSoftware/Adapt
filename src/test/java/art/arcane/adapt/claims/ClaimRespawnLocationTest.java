package art.arcane.adapt.claims;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimRespawnLocationTest {
    private ClaimsService claims;
    private ClaimSafeLocation terrain;
    private Player player;
    private World world;
    private Location intended;
    private ClaimRespawnLocation locations;

    @BeforeEach
    void prepare() {
        claims = mock(ClaimsService.class);
        terrain = mock(ClaimSafeLocation.class);
        player = mock(Player.class);
        when(player.getWidth()).thenReturn(0.6);
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 1000.5, 64, 1000.5));
        when(player.getLocation()).thenReturn(new Location(world, 200.5, 70, 200.5));
        when(terrain.safeLandingOwned(any(), any())).thenReturn(true);
        when(claims.canTeleport(eq(player), any())).thenReturn(true);
        intended = new Location(world, 0.5, 70, 0.5);
        locations = new ClaimRespawnLocation(claims, terrain);
    }

    @Test
    void usesAPermittedLoadedSpawnWhenIntendedAndSourceAreasAreClaimed() {
        when(claims.canTeleport(eq(player), any())).thenAnswer(call ->
                ((Location) call.getArgument(1)).getX() >= 900);
        assertThat(find()).contains(new Location(world, 1000.5, 64, 1000.5));
    }

    @Test
    void unloadedChunksAreNeverReadOrLoadedSynchronously() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertThat(find()).isEmpty();
        verify(terrain, never()).safeLandingOwned(any(), any());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void currentClaimPolicyIsRecheckedForEveryFallback() {
        assertThat(find()).isPresent();
        when(claims.canTeleport(eq(player), any())).thenReturn(false);
        assertThat(find()).isEmpty();
    }

    @Test
    void unsuitableTerrainHasABoundedSearch() {
        when(terrain.safeLandingOwned(any(), any())).thenReturn(false);
        assertThat(find()).isEmpty();
        verify(terrain, atMost(1224)).safeLandingOwned(any(), any());
    }

    @Test
    void reachesLoadedWildernessBeyondAThreeByThreeTown() {
        intended = new Location(world, 1608.5, 64, 1608.5);
        when(player.getLocation()).thenReturn(intended.clone());
        when(world.getSpawnLocation()).thenReturn(intended.clone());
        when(claims.canTeleport(eq(player), any())).thenAnswer(call -> {
            Location candidate = call.getArgument(1);
            return Math.abs((candidate.getBlockX() >> 4) - 100) > 1
                    || Math.abs((candidate.getBlockZ() >> 4) - 100) > 1;
        });
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(call ->
                Math.abs((int) call.getArgument(0) - 100) >= 3 || Math.abs((int) call.getArgument(1) - 100) >= 3);
        assertThat(find()).contains(new Location(world, 1560.5, 64, 1608.5));
    }

    @Test
    void aBelowCeilingRespawnDoesNotChooseTheNetherRoof() {
        when(world.hasCeiling()).thenReturn(true);
        when(world.getLogicalHeight()).thenReturn(128);
        when(world.getMinHeight()).thenReturn(0);
        intended.setY(127);
        when(player.getLocation()).thenReturn(intended.clone());
        when(world.getSpawnLocation()).thenReturn(intended.clone());
        when(terrain.safeLandingOwned(any(), any())).thenAnswer(call -> ((Location) call.getArgument(1)).getY() >= 128);
        assertThat(find()).isEmpty();
    }

    private Optional<Location> find() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            return locations.find(player, intended);
        }
    }
}
