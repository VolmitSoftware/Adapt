package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.world.ModBlockRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerBucketEmptyEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Clicks, entity interaction and the block break, damage, drop and place cycle. */
public final class InteractHooks {
    private static Break currentBreak;
    private static int instaBreak;

    private InteractHooks() {
    }

    /** Left click on a block, before creative breaking and mining start. True when a handler denied the block use. */
    public static boolean leftClickBlock(ServerPlayer player, BlockPos pos, Direction face) {
        if (!ModEvents.listening(PlayerInteractEvent.class)) {
            return false;
        }
        ServerLevel level = HookCompat.level(player);
        PlayerInteractEvent event = ModEvents.post(new PlayerInteractEvent(ModRefs.player(player), InteractAction.LEFT_CLICK_BLOCK,
            ModConversions.hand(InteractionHand.MAIN_HAND), ModRefs.item(player.getMainHandItem()), ModRefs.block(level, pos),
            ModConversions.face(face), null, UseResult.DEFAULT, UseResult.DEFAULT));
        if (event.useBlock() != UseResult.DENY) {
            return false;
        }
        resendBlock(player, level, pos);
        return true;
    }

    /**
     * An arm swing. Posts a left click on air only when neither a block nor an entity is in reach, as
     * Paper's ray trace does, so attacking a mob is not also a click on air.
     */
    public static void swung(ServerPlayer player) {
        if (!ModEvents.listening(PlayerInteractEvent.class)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(HookCompat.blockReach(player)));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return;
        }
        double reach = HookCompat.entityReach(player);
        Vec3 entityEnd = eye.add(look.scale(reach));
        AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, entityEnd, area,
            entity -> !entity.isSpectator() && entity.isPickable() && !entity.isPassengerOfSameVehicle(player), reach * reach);
        if (entityHit != null) {
            return;
        }
        ModEvents.post(new PlayerInteractEvent(ModRefs.player(player), InteractAction.LEFT_CLICK_AIR,
            ModConversions.hand(InteractionHand.MAIN_HAND), ModRefs.item(player.getMainHandItem()), null, null, null, UseResult.DENY,
            UseResult.DEFAULT));
    }

    /**
     * Right click on a block, before vanilla uses the block or the item. The outcome stays on the game
     * mode for the rest of the click. True when both uses were denied.
     */
    public static boolean rightClickBlock(ServerPlayer player, ServerPlayerGameMode mode, ItemStack stack, InteractionHand hand,
                                          BlockHitResult hit) {
        InteractState state = ((InteractState.Holder) mode).adapt$interactState();
        state.active = false;
        state.fired = false;
        if (player.isSpectator() || !ModEvents.listening(PlayerInteractEvent.class)) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 offset = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        PlayerInteractEvent event = ModEvents.post(new PlayerInteractEvent(ModRefs.player(player), InteractAction.RIGHT_CLICK_BLOCK,
            ModConversions.hand(hand), ModRefs.item(stack), ModRefs.block(HookCompat.level(player), pos), ModConversions.face(hit.getDirection()),
            ModRefs.vec(offset), UseResult.DEFAULT, UseResult.DEFAULT));
        state.useBlock = event.useBlock();
        state.useItem = event.useItem();
        state.active = true;
        state.fired = true;
        state.hand = hand;
        state.pos = pos.immutable();
        state.stack = stack.copy();
        if (!UseRules.usesItemOnBlock(state.useBlock, state.useItem)) {
            player.containerMenu.sendAllDataToRemote();
        }
        return state.useBlock == UseResult.DENY && state.useItem == UseResult.DENY;
    }

    /**
     * The use-item packet. As Paper does, it ray traces the player's view: on a block it reuses the
     * outcome of the block click for the same block, hand and item, else posts a right click on that
     * block; on nothing it posts a right click on air. True when the item use was denied.
     */
    public static boolean rightClickAir(ServerPlayer player, InteractionHand hand) {
        InteractState state = ((InteractState.Holder) player.gameMode).adapt$interactState();
        boolean fired = state.fired;
        state.fired = false;
        if (!ModEvents.listening(PlayerInteractEvent.class)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(HookCompat.blockReach(player)));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        boolean denied;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (fired && pos.equals(state.pos) && state.hand == hand && HookCompat.sameItem(state.stack, stack)) {
                denied = state.useItem == UseResult.DENY;
            } else {
                Vec3 offset = blockHit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
                PlayerInteractEvent event = ModEvents.post(new PlayerInteractEvent(ModRefs.player(player), InteractAction.RIGHT_CLICK_BLOCK,
                    ModConversions.hand(hand), ModRefs.item(stack), ModRefs.block(HookCompat.level(player), pos), ModConversions.face(blockHit.getDirection()),
                    ModRefs.vec(offset), UseResult.DEFAULT, UseResult.DEFAULT));
                denied = event.useItem() == UseResult.DENY;
            }
        } else {
            PlayerInteractEvent event = ModEvents.post(new PlayerInteractEvent(ModRefs.player(player), InteractAction.RIGHT_CLICK_AIR,
                ModConversions.hand(hand), ModRefs.item(stack), null, null, null, UseResult.DENY, UseResult.DEFAULT));
            denied = event.useItem() == UseResult.DENY;
        }
        if (denied) {
            player.containerMenu.sendAllDataToRemote();
        }
        return denied;
    }

    /** Stepping on or into a block a player triggers (plates, tripwire, farmland, eggs, ore, sensors, dripleaf). True when a handler denied the block use. */
    public static boolean physical(Entity entity, Level level, BlockPos pos) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)
            || !ModEvents.listening(PlayerInteractEvent.class)) {
            return false;
        }
        PlayerInteractEvent event = ModEvents.post(new PlayerInteractEvent(ModRefs.player(player), InteractAction.PHYSICAL, null,
            ModRefs.item(ItemStack.EMPTY), ModRefs.block(serverLevel, pos), null, null, UseResult.DEFAULT, UseResult.DEFAULT));
        return event.useBlock() == UseResult.DENY;
    }

    /** A player empties {@code bucket} into {@code pos}. True when a handler cancelled; the player is resent the block. */
    public static boolean emptyingBucket(LivingEntity user, Level level, BlockPos pos, Item bucket) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)
            || !ModEvents.listening(PlayerBucketEmptyEvent.class)) {
            return false;
        }
        PlayerBucketEmptyEvent event = ModEvents.post(new PlayerBucketEmptyEvent(ModRefs.player(player),
            ModRefs.block(serverLevel, pos), ModRefs.item(new ItemStack(bucket)).type()));
        if (!event.isCancelled()) {
            return false;
        }
        resendBlock(player, serverLevel, pos);
        return true;
    }

    /** Right click on an entity. True when a handler cancelled. */
    public static boolean interactingEntity(ServerPlayer player, Entity target, InteractionHand hand, Vec3 location) {
        if (target == null || !ModEvents.listening(PlayerInteractEntityEvent.class)) {
            return false;
        }
        PlayerInteractEntityEvent event = ModEvents.post(new PlayerInteractEntityEvent(ModRefs.player(player), ModRefs.entity(target),
            ModConversions.hand(hand), location == null ? null : ModRefs.vec(location)));
        if (!event.isCancelled()) {
            return false;
        }
        player.containerMenu.sendAllDataToRemote();
        return true;
    }

    /**
     * A player starts mining a block (after the left click event). Returns -1 when cancelled, 1 when a
     * handler asked for an instant break, 0 otherwise.
     */
    public static int blockDamaging(ServerPlayer player, BlockPos pos, BlockState state) {
        instaBreak = 0;
        if (ModEvents.breaksSilenced() || !ModEvents.listening(BlockDamageEvent.class)) {
            return 0;
        }
        ServerLevel level = HookCompat.level(player);
        boolean insta = state.getDestroyProgress(player, level, pos) >= 1.0F;
        BlockDamageEvent event = ModEvents.post(new BlockDamageEvent(ModRefs.player(player), ModRefs.block(level, pos),
            ModRefs.item(player.getMainHandItem()), insta));
        if (event.isCancelled()) {
            resendBlock(player, level, pos);
            return -1;
        }
        instaBreak = event.instaBreak() && !insta ? 1 : 0;
        return instaBreak;
    }

    /** Destroy progress after the damage event: an instant break forced by a handler. */
    public static float destroyProgress(float progress) {
        int forced = instaBreak;
        instaBreak = 0;
        return forced == 1 ? Math.max(progress, 2.0F) : progress;
    }

    /**
     * Start of a player block break, with the block still in place. True when a handler cancelled;
     * the block is resent. Otherwise the break's drops are held back until {@link #broken}.
     */
    public static boolean breaking(ServerPlayer player, BlockPos pos) {
        if (ModEvents.breaksSilenced()) {
            return false;
        }
        boolean breakListeners = ModEvents.listening(ModBlockBreakEvent.class);
        boolean dropListeners = ModEvents.listening(BlockDropItemEvent.class);
        if (!breakListeners && !dropListeners) {
            return false;
        }
        ServerLevel level = HookCompat.level(player);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        ModBlockBreakEvent event = null;
        if (breakListeners) {
            boolean refused = !HookCompat.canDestroy(player, state, level, pos);
            event = new ModBlockBreakEvent(ModRefs.player(player), ModRefs.block(level, pos));
            event.setCancelled(refused);
            ModEvents.post(event);
            if (refused) {
                return false;
            }
            if (event.isCancelled()) {
                resendBlock(player, level, pos);
                return true;
            }
        }
        Break context = new Break(player, level, pos.immutable(), state, event, currentBreak, spawnerType(level, pos),
                level.getBlockEntity(pos));
        currentBreak = context;
        DropCapture.open(level, context);
        return false;
    }

    /** The mob a spawner block entity spawns, read before vanilla removes it; null for other blocks or an empty spawner. */
    private static Id spawnerType(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return null;
        }
        String id = HookCompat.spawnerEntity(spawner, level);
        return id == null ? null : Id.tryParse(id);
    }

    /** Block experience inside a player break: replaced by the experience a handler set. True when vanilla's is skipped. */
    public static boolean blockExperience(Level level, BlockPos pos) {
        Break context = currentBreak;
        if (context == null || context.level != level || !context.pos.equals(pos) || context.event == null || !context.event.experienceSet()) {
            return false;
        }
        if (!context.experienceApplied) {
            context.experienceApplied = true;
            int experience = context.event.experience();
            if (experience > 0) {
                ExperienceOrb.award(context.level, Vec3.atCenterOf(pos), experience);
            }
        }
        return true;
    }

    /** End of a player block break: posts the drop event for what vanilla tried to spawn and spawns the rest. */
    public static void broken(ServerPlayer player, boolean destroyed) {
        Break context = currentBreak;
        if (context == null || context.player != player) {
            return;
        }
        currentBreak = context.parent;
        DropCapture.Frame frame = DropCapture.close(context);
        List<ItemEntity> items = frame == null ? List.of() : frame.items;
        ModBlockBreakEvent event = context.event;
        if (destroyed && event != null && event.experienceSet() && !context.experienceApplied && event.experience() > 0) {
            ExperienceOrb.award(context.level, Vec3.atCenterOf(context.pos), event.experience());
        }
        if (event != null && !event.dropItems()) {
            return;
        }
        if (!destroyed || HookCompat.preventsBlockDrops(player) || !ModEvents.listening(BlockDropItemEvent.class)) {
            spawn(context.level, items);
            return;
        }
        List<ItemEntityRef> refs = new ArrayList<>(items.size());
        Map<ItemEntityRef, ItemEntity> origins = new IdentityHashMap<>();
        for (ItemEntity item : items) {
            ItemEntityRef ref = ModEvents.itemEntity(item);
            refs.add(ref);
            origins.put(ref, item);
        }
        BlockRef block = ModRefs.block(context.level, context.pos);
        BlockDropItemEvent dropEvent = ModEvents.post(new BlockDropItemEvent(ModRefs.player(player), block, ModRefs.state(context.state), refs,
                context.spawnerType, ModBlockRef.blockEntityData(context.blockEntity)));
        if (dropEvent.isCancelled()) {
            return;
        }
        List<ItemEntity> kept = new ArrayList<>(dropEvent.items().size());
        for (ItemEntityRef ref : dropEvent.items()) {
            ItemEntity origin = origins.get(ref);
            if (origin != null) {
                kept.add(origin);
            } else if (ref != null && ModRefs.entity(ref) instanceof ItemEntity added) {
                kept.add(added);
            }
        }
        spawn(context.level, kept);
    }

    private static void spawn(ServerLevel level, List<ItemEntity> items) {
        for (ItemEntity item : items) {
            if (!item.getItem().isEmpty() && !item.isRemoved()) {
                level.addFreshEntity(item);
            }
        }
    }

    /** True when a placement in this context will post a {@link BlockPlaceEvent}. */
    public static boolean postsPlace(BlockPlaceContext context) {
        return context.getPlayer() instanceof ServerPlayer && context.getLevel() instanceof ServerLevel
            && ModEvents.listening(BlockPlaceEvent.class);
    }

    /** A block was set by a player placement. True when a handler cancelled or denied the build; the old state is restored. */
    public static boolean placed(BlockPlaceContext context, BlockState replaced, ItemStack before) {
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer) || !(context.getLevel() instanceof ServerLevel level)
            || !ModEvents.listening(BlockPlaceEvent.class)) {
            return false;
        }
        BlockPos pos = context.getClickedPos();
        BlockPos against = context.replacingClickedOnBlock() ? pos : pos.relative(context.getClickedFace().getOpposite());
        BlockPlaceEvent event = ModEvents.post(new BlockPlaceEvent(ModRefs.player(serverPlayer), ModRefs.block(level, pos),
            ModRefs.state(replaced), ModRefs.block(level, against), ModRefs.item(before), ModConversions.hand(context.getHand())));
        if (!event.isCancelled() && event.canBuild()) {
            return false;
        }
        level.setBlock(pos, replaced, Block.UPDATE_ALL);
        serverPlayer.containerMenu.sendAllDataToRemote();
        return true;
    }

    static void resendBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            Packet<?> update = blockEntity.getUpdatePacket();
            if (update != null) {
                player.connection.send(update);
            }
        }
    }

    private static final class Break {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos pos;
        final BlockState state;
        final ModBlockBreakEvent event;
        final Break parent;
        final Id spawnerType;
        final BlockEntity blockEntity;
        boolean experienceApplied;

        Break(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ModBlockBreakEvent event, Break parent,
              Id spawnerType, BlockEntity blockEntity) {
            this.player = player;
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.event = event;
            this.parent = parent;
            this.spawnerType = spawnerType;
            this.blockEntity = blockEntity;
        }
    }
}
