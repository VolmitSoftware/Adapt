package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrackenTravelBridgeTest {
    private static final UUID PLAYER_ID = UUID.fromString("82f4ba17-879b-4c5b-8395-fd57c556583e");

    private ClaimTravelService travel;
    private BrackenTravelBridge bridge;
    private Player player;
    private ConsoleCommandSender console;
    private World underdark;
    private World overworld;
    private Location requested;
    private Location approved;
    private ClaimTravelService.Prepared prepared;
    private CompletableFuture<Optional<ClaimTravelService.Prepared>> approval;
    private CompletableFuture<Boolean> completion;
    private MockedStatic<J> scheduler;
    private MockedStatic<Bukkit> bukkit;
    private final List<String> dispatched = new ArrayList<>();
    private final List<Runnable> timeouts = new ArrayList<>();

    @BeforeEach
    void prepareBridge() {
        Adapt plugin = mock(Adapt.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        travel = mock(ClaimTravelService.class);
        player = mock(Player.class);
        console = mock(ConsoleCommandSender.class);
        underdark = mock(World.class);
        overworld = mock(World.class);
        when(underdark.getKey()).thenReturn(new NamespacedKey("bracken", "the_underdark"));
        when(overworld.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(underdark);
        when(player.getLocation()).thenReturn(new Location(underdark, 32.5, 180, -48.5));
        requested = new Location(overworld, 32.5, -59, -48.5, 90, 12);
        approved = new Location(overworld, 48.5, -59, -48.5, 90, 12);
        prepared = new ClaimTravelService.Prepared(PLAYER_ID, 7, approved);
        approval = new CompletableFuture<>();
        completion = new CompletableFuture<>();
        when(travel.prepare(any())).thenReturn(approval);
        when(travel.valid(prepared)).thenReturn(true);
        when(travel.commit(prepared)).thenReturn(completion);
        scheduler = mockStatic(J.class);
        scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return true;
        });
        scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class), eq(200))).thenAnswer(call -> {
            timeouts.add(call.getArgument(1));
            return true;
        });
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getName).thenReturn("Paper");
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        bukkit.when(() -> Bukkit.dispatchCommand(eq(console), any(String.class))).thenAnswer(call -> {
            dispatched.add(call.getArgument(1));
            return true;
        });
        bridge = new BrackenTravelBridge(plugin, travel);
    }

    @AfterEach
    void releaseBridge() {
        bridge.close();
        bukkit.close();
        scheduler.close();
    }

    @Test
    void macroCommandsParseWithoutAPlayerExecutorButExecutionStillRejectsThatSource() {
        CommandSourceStack source = source(requested);
        when(source.getExecutor()).thenReturn(null);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(BrackenTravelBridge.command());
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(
                "brackentravel request underdark_to_overworld 41", source);
        assertThat(parsed.getExceptions()).isEmpty();
        assertThat(parsed.getReader().getRemaining()).isEmpty();
        assertThat(bridge.request(source, "underdark_to_overworld", 41)).isZero();
        verify(travel, never()).prepare(any());
    }

    @Test
    void onlyConsoleOriginWithPlayerExecutorAndKnownRouteCanRequestTravel() {
        CommandSourceStack source = source(requested);
        when(source.getSender()).thenReturn(player);
        assertThat(bridge.request(source, "underdark_to_overworld", 41)).isZero();
        when(source.getSender()).thenReturn(console);
        when(source.getExecutor()).thenReturn(null);
        assertThat(bridge.request(source, "underdark_to_overworld", 41)).isZero();
        assertThat(bridge.request(source(requested), "arbitrary:function", 41)).isZero();
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 0)).isZero();
        verify(travel, never()).prepare(any());
    }

    @Test
    void wrongWorldAndNonfiniteDestinationAreRejectedBeforePreparation() {
        assertThat(bridge.request(source(new Location(underdark, 1, 2, 3)), "underdark_to_overworld", 41)).isZero();
        assertThat(bridge.request(source(new Location(overworld, Double.NaN, 2, 3)), "underdark_to_overworld", 41)).isZero();
        when(player.getWorld()).thenReturn(overworld);
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isZero();
        verify(travel, never()).prepare(any());
    }

    @Test
    void registeredFallingRoutesUseTheirOwnSourceAndArrivalProfile() {
        World faewild = mock(World.class);
        when(faewild.getKey()).thenReturn(new NamespacedKey("bracken", "the_faewild"));
        when(player.getWorld()).thenReturn(faewild);
        Location landing = new Location(overworld, 32.5, 305, -48.5, 90, 12);
        assertThat(bridge.request(source(landing), "faewild_to_overworld", 41)).isOne();
        ArgumentCaptor<ClaimTravelService.Request> request = ArgumentCaptor.forClass(ClaimTravelService.Request.class);
        verify(travel).prepare(request.capture());
        assertThat(request.getValue().requested()).isEqualTo(landing);
        assertThat(request.getValue().profile()).isEqualTo(ClaimArrivalProfile.BODY_AIR);
    }

    @Test
    void savedMarkerRoutesCanReturnToAnotherLoadedDimension() {
        assertThat(bridge.request(source(requested), "recall", 41)).isOne();
        assertThat(bridge.request(source(requested), "astral_return", 42)).isOne();
        ArgumentCaptor<ClaimTravelService.Request> requests = ArgumentCaptor.forClass(ClaimTravelService.Request.class);
        verify(travel, times(2)).prepare(requests.capture());
        assertThat(requests.getAllValues()).extracting(ClaimTravelService.Request::profile)
                .containsExactly(ClaimArrivalProfile.BRACKEN_STANDING, ClaimArrivalProfile.ASTRAL_RETURN);
    }

    @Test
    void foliaAndUnsupportedServersCannotStartTheBridge() {
        bukkit.when(Bukkit::getName).thenReturn("Folia");
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isZero();
        bukkit.when(Bukkit::getName).thenReturn("Spigot");
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isZero();
        verify(travel, never()).prepare(any());
    }

    @Test
    void approvedLocationIsPreparedBeforeExplicitCommitAndCompletionWaitsForArrival() {
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isOne();
        ArgumentCaptor<ClaimTravelService.Request> request = ArgumentCaptor.forClass(ClaimTravelService.Request.class);
        verify(travel).prepare(request.capture());
        assertThat(request.getValue().player()).isSameAs(player);
        assertThat(request.getValue().requested()).isEqualTo(requested);
        assertThat(request.getValue().cause()).isEqualTo(PlayerTeleportEvent.TeleportCause.PLUGIN);
        assertThat(request.getValue().profile()).isEqualTo(ClaimArrivalProfile.UNDERDARK_TO_OVERWORLD);
        assertThat(dispatched).isEmpty();

        approval.complete(Optional.of(prepared));
        assertThat(dispatched).containsExactly("execute as " + PLAYER_ID
                + " in minecraft:overworld positioned 48.5 -59.0 -48.5 rotated 90.0 12.0"
                + " run function bracken:travel/prepare {request:41}");
        verify(travel, never()).commit(any());

        assertThat(bridge.commit(source(approved), 41)).isOne();
        verify(travel).commit(prepared);
        assertThat(dispatched).hasSize(1);
        when(player.getLocation()).thenReturn(approved);
        when(player.getWorld()).thenReturn(overworld);
        when(travel.valid(prepared)).thenReturn(false);
        completion.complete(true);
        assertThat(dispatched).hasSize(2).last().isEqualTo("execute as " + PLAYER_ID
                + " at @s run function bracken:travel/complete {request:41,status:1}");
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isZero();
    }

    @Test
    void commitRequiresMatchingSequenceApprovedPositionAndLivePreparedToken() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        approval.complete(Optional.of(prepared));
        assertThat(bridge.commit(source(approved), 40)).isZero();
        assertThat(bridge.commit(source(requested), 41)).isZero();
        CommandSourceStack direct = source(approved);
        when(direct.getSender()).thenReturn(player);
        assertThat(bridge.commit(direct, 41)).isZero();
        verify(travel, never()).commit(any());
        when(travel.valid(prepared)).thenReturn(false);
        assertThat(bridge.commit(source(approved), 41)).isZero();
        verify(travel, never()).commit(any());
        assertThat(dispatched.getLast()).contains("{request:41,status:0}");
    }

    @Test
    void duplicateAndStaleRequestsNeverPrepareAgain() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isZero();
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 40)).isZero();
        verify(travel, times(1)).prepare(any());
        approval.complete(Optional.of(prepared));
        bridge.commit(source(approved), 41);
        assertThat(bridge.commit(source(approved), 41)).isZero();
        verify(travel, times(1)).commit(prepared);
    }

    @Test
    void supersededApprovalCannotPrepareBlocksOrCancelTheNewRequest() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        CompletableFuture<Optional<ClaimTravelService.Prepared>> newer = new CompletableFuture<>();
        when(travel.prepare(any())).thenReturn(newer);
        bridge.request(source(requested), "underdark_to_overworld", 42);
        approval.complete(Optional.of(prepared));
        timeouts.getFirst().run();
        assertThat(dispatched).isEmpty();
        verify(travel, times(1)).cancel(any(ClaimTravelService.Request.class));
        newer.complete(Optional.of(prepared));
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.getFirst()).contains("{request:42}");
    }

    @Test
    void rejectedOrFailedTeleportSendsCancellationWithoutCompletionEffects() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        approval.complete(Optional.of(prepared));
        bridge.commit(source(approved), 41);
        completion.complete(false);
        assertThat(dispatched.getLast()).contains("{request:41,status:0}");
        assertThat(dispatched).noneMatch(command -> command.contains("status:1"));
    }

    @Test
    void completionAtADifferentPositionCannotRunArrivalEffects() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        approval.complete(Optional.of(prepared));
        bridge.commit(source(approved), 41);
        completion.complete(true);
        assertThat(dispatched.getLast()).contains("{request:41,status:0}");
        assertThat(dispatched).noneMatch(command -> command.contains("status:1"));
    }

    @Test
    void timeoutCancelsPendingPreparationAndIgnoresItsLateApproval() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        assertThat(timeouts).hasSize(1);
        timeouts.getFirst().run();
        verify(travel).cancel(any(ClaimTravelService.Request.class));
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.getFirst()).contains("{request:41,status:0}");
        approval.complete(Optional.of(prepared));
        assertThat(dispatched).hasSize(1);
        assertThat(bridge.commit(source(approved), 41)).isZero();
    }

    @Test
    void reloadClearsPendingAndSequenceHistoryWhileCloseRefusesFutureRequests() {
        bridge.request(source(requested), "underdark_to_overworld", 41);
        bridge.reset();
        approval.complete(Optional.of(prepared));
        assertThat(dispatched).noneMatch(command -> command.contains("travel/prepare"));
        when(travel.prepare(any())).thenReturn(new CompletableFuture<>());
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 41)).isOne();
        bridge.close();
        assertThat(bridge.request(source(requested), "underdark_to_overworld", 42)).isZero();
        assertThat(bridge.commit(source(approved), 41)).isZero();
    }

    private CommandSourceStack source(Location location) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(console);
        when(source.getExecutor()).thenReturn(player);
        when(source.getLocation()).thenReturn(location);
        return source;
    }
}
