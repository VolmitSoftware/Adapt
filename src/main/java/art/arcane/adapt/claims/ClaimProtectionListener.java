package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimAction;
import art.arcane.adapt.api.claims.ClaimAttempt;
import art.arcane.adapt.api.claims.ClaimAttemptResult;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class ClaimProtectionListener implements Listener {
    private final ClaimsService claims;
    private final ThreadLocal<Event> externalProbe = new ThreadLocal<>();
    private final Map<ClaimBlock, LinkedBreak> linkedBreaks = new ConcurrentHashMap<>();
    private final Map<ClaimBlock, AnchorBreak> anchorBreaks = new ConcurrentHashMap<>();

    public ClaimProtectionListener(ClaimsService claims) {
        this.claims = Objects.requireNonNull(claims);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!claims.enabled() || externalProbe.get() == event || event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (claims.relocating(player)) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        ClaimBlock position = ClaimsService.block(block);
        Block linked = linkedCandidate(block);
        if (linked != null && (protectedBlock(block) || protectedBlock(linked))) {
            if (!J.isOwnedByCurrentRegion(linked.getLocation())) {
                event.setCancelled(true);
                return;
            }
            if (!matchingPair(block, linked)) {
                linked = null;
            }
        } else {
            linked = null;
        }
        if (linked != null && !ProtectionEventProbe.isActive(event)
                && !externalAllows(new BlockBreakEvent(linked, player))) {
            event.setCancelled(true);
            return;
        }
        if (claims.bypass(player)) {
            observeBreak(event, block, linked, false);
            return;
        }
        if (ownedAnchor(player, position)) {
            event.setCancelled(true);
            feedback(player, AdaptLanguage.text(player.getUniqueId(), ClaimsMessages.ANCHOR_CONFIRM, MessageArgs.empty()));
            return;
        }
        boolean editable = claims.canEdit(player, block.getLocation());
        boolean linkedEditable = linked == null || claims.canEdit(player, linked.getLocation());
        if (editable && linkedEditable) {
            observeBreak(event, block, linked, false);
            return;
        }
        if (ProtectionEventProbe.isActive(event) || player.getGameMode() != GameMode.SURVIVAL) {
            event.setCancelled(true);
            return;
        }
        BlockBreakEvent probe = new BlockBreakEvent(block, player);
        if (!externalAllows(probe)) {
            event.setCancelled(true);
            return;
        }
        List<ClaimAttempt> attempts = new ArrayList<>(2);
        long now = System.currentTimeMillis();
        if (!editable) {
            attempts.add(new ClaimAttempt(player.getUniqueId(), position, ClaimAction.BREAK,
                    block.getBlockData().getAsString(), now, true));
        }
        if (!linkedEditable) {
            attempts.add(new ClaimAttempt(player.getUniqueId(), ClaimsService.block(linked), ClaimAction.BREAK,
                    linked.getBlockData().getAsString(), now, true));
        }
        List<ClaimAttemptResult> results = attempts.size() == 1
                ? List.of(claims.manager().attempt(attempts.getFirst())) : claims.manager().attemptAll(attempts);
        feedback(player, ClaimAction.BREAK, results.getFirst());
        if (!allAllowed(results)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }
        observeBreak(event, block, linked, !linkedEditable);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnchorReplacement(BlockPlaceEvent event) {
        if (!claims.enabled() || ProtectionEventProbe.isActive(event)) {
            return;
        }
        List<BlockState> replaced = event instanceof BlockMultiPlaceEvent multi
                ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
        for (BlockState state : replaced) {
            ClaimBlock position = ClaimsService.block(state.getLocation());
            AnchorBreak broken = anchorBreaks.get(position);
            if (broken != null && !broken.event().isCancelled()
                    && !broken.blockState().equals(state.getBlockData().getAsString())) {
                releaseAnchor(broken);
                anchorBreaks.remove(position, broken);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!claims.enabled() || externalProbe.get() == event || event.isCancelled() || !event.canBuild()) {
            return;
        }
        Player player = event.getPlayer();
        if (claims.relocating(player)) {
            event.setCancelled(true);
            return;
        }
        List<BlockState> replaced = event instanceof BlockMultiPlaceEvent multi
                ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
        List<ClaimAttempt> attempts = new ArrayList<>(replaced.size());
        long now = System.currentTimeMillis();
        for (BlockState state : replaced) {
            if (!claims.canEdit(player, state.getLocation())) {
                attempts.add(new ClaimAttempt(player.getUniqueId(), ClaimsService.block(state.getLocation()),
                        ClaimAction.PLACE, state.getBlockData().getAsString(), now, true));
            }
        }
        if (attempts.isEmpty()) {
            observePlacement(event, replaced);
            return;
        }
        if (ProtectionEventProbe.isActive(event) || player.getGameMode() != GameMode.SURVIVAL) {
            event.setCancelled(true);
            return;
        }
        BlockPlaceEvent probe = event instanceof BlockMultiPlaceEvent
                ? new BlockMultiPlaceEvent(replaced, event.getBlockAgainst(), event.getItemInHand(), player,
                event.canBuild(), event.getHand())
                : new BlockPlaceEvent(event.getBlock(), event.getBlockReplacedState(), event.getBlockAgainst(),
                event.getItemInHand(), player, event.canBuild(), event.getHand());
        if (!externalAllows(probe)) {
            event.setCancelled(true);
            return;
        }
        List<ClaimAttemptResult> results = claims.manager().attemptAll(attempts);
        boolean allowed = true;
        for (ClaimAttemptResult result : results) {
            allowed &= result.allowed();
        }
        feedback(player, ClaimAction.PLACE, results.getFirst());
        if (!allowed) {
            event.setCancelled(true);
            return;
        }
        observePlacement(event, replaced);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonProtected(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonProtected(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (protectedBlock(event.getBlock()) || protectedBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (attributedEditDenied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (attributedEditDenied(event.getPlayer(), event.getBlock().getLocation())
                || attributedChangesDenied(event.getPlayer(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTree(StructureGrowEvent event) {
        if (attributedEditDenied(event.getPlayer(), event.getLocation())
                || attributedChangesDenied(event.getPlayer(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (containsProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityEdit(EntityChangeBlockEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        Block block = event.getBlock();
        if (!protectedBlock(block)) {
            return;
        }
        ClaimBlock position = ClaimsService.block(block);
        LinkedBreak linked = linkedBreaks.get(position);
        if (linked == null || anchor(position) || linked.event().isCancelled() || !ownership(position).equals(linked.ownership())
                || !linked.resistanceGranted() && !claims.canEdit(linked.event().getPlayer(), block.getLocation())
                || !J.isOwnedByCurrentRegion(linked.primary().getLocation())
                || linked.primaryState().equals(linked.primary().getBlockData().getAsString())
                || !linked.secondaryState().equals(block.getBlockData().getAsString())) {
            event.setCancelled(true);
            return;
        }
        linkedBreaks.remove(position, linked);
        J.runAt(block.getLocation(), () -> {
            if (!event.isCancelled() && !linked.secondaryState().equals(block.getBlockData().getAsString())) {
                claims.manager().blockChanged(position);
            }
        }, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        if (containsProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoilChange(MoistureChangeEvent event) {
        if (event.getNewState().getType() != event.getBlock().getType() && protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getBlockData() instanceof Directional directional
                && protectedBlock(event.getBlock().getRelative(directional.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (claims.relocating(event.getPlayer())
                || protectedBlock(event.getBlock()) && !claims.canEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (claims.relocating(event.getPlayer())
                || protectedBlock(event.getBlock()) && !claims.canEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!claims.canEdit(event.getPlayer(), event.getHarvestedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !protectedBlock(block) || claims.canEdit(event.getPlayer(), block.getLocation())) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL && block.getType() == Material.FARMLAND) {
            event.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null) {
            return;
        }
        Material item = event.getItem().getType();
        String name = item.name();
        if (name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SHOVEL")
                || item == Material.BONE_MEAL || item == Material.HONEYCOMB || item == Material.SHEARS
                || item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    private boolean externalAllows(Event event) {
        Event previous = externalProbe.get();
        externalProbe.set(event);
        try {
            ProtectionEventProbe.dispatch(event);
            if (event instanceof BlockBreakEvent broken) {
                return !broken.isCancelled();
            }
            BlockPlaceEvent placed = (BlockPlaceEvent) event;
            return !placed.isCancelled() && placed.canBuild();
        } finally {
            if (previous == null) {
                externalProbe.remove();
            } else {
                externalProbe.set(previous);
            }
        }
    }

    private void observeBreak(BlockBreakEvent event, Block block, Block linked, boolean resistanceGranted) {
        if (ProtectionEventProbe.isActive(event) || !protectedBlock(block) && linked == null) {
            return;
        }
        ClaimBlock position = ClaimsService.block(block);
        String previous = block.getBlockData().getAsString();
        AnchorBreak brokenAnchor = pendingAnchor(event, position, previous);
        if (brokenAnchor != null) {
            anchorBreaks.put(position, brokenAnchor);
        }
        ClaimBlock linkedPosition = linked == null ? null : ClaimsService.block(linked);
        LinkedBreak authorization = linked == null ? null : new LinkedBreak(event, block, previous,
                linked.getBlockData().getAsString(), ownership(linkedPosition), resistanceGranted);
        if (authorization != null) {
            linkedBreaks.put(linkedPosition, authorization);
        }
        boolean scheduled = J.runAt(block.getLocation(), () -> {
            if (authorization != null) {
                linkedBreaks.remove(linkedPosition, authorization);
            }
            if (brokenAnchor != null) {
                anchorBreaks.remove(position, brokenAnchor);
            }
            if (claims.enabled() && !event.isCancelled() && !previous.equals(block.getBlockData().getAsString())) {
                claims.manager().blockChanged(position);
                if (brokenAnchor != null) {
                    releaseAnchor(brokenAnchor);
                }
            }
        }, 1);
        if (!scheduled) {
            if (authorization != null) {
                linkedBreaks.remove(linkedPosition, authorization);
            }
            if (brokenAnchor != null) {
                anchorBreaks.remove(position, brokenAnchor);
                event.setCancelled(true);
            }
        }
    }

    private AnchorBreak pendingAnchor(BlockBreakEvent event, ClaimBlock position, String previous) {
        ClaimSnapshot snapshot = claims.manager().snapshot();
        ClaimCity city = snapshot.cityAt(position.chunk()).orElse(null);
        if (city == null) {
            return null;
        }
        if (city.anchor().equals(position)) {
            return new AnchorBreak(event, previous, city, null);
        }
        ClaimPlot plot = snapshot.plotAt(position.chunk()).orElse(null);
        return plot != null && plot.anchor().equals(position) ? new AnchorBreak(event, previous, city, plot) : null;
    }

    private void releaseAnchor(AnchorBreak broken) {
        if (broken.plot() == null) {
            claims.manager().destroyAnchorIfUnchanged(broken.city());
        } else {
            claims.manager().destroyAnchorIfUnchanged(broken.plot());
        }
    }

    private Block linkedCandidate(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Bed bed) {
            return block.getRelative(bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing());
        }
        if (data instanceof Bisected bisected && !(data instanceof Stairs) && !(data instanceof TrapDoor)) {
            return block.getRelative(bisected.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
        }
        return null;
    }

    private boolean matchingPair(Block primary, Block secondary) {
        if (primary.getType() != secondary.getType() || anchor(ClaimsService.block(secondary))) {
            return false;
        }
        BlockData expected = primary.getBlockData().clone();
        BlockData actual = secondary.getBlockData();
        if (expected instanceof Bed bed && actual instanceof Bed) {
            bed.setPart(bed.getPart() == Bed.Part.HEAD ? Bed.Part.FOOT : Bed.Part.HEAD);
        } else if (expected instanceof Bisected bisected && actual instanceof Bisected) {
            bisected.setHalf(bisected.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
        } else {
            return false;
        }
        if (expected instanceof Waterlogged water && actual instanceof Waterlogged other) {
            water.setWaterlogged(other.isWaterlogged());
        }
        return expected.getAsString().equals(actual.getAsString());
    }

    private ClaimOwnership ownership(ClaimBlock block) {
        ClaimSnapshot snapshot = claims.manager().snapshot();
        ClaimCity city = snapshot.cityAt(block.chunk()).orElse(null);
        ClaimPlot plot = snapshot.plotAt(block.chunk()).orElse(null);
        return new ClaimOwnership(city == null ? null : city.id(), plot == null ? null : plot.owner());
    }

    private boolean anchor(ClaimBlock block) {
        ClaimSnapshot snapshot = claims.manager().snapshot();
        ClaimCity city = snapshot.cityAt(block.chunk()).orElse(null);
        ClaimPlot plot = snapshot.plotAt(block.chunk()).orElse(null);
        return city != null && city.anchor().equals(block) || plot != null && plot.anchor().equals(block);
    }

    private boolean allAllowed(List<ClaimAttemptResult> results) {
        for (ClaimAttemptResult result : results) {
            if (!result.allowed()) {
                return false;
            }
        }
        return true;
    }

    private void observePlacement(BlockPlaceEvent event, List<BlockState> replaced) {
        if (ProtectionEventProbe.isActive(event)) {
            return;
        }
        for (BlockState state : replaced) {
            if (!protectedBlock(state.getBlock())) {
                continue;
            }
            ClaimBlock position = ClaimsService.block(state.getLocation());
            String previous = state.getBlockData().getAsString();
            J.runAt(state.getLocation(), () -> {
                if (!event.isCancelled() && !previous.equals(state.getBlock().getBlockData().getAsString())) {
                    claims.manager().blockChanged(position);
                }
            }, 1);
        }
    }

    private boolean ownedAnchor(Player player, ClaimBlock block) {
        ClaimCity city = claims.manager().snapshot().cityAt(block.chunk()).orElse(null);
        if (city == null) {
            return false;
        }
        if (city.anchor().equals(block)) {
            return city.founder().equals(player.getUniqueId());
        }
        ClaimPlot plot = claims.manager().snapshot().plotAt(block.chunk()).orElse(null);
        return plot != null && plot.anchor().equals(block) && plot.owner().equals(player.getUniqueId());
    }

    private boolean pistonProtected(Block piston, List<Block> moved, BlockFace direction) {
        if (protectedBlock(piston) || protectedBlock(piston.getRelative(direction))) {
            return true;
        }
        for (Block block : moved) {
            if (protectedBlock(block) || protectedBlock(block.getRelative(direction))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsProtected(List<BlockState> blocks) {
        for (BlockState block : blocks) {
            if (protectedBlock(block.getBlock())) {
                return true;
            }
        }
        return false;
    }

    private boolean attributedChangesDenied(Player player, List<BlockState> blocks) {
        for (BlockState block : blocks) {
            if (attributedEditDenied(player, block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private boolean attributedEditDenied(Player player, Location location) {
        return claims.enabled() && claims.manager().snapshot().cityAt(ClaimsService.chunk(location)).isPresent()
                && (player == null || !claims.canEdit(player, location));
    }

    private boolean protectedBlock(Block block) {
        return claims.enabled() && claims.manager().snapshot().cityAt(ClaimsService.block(block).chunk()).isPresent();
    }

    private void feedback(Player player, ClaimAction action, ClaimAttemptResult result) {
        LanguageAudience.run(player.getUniqueId(), () -> feedback(player, AdaptLanguage.text(ClaimsMessages.RAID_PROGRESS,
                trusted("action", AdaptLanguage.text(action == ClaimAction.BREAK
                        ? ClaimsMessages.BREAK_ACTION : ClaimsMessages.PLACE_ACTION)),
                trusted("current", Integer.toString((int) Math.round(result.progress() * result.required()))),
                trusted("required", Integer.toString(result.required())))));
    }

    private void feedback(Player player, String message) {
        player.sendActionBar(message);
    }

    private record ClaimOwnership(UUID city, UUID personalOwner) {
    }

    private record LinkedBreak(BlockBreakEvent event, Block primary, String primaryState,
                               String secondaryState, ClaimOwnership ownership, boolean resistanceGranted) {
    }

    private record AnchorBreak(BlockBreakEvent event, String blockState, ClaimCity city, ClaimPlot plot) {
    }
}
