package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimAttempt;
import art.arcane.adapt.api.claims.ClaimAttemptResult;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimProtectionListenerTest extends AdaptTestBase {
    private final UUID founder = UUID.randomUUID();
    private final UUID attacker = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private ClaimsService claims;
    private ClaimManager manager;
    private ClaimProtectionListener listener;
    private Player player;
    private World world;
    private Block target;
    private PluginManager plugins;

    @Test
    void aMemberCanCreateAPortalOnlyWhenEveryStagedBlockIsEditable() {
        BlockState first = mock(BlockState.class);
        BlockState second = mock(BlockState.class);
        Location firstLocation = new Location(world, 1609, 64, 1608);
        Location secondLocation = new Location(world, 1610, 64, 1608);
        when(first.getLocation()).thenReturn(firstLocation);
        when(second.getLocation()).thenReturn(secondLocation);
        when(claims.canPrepareArrival(player, firstLocation)).thenReturn(true);
        when(claims.canPrepareArrival(player, secondLocation)).thenReturn(true);
        PortalCreateEvent allowed = new PortalCreateEvent(List.of(first, second), world, player,
                PortalCreateEvent.CreateReason.NETHER_PAIR);
        listener.onPortalCreate(allowed);
        assertThat(allowed.isCancelled()).isFalse();
        when(claims.canPrepareArrival(player, secondLocation)).thenReturn(false);
        PortalCreateEvent denied = new PortalCreateEvent(List.of(first, second), world, player,
                PortalCreateEvent.CreateReason.NETHER_PAIR);
        listener.onPortalCreate(denied);
        assertThat(denied.isCancelled()).isTrue();
    }

    @BeforeEach
    void prepare() {
        claims = mock(ClaimsService.class);
        manager = mock(ClaimManager.class);
        player = mock(Player.class);
        world = mock(World.class);
        plugins = mock(PluginManager.class);
        when(world.getUID()).thenReturn(worldId);
        when(player.getUniqueId()).thenReturn(attacker);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(claims.enabled()).thenReturn(true);
        when(claims.manager()).thenReturn(manager);
        ClaimBlock anchor = new ClaimBlock(worldId, 1608, 64, 1608);
        ClaimCity city = new ClaimCity(UUID.randomUUID(), "Oakwatch", founder, anchor, Set.of(),
                ClaimSnapshot.footprint(anchor.chunk()), new ClaimRanks(true, 1, 0, 0, false), 0);
        when(manager.snapshot()).thenReturn(new ClaimSnapshot(Map.of(city.id(), city), Map.of()));
        target = block(1609, 64, 1608, "minecraft:stone");
        listener = new ClaimProtectionListener(claims);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof BlockBreakEvent broken) {
                listener.onBreak(broken);
            } else if (event instanceof BlockPlaceEvent placed) {
                listener.onPlace(placed);
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
    }

    @Test
    void completedSurvivalBreakCreditsOnceAndSuppressesDropsUntilThreshold() {
        when(manager.attempt(any())).thenReturn(new ClaimAttemptResult(false, true, 1.0 / 16, 16));
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        event.setExpToDrop(5);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            listener.onBreak(event);
        }
        assertThat(event.isCancelled()).isTrue();
        assertThat(event.isDropItems()).isFalse();
        assertThat(event.getExpToDrop()).isZero();
        ArgumentCaptor<ClaimAttempt> attempted = ArgumentCaptor.forClass(ClaimAttempt.class);
        verify(manager).attempt(attempted.capture());
        assertThat(attempted.getValue().blockState()).isEqualTo("minecraft:stone");
    }

    @Test
    void externalDenialAfterClaimsHandlerEarnsNoProgress() {
        doAnswer(invocation -> {
            BlockBreakEvent probe = invocation.getArgument(0);
            listener.onBreak(probe);
            probe.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any(Event.class));
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            listener.onBreak(event);
        }
        assertThat(event.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
    }

    @Test
    void alreadyDeniedBreakEarnsNoProgress() {
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        event.setCancelled(true);
        listener.onBreak(event);
        verify(manager, never()).attempt(any());
    }

    @Test
    void protectionProbesNeverAdvanceResistance() {
        BlockBreakEvent probe = new BlockBreakEvent(target, player);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            ProtectionEventProbe.dispatch(probe);
        }
        assertThat(probe.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
    }

    @Test
    void creativeModeDoesNotGrantRaidBypass() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        listener.onBreak(event);
        assertThat(event.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
    }

    @Test
    void explicitBypassLeavesCreativeBreakUncancelled() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(claims.bypass(player)).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            listener.onBreak(event);
        }
        assertThat(event.isCancelled()).isFalse();
        verify(manager, never()).attempt(any());
    }

    @Test
    void founderMustConfirmDismantlingEvenWithNormalEditPermission() {
        when(player.getUniqueId()).thenReturn(founder);
        Block anchor = block(1608, 64, 1608, "minecraft:lodestone");
        when(claims.canEdit(player, anchor.getLocation())).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(anchor, player);
        listener.onBreak(event);
        assertThat(event.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
    }

    @Test
    void lateCancellationPreservesAnchorAndRaidProgress() {
        when(manager.attempt(any())).thenReturn(new ClaimAttemptResult(true, true, 1, 16));
        Block anchor = block(1608, 64, 1608, "minecraft:lodestone");
        BlockBreakEvent event = new BlockBreakEvent(anchor, player);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
                delayed.add(invocation.getArgument(1));
                return true;
            });
            listener.onBreak(event);
            event.setCancelled(true);
            delayed.forEach(Runnable::run);
        }
        assertThat(delayed).hasSize(1);
        verify(manager, never()).blockChanged(any());
        verify(manager, never()).destroyAnchor(any());
        verify(manager, never()).destroyAnchorIfUnchanged(any(ClaimCity.class));
    }

    @Test
    void confirmedActualAnchorBreakReleasesOwnership() {
        when(manager.attempt(any())).thenReturn(new ClaimAttemptResult(true, true, 1, 16));
        Block anchor = block(1608, 64, 1608, "minecraft:lodestone");
        BlockBreakEvent event = new BlockBreakEvent(anchor, player);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
                delayed.add(invocation.getArgument(1));
                return true;
            });
            listener.onBreak(event);
            BlockData air = mock(BlockData.class);
            when(air.getAsString()).thenReturn("minecraft:air");
            when(anchor.getBlockData()).thenReturn(air);
            delayed.forEach(Runnable::run);
        }
        assertThat(event.isCancelled()).isFalse();
        verify(manager).blockChanged(new ClaimBlock(worldId, 1608, 64, 1608));
        verify(manager).destroyAnchorIfUnchanged(any(ClaimCity.class));
    }

    @Test
    void multiBlockPlacementsApplyOneBatchToEveryTarget() {
        Block upper = block(1609, 65, 1608, "minecraft:oak_door[half=upper]");
        BlockState lowerState = replaced(target, "minecraft:air");
        BlockState upperState = replaced(upper, "minecraft:air");
        when(manager.attemptAll(anyList())).thenReturn(List.of(
                new ClaimAttemptResult(false, true, 1.0 / 64, 64),
                new ClaimAttemptResult(false, true, 1.0 / 64, 64)));
        BlockMultiPlaceEvent event = new BlockMultiPlaceEvent(List.of(lowerState, upperState), target,
                mock(ItemStack.class), player, true, EquipmentSlot.HAND);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            listener.onPlace(event);
        }
        assertThat(event.isCancelled()).isTrue();
        ArgumentCaptor<List<ClaimAttempt>> attempted = ArgumentCaptor.forClass(List.class);
        verify(manager).attemptAll(attempted.capture());
        assertThat(attempted.getValue()).hasSize(2);
        assertThat(attempted.getValue()).allSatisfy(attempt -> assertThat(attempt.blockState()).isEqualTo("minecraft:air"));
        assertThat(attempted.getValue().getFirst().timestamp()).isEqualTo(attempted.getValue().getLast().timestamp());
    }

    @Test
    void deniedPlacementNeverDebitsProgress() {
        BlockPlaceEvent event = new BlockPlaceEvent(target, replaced(target, "minecraft:air"), target,
                mock(ItemStack.class), player, false, EquipmentSlot.HAND);
        listener.onPlace(event);
        verify(manager, never()).attemptAll(anyList());
    }

    @Test
    void pistonsCannotPushFromOutsideIntoClaims() {
        Block piston = block(1582, 64, 1608, "minecraft:piston");
        Block moved = block(1583, 64, 1608, "minecraft:stone");
        Block destination = block(1584, 64, 1608, "minecraft:air");
        when(piston.getRelative(BlockFace.EAST)).thenReturn(moved);
        when(moved.getRelative(BlockFace.EAST)).thenReturn(destination);
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(piston, List.of(moved), BlockFace.EAST);
        listener.onPistonExtend(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void explosionsRemoveOnlyClaimedBlocksFromTheirDamageList() {
        Block outside = block(0, 64, 0, "minecraft:stone");
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        List<Block> affected = new ArrayList<>(List.of(target, outside));
        when(event.blockList()).thenReturn(affected);
        listener.onExplosion(event);
        assertThat(affected).containsExactly(outside);
    }

    @Test
    void fireFluidsAndEntityEditsCannotBypassResistance() {
        BlockBurnEvent fire = new BlockBurnEvent(target);
        Block outside = block(1583, 64, 1608, "minecraft:water");
        BlockFromToEvent fluid = new BlockFromToEvent(outside, target);
        EntityChangeBlockEvent entity = mock(EntityChangeBlockEvent.class);
        when(entity.getBlock()).thenReturn(target);
        listener.onBurn(fire);
        listener.onFlow(fluid);
        listener.onEntityEdit(entity);
        assertThat(fire.isCancelled()).isTrue();
        assertThat(fluid.isCancelled()).isTrue();
        verify(entity).setCancelled(true);
    }

    @Test
    void relocationCannotBeUsedToAttackWhileSafeDestinationLoads() {
        when(claims.relocating(player)).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        listener.onBreak(event);
        assertThat(event.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
    }

    @Test
    void attributedOwnerFertilizationAndTreeGrowthRequireEveryTargetToBeEditable() {
        Block other = block(1624, 64, 1608, "minecraft:grass_block");
        List<BlockState> changes = List.of(replaced(target, "minecraft:oak_log"), replaced(other, "minecraft:oak_leaves"));
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        when(claims.canEdit(player, other.getLocation())).thenReturn(true);
        BlockFertilizeEvent fertilize = new BlockFertilizeEvent(target, player, changes);
        StructureGrowEvent grow = new StructureGrowEvent(target.getLocation(), TreeType.TREE, true, player, changes);
        listener.onFertilize(fertilize);
        listener.onTree(grow);
        assertThat(fertilize.isCancelled()).isFalse();
        assertThat(grow.isCancelled()).isFalse();
        when(claims.canEdit(player, other.getLocation())).thenReturn(false);
        listener.onFertilize(fertilize);
        listener.onTree(grow);
        assertThat(fertilize.isCancelled()).isTrue();
        assertThat(grow.isCancelled()).isTrue();
    }

    @Test
    void naturalAndDispenserGrowthRemainBlockedEvenWhenTheOwnerCouldEdit() {
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        List<BlockState> changes = List.of(replaced(target, "minecraft:oak_log"));
        BlockFertilizeEvent fertilize = new BlockFertilizeEvent(target, null, changes);
        StructureGrowEvent grow = new StructureGrowEvent(target.getLocation(), TreeType.TREE, false, null, changes);
        listener.onFertilize(fertilize);
        listener.onTree(grow);
        assertThat(fertilize.isCancelled()).isTrue();
        assertThat(grow.isCancelled()).isTrue();
    }

    @Test
    void growthCannotStartFromAnotherOwnersPlotEvenWhenItsResultIsElsewhere() {
        Block allowed = block(1624, 64, 1608, "minecraft:grass_block");
        when(claims.canEdit(player, allowed.getLocation())).thenReturn(true);
        List<BlockState> changes = List.of(replaced(allowed, "minecraft:oak_leaves"));
        BlockFertilizeEvent fertilize = new BlockFertilizeEvent(target, player, changes);
        StructureGrowEvent grow = new StructureGrowEvent(target.getLocation(), TreeType.TREE, true, player, changes);
        listener.onFertilize(fertilize);
        listener.onTree(grow);
        assertThat(fertilize.isCancelled()).isTrue();
        assertThat(grow.isCancelled()).isTrue();
    }

    @Test
    void directOwnerIgnitionIsAllowedButOutsidersAndUnattributedFireAreBlocked() {
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        BlockIgniteEvent owned = new BlockIgniteEvent(target, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player);
        BlockIgniteEvent natural = new BlockIgniteEvent(target, BlockIgniteEvent.IgniteCause.LAVA, (Entity) null);
        listener.onIgnite(owned);
        listener.onIgnite(natural);
        assertThat(owned.isCancelled()).isFalse();
        assertThat(natural.isCancelled()).isTrue();
        when(claims.canEdit(player, target.getLocation())).thenReturn(false);
        listener.onIgnite(owned);
        assertThat(owned.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
    }

    @Test
    void linkedDoorHalfRequiresThePrimaryBreakToActuallySucceed() {
        Block upper = doorPair();
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        when(claims.canEdit(player, upper.getLocation())).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenReturn(true);
            listener.onBreak(event);
            BlockDestroyEvent early = destroy(upper);
            listener.onDestroy(early);
            assertThat(early.isCancelled()).isTrue();
            changeToAir(target);
            BlockDestroyEvent linked = destroy(upper);
            listener.onDestroy(linked);
            assertThat(linked.isCancelled()).isFalse();
            BlockDestroyEvent repeated = destroy(upper);
            listener.onDestroy(repeated);
            assertThat(repeated.isCancelled()).isTrue();
        }
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void externalDenialOfLinkedHalfStopsTheWholeOwnerBreak() {
        Block upper = doorPair();
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        when(claims.canEdit(player, upper.getLocation())).thenReturn(true);
        doAnswer(invocation -> {
            BlockBreakEvent probe = invocation.getArgument(0);
            if (probe.getBlock() == upper) {
                probe.setCancelled(true);
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            listener.onBreak(event);
        }
        assertThat(event.isCancelled()).isTrue();
        verify(manager, never()).attempt(any());
        verify(manager, never()).attemptAll(anyList());
    }

    @Test
    void latePrimaryCancellationDoesNotAuthorizeItsLinkedHalf() {
        Block upper = doorPair();
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        when(claims.canEdit(player, upper.getLocation())).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenReturn(true);
            listener.onBreak(event);
            event.setCancelled(true);
            changeToAir(target);
            BlockDestroyEvent linked = destroy(upper);
            listener.onDestroy(linked);
            assertThat(linked.isCancelled()).isTrue();
        }
    }

    @Test
    void linkedOwnerAuthorizationDoesNotSurviveLosingEditPermission() {
        Block upper = doorPair();
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        when(claims.canEdit(player, upper.getLocation())).thenReturn(true);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenReturn(true);
            listener.onBreak(new BlockBreakEvent(target, player));
            changeToAir(target);
            when(claims.canEdit(player, upper.getLocation())).thenReturn(false);
            BlockDestroyEvent linked = destroy(upper);
            listener.onDestroy(linked);
            assertThat(linked.isCancelled()).isTrue();
        }
    }

    @Test
    void pairedRaidCreditsBothPositionsAndNeedsBothThresholds() {
        Block upper = doorPair();
        when(manager.attemptAll(anyList())).thenReturn(List.of(new ClaimAttemptResult(true, true, 1, 16),
                new ClaimAttemptResult(false, true, 0.5, 32)));
        BlockBreakEvent event = new BlockBreakEvent(target, player);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            listener.onBreak(event);
        }
        assertThat(event.isCancelled()).isTrue();
        ArgumentCaptor<List<ClaimAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(manager).attemptAll(attempts.capture());
        assertThat(attempts.getValue()).extracting(ClaimAttempt::block).containsExactly(ClaimsService.block(target), ClaimsService.block(upper));
        BlockDestroyEvent linked = destroy(upper);
        listener.onDestroy(linked);
        assertThat(linked.isCancelled()).isTrue();
    }

    @Test
    void editableBedHalfCannotBypassResistanceOnTheAdjacentOwnersHalf() {
        Block foot = block(1615, 64, 1608, "minecraft:red_bed[part=foot,facing=east]");
        Block head = block(1616, 64, 1608, "minecraft:red_bed[part=head,facing=east]");
        Bed footData = mock(Bed.class);
        Bed headData = mock(Bed.class);
        Bed expected = mock(Bed.class);
        when(footData.getPart()).thenReturn(Bed.Part.FOOT);
        when(footData.getFacing()).thenReturn(BlockFace.EAST);
        when(footData.getAsString()).thenReturn("minecraft:red_bed[part=foot,facing=east]");
        when(footData.clone()).thenReturn(expected);
        when(expected.getPart()).thenReturn(Bed.Part.FOOT);
        when(expected.getAsString()).thenReturn("minecraft:red_bed[part=head,facing=east]");
        when(headData.getAsString()).thenReturn("minecraft:red_bed[part=head,facing=east]");
        when(foot.getType()).thenReturn(Material.RED_BED);
        when(head.getType()).thenReturn(Material.RED_BED);
        when(foot.getBlockData()).thenReturn(footData);
        when(head.getBlockData()).thenReturn(headData);
        when(foot.getRelative(BlockFace.EAST)).thenReturn(head);
        when(claims.canEdit(player, foot.getLocation())).thenReturn(true);
        when(manager.attempt(any())).thenReturn(new ClaimAttemptResult(false, true, 1.0 / 16, 16));
        BlockBreakEvent event = new BlockBreakEvent(foot, player);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            listener.onBreak(event);
        }
        assertThat(event.isCancelled()).isTrue();
        ArgumentCaptor<ClaimAttempt> attempt = ArgumentCaptor.forClass(ClaimAttempt.class);
        verify(manager).attempt(attempt.capture());
        assertThat(attempt.getValue().block()).isEqualTo(ClaimsService.block(head));
        assertThat(attempt.getValue().blockState()).isEqualTo("minecraft:red_bed[part=head,facing=east]");
    }

    @Test
    void linkedAuthorizationCannotDestroyAReplacementOrSurviveItsTick() {
        Block upper = doorPair();
        when(claims.canEdit(player, target.getLocation())).thenReturn(true);
        when(claims.canEdit(player, upper.getLocation())).thenReturn(true);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenAnswer(call -> {
                delayed.add(call.getArgument(1));
                return true;
            });
            listener.onBreak(new BlockBreakEvent(target, player));
            changeToAir(target);
            BlockData original = upper.getBlockData();
            changeToAir(upper);
            BlockDestroyEvent replaced = destroy(upper);
            listener.onDestroy(replaced);
            assertThat(replaced.isCancelled()).isTrue();
            when(upper.getBlockData()).thenReturn(original);
            delayed.forEach(Runnable::run);
            BlockDestroyEvent expired = destroy(upper);
            listener.onDestroy(expired);
            assertThat(expired.isCancelled()).isTrue();
        }
    }

    private Block doorPair() {
        Block upper = block(1609, 65, 1608, "minecraft:oak_door[half=upper]");
        Door lowerData = mock(Door.class);
        Door upperData = mock(Door.class);
        Door expectedUpper = mock(Door.class);
        when(lowerData.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        when(lowerData.getAsString()).thenReturn("minecraft:oak_door[half=lower]");
        when(lowerData.clone()).thenReturn(expectedUpper);
        when(expectedUpper.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        when(expectedUpper.getAsString()).thenReturn("minecraft:oak_door[half=upper]");
        when(upperData.getHalf()).thenReturn(Bisected.Half.TOP);
        when(upperData.getAsString()).thenReturn("minecraft:oak_door[half=upper]");
        when(target.getType()).thenReturn(Material.OAK_DOOR);
        when(target.getBlockData()).thenReturn(lowerData);
        when(target.getRelative(BlockFace.UP)).thenReturn(upper);
        when(upper.getType()).thenReturn(Material.OAK_DOOR);
        when(upper.getBlockData()).thenReturn(upperData);
        return upper;
    }

    private BlockDestroyEvent destroy(Block block) {
        return new BlockDestroyEvent(block, mock(BlockData.class), block.getBlockData(), 0, false);
    }

    private void changeToAir(Block block) {
        BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        when(block.getBlockData()).thenReturn(air);
    }

    private Block block(int x, int y, int z, String state) {
        Block block = mock(Block.class);
        BlockData data = mock(BlockData.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        when(block.getBlockData()).thenReturn(data);
        when(data.getAsString()).thenReturn(state);
        when(block.getType()).thenReturn(Material.STONE);
        return block;
    }

    private BlockState replaced(Block block, String state) {
        BlockState replaced = mock(BlockState.class);
        BlockData data = mock(BlockData.class);
        Location location = block.getLocation();
        when(data.getAsString()).thenReturn(state);
        when(replaced.getBlockData()).thenReturn(data);
        when(replaced.getLocation()).thenReturn(location);
        when(replaced.getBlock()).thenReturn(block);
        return replaced;
    }
}
