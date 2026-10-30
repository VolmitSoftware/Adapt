package art.arcane.adapt.util.common.compat;

import art.arcane.adapt.claims.ClaimTravelService;
import art.arcane.adapt.claims.ClaimsService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperCompatTeleportTest {
    @Test
    void deniedPlayerTargetReturnsTheActualRedirectCompletion() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        ClaimsService claims = mock(ClaimsService.class);
        ClaimTravelService travel = mock(ClaimTravelService.class);
        Location target = new Location(world, 100, 64, 100);
        CompletableFuture<Boolean> arrival = new CompletableFuture<>();
        when(claims.enabled()).thenReturn(true);
        when(claims.travel()).thenReturn(travel);
        when(travel.transfer(any())).thenAnswer(call -> {
            ClaimTravelService.Request request = call.getArgument(0);
            assertThat(request.player()).isSameAs(player);
            assertThat(request.requested()).isEqualTo(target);
            assertThat(request.cause()).isEqualTo(PlayerTeleportEvent.TeleportCause.COMMAND);
            return arrival;
        });
        try (MockedStatic<ClaimsService> services = mockStatic(ClaimsService.class)) {
            services.when(ClaimsService::current).thenReturn(claims);
            CompletableFuture<Boolean> result = PaperCompat.teleportAsync(player, target,
                    PlayerTeleportEvent.TeleportCause.COMMAND);
            assertThat(result).isSameAs(arrival).isNotDone();
            arrival.complete(true);
            assertThat(result.join()).isTrue();
            verify(player, never()).teleportAsync(any(), any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void deniedDefaultCauseDoesNotReportSuccessWhenTheRedirectFails() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        ClaimsService claims = mock(ClaimsService.class);
        ClaimTravelService travel = mock(ClaimTravelService.class);
        Location target = new Location(world, 100, 64, 100);
        CompletableFuture<Boolean> arrival = new CompletableFuture<>();
        when(claims.enabled()).thenReturn(true);
        when(claims.travel()).thenReturn(travel);
        when(travel.transfer(any())).thenAnswer(call -> {
            ClaimTravelService.Request request = call.getArgument(0);
            assertThat(request.cause()).isEqualTo(PlayerTeleportEvent.TeleportCause.PLUGIN);
            return arrival;
        });
        try (MockedStatic<ClaimsService> services = mockStatic(ClaimsService.class)) {
            services.when(ClaimsService::current).thenReturn(claims);
            CompletableFuture<Boolean> result = PaperCompat.teleportAsync(player, target);
            assertThat(result).isSameAs(arrival).isNotDone();
            arrival.complete(false);
            assertThat(result.join()).isFalse();
            verify(player, never()).teleportAsync(any(Location.class));
        }
    }

    @Test
    void allowedPlayerTargetsPreserveDirectAbilityTeleportBehavior() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        ClaimsService claims = mock(ClaimsService.class);
        Location target = new Location(world, 100, 100, 100);
        CompletableFuture<Boolean> arrival = CompletableFuture.completedFuture(true);
        when(claims.enabled()).thenReturn(true);
        when(claims.canTeleport(player, target)).thenReturn(true);
        when(player.teleportAsync(target)).thenReturn(arrival);
        try (MockedStatic<ClaimsService> services = mockStatic(ClaimsService.class)) {
            services.when(ClaimsService::current).thenReturn(claims);
            assertThat(PaperCompat.teleportAsync(player, target)).isSameAs(arrival);
            verify(claims, never()).travel();
        }
    }

    @Test
    void nonPlayerEntitiesKeepTheirNativeTeleportFuture() {
        World world = mock(World.class);
        Entity entity = mock(Entity.class);
        Location target = new Location(world, 1, 64, 1);
        CompletableFuture<Boolean> arrival = CompletableFuture.completedFuture(true);
        when(entity.teleportAsync(target)).thenReturn(arrival);
        assertThat(PaperCompat.teleportAsync(entity, target)).isSameAs(arrival);
    }
}
