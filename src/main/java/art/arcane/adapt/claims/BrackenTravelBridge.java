package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BrackenTravelBridge implements Listener, AutoCloseable {
    private static final int TIMEOUT_TICKS = 200;

    private final Adapt plugin;
    private final ClaimTravelService travel;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sequences = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public BrackenTravelBridge(Adapt plugin, ClaimTravelService travel) {
        this.plugin = Objects.requireNonNull(plugin);
        this.travel = Objects.requireNonNull(travel);
    }

    public static void register(Adapt plugin) {
        if (!supported()) {
            return;
        }
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command().build()));
    }

    @Override
    public void close() {
        closed = true;
        reset();
    }

    public void reset() {
        for (Pending transfer : pending.values()) {
            finish(transfer, false);
        }
        sequences.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
        sequences.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancel(event.getEntity());
    }

    int request(CommandSourceStack source, String routeId, int sequence) {
        BrackenTravelRoute route = BrackenTravelRoute.find(routeId).orElse(null);
        if (closed || !supported() || !authorized(source) || route == null || sequence < 1) {
            return 0;
        }
        Player player = (Player) source.getExecutor();
        Location requested = source.getLocation();
        if (!available(player) || !route.accepts(player, requested)) {
            return 0;
        }
        UUID playerId = player.getUniqueId();
        Integer previousSequence = sequences.get(playerId);
        if (previousSequence != null && sequence <= previousSequence) {
            return 0;
        }
        sequences.put(playerId, sequence);
        cancel(player);
        ClaimTravelService.Request request = new ClaimTravelService.Request(player, requested,
                PlayerTeleportEvent.TeleportCause.PLUGIN, route.profile());
        Pending transfer = new Pending(request, new RouteRequest(route, sequence));
        pending.put(playerId, transfer);
        if (!J.runEntity(player, () -> finish(transfer, false), TIMEOUT_TICKS)) {
            abandon(transfer);
            return 0;
        }
        try {
            travel.prepare(request).whenComplete((prepared, failure) -> {
                if (!J.runEntity(player, () -> prepared(transfer, prepared, failure))) {
                    abandon(transfer);
                }
            });
        } catch (RuntimeException failure) {
            failed(transfer, failure);
            return 0;
        }
        return 1;
    }

    int commit(CommandSourceStack source, int sequence) {
        if (closed || !supported() || !authorized(source)) {
            return 0;
        }
        Player player = (Player) source.getExecutor();
        Pending transfer = pending.get(player.getUniqueId());
        if (transfer == null || transfer.sequence != sequence || transfer.prepared == null || transfer.committing
                || !samePosition(transfer.prepared.destination(), source.getLocation())) {
            return 0;
        }
        if (!available(player) || !travel.valid(transfer.prepared)) {
            finish(transfer, false);
            return 0;
        }
        transfer.committing = true;
        try {
            travel.commit(transfer.prepared).whenComplete((success, failure) -> {
                if (!J.runEntity(player, () -> committed(transfer, Boolean.TRUE.equals(success), failure))) {
                    abandon(transfer);
                }
            });
        } catch (RuntimeException failure) {
            failed(transfer, failure);
            return 0;
        }
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("brackentravel")
                .then(Commands.literal("request")
                        .then(Commands.argument("route", StringArgumentType.word())
                                .then(Commands.argument("request", IntegerArgumentType.integer(1))
                                        .executes(BrackenTravelBridge::executeRequest))))
                .then(Commands.literal("commit")
                        .then(Commands.argument("request", IntegerArgumentType.integer(1))
                                .executes(BrackenTravelBridge::executeCommit)));
    }

    private static int executeRequest(CommandContext<CommandSourceStack> context) {
        ClaimsService service = ClaimsService.current();
        return service == null ? 0 : service.brackenTravel().request(context.getSource(),
                StringArgumentType.getString(context, "route"), IntegerArgumentType.getInteger(context, "request"));
    }

    private static int executeCommit(CommandContext<CommandSourceStack> context) {
        ClaimsService service = ClaimsService.current();
        return service == null ? 0 : service.brackenTravel().commit(context.getSource(),
                IntegerArgumentType.getInteger(context, "request"));
    }

    private static boolean supported() {
        return !J.isFoliaThreading() && ("Paper".equals(Bukkit.getName()) || "Purpur".equals(Bukkit.getName()));
    }

    private static boolean authorized(CommandSourceStack source) {
        return source != null && source.getSender() instanceof ConsoleCommandSender
                && source.getExecutor() instanceof Player;
    }

    private static boolean available(Player player) {
        return player.isOnline() && !player.isDead();
    }

    private static boolean samePosition(Location first, Location second) {
        return second != null && first.getWorld() == second.getWorld() && first.distanceSquared(second) < 0.000001;
    }

    private void prepared(Pending transfer, Optional<ClaimTravelService.Prepared> prepared, Throwable failure) {
        if (!current(transfer)) {
            return;
        }
        if (failure != null) {
            failed(transfer, failure);
            return;
        }
        if (prepared.isEmpty()) {
            finish(transfer, false);
            return;
        }
        ClaimTravelService.Prepared approval = prepared.get();
        if (!approval.playerId().equals(transfer.request.player().getUniqueId())
                || !transfer.route.accepts(transfer.request.player(), approval.destination()) || !travel.valid(approval)) {
            finish(transfer, false);
            return;
        }
        transfer.prepared = approval;
        Location approved = approval.destination();
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as " + approval.playerId()
                    + " in " + approved.getWorld().getKey()
                    + " positioned " + approved.getX() + " " + approved.getY() + " " + approved.getZ()
                    + " rotated " + approved.getYaw() + " " + approved.getPitch()
                    + " run function bracken:travel/prepare {request:" + transfer.sequence + "}");
        } catch (RuntimeException dispatchFailure) {
            failed(transfer, dispatchFailure);
        }
    }

    private void committed(Pending transfer, boolean success, Throwable failure) {
        if (!current(transfer)) {
            return;
        }
        if (failure != null) {
            failed(transfer, failure);
            return;
        }
        Player player = transfer.request.player();
        finish(transfer, success && available(player) && samePosition(transfer.prepared.destination(), player.getLocation()));
    }

    private void finish(Pending transfer, boolean success) {
        if (!pending.remove(transfer.request.player().getUniqueId(), transfer)) {
            return;
        }
        if (!success) {
            travel.cancel(transfer.request);
        }
        Player player = transfer.request.player();
        if (closed || !available(player)) {
            return;
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId()
                    + " at @s run function bracken:travel/complete {request:" + transfer.sequence
                    + ",status:" + (success ? 1 : 0) + "}");
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not deliver Bracken travel result for " + player.getUniqueId(), failure);
        }
    }

    private void cancel(Player player) {
        Pending transfer = pending.get(player.getUniqueId());
        if (transfer != null) {
            abandon(transfer);
        }
    }

    private void abandon(Pending transfer) {
        if (pending.remove(transfer.request.player().getUniqueId(), transfer)) {
            travel.cancel(transfer.request);
        }
    }

    private boolean current(Pending transfer) {
        return !closed && pending.get(transfer.request.player().getUniqueId()) == transfer;
    }

    private void failed(Pending transfer, Throwable failure) {
        plugin.getLogger().log(Level.SEVERE,
                "Bracken travel failed for " + transfer.request.player().getUniqueId(), failure);
        finish(transfer, false);
    }

    private static final class Pending {
        private final ClaimTravelService.Request request;
        private final BrackenTravelRoute route;
        private final int sequence;
        private ClaimTravelService.Prepared prepared;
        private boolean committing;

        private Pending(ClaimTravelService.Request request, RouteRequest routeRequest) {
            this.request = request;
            this.route = routeRequest.route();
            this.sequence = routeRequest.sequence();
        }
    }

    private record RouteRequest(BrackenTravelRoute route, int sequence) {
    }
}
