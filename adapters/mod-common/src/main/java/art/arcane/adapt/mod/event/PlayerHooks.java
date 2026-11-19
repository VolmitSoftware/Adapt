package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.FoodLevelChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleFlightEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** Player lifecycle, movement, input toggles and the player's own item, food and experience changes. */
public final class PlayerHooks {
    private static final double MOVE_DISTANCE_SQUARED = 1.0D / 256.0D;
    private static final float MOVE_ROTATION = 10.0F;

    private static final CauseStack<TeleportCause> TELEPORT_CAUSES = new CauseStack<>();
    private static final CauseStack<ItemStack> FOOD_ITEMS = new CauseStack<>();
    private static int silentTeleports;
    private static int pendingFishExperience = -1;

    private PlayerHooks() {
    }

    public static void joined(ServerPlayer player) {
        if (ModEvents.listening(PlayerJoinEvent.class)) {
            ModEvents.post(new PlayerJoinEvent(ModRefs.player(player)));
        }
    }

    public static void quitting(ServerPlayer player) {
        if (ModEvents.listening(PlayerQuitEvent.class)) {
            ModEvents.post(new PlayerQuitEvent(ModRefs.player(player)));
        }
    }

    /** After vanilla built the respawned player. A changed respawn location teleports it before the client sees a tick. */
    public static void respawned(ServerPlayer old, ServerPlayer player) {
        ServerLevel oldLevel = HookCompat.level(old);
        if (ModEvents.listening(PlayerRespawnEvent.class)) {
            boolean bed = false;
            boolean anchor = false;
            ResourceKey<Level> respawnDimension = HookCompat.respawnDimension(player);
            if (respawnDimension != null) {
                ServerLevel respawnLevel = HookCompat.level(player).getServer().getLevel(respawnDimension);
                BlockPos respawnPos = HookCompat.respawnPosition(player);
                if (respawnLevel == player.level() && respawnPos.closerToCenterThan(player.position(), 3.0D)) {
                    BlockState state = respawnLevel.getBlockState(respawnPos);
                    bed = state.getBlock() instanceof BedBlock;
                    anchor = state.getBlock() instanceof RespawnAnchorBlock;
                }
            }
            Loc spawn = ModEvents.loc(player);
            PlayerRespawnEvent event = ModEvents.post(new PlayerRespawnEvent(ModRefs.player(player), spawn, bed, anchor));
            Loc chosen = event.respawnLocation();
            if (!chosen.equals(spawn)) {
                ServerLevel level = ModRefs.world(chosen.world());
                if (level != null) {
                    silently(() -> HookCompat.teleport(player, level, chosen));
                }
            }
        }
        if (player.level() != oldLevel) {
            changedWorld(player, oldLevel);
        }
    }

    public static void changedWorld(ServerPlayer player, ServerLevel from) {
        if (from != null && from != player.level() && ModEvents.listening(PlayerChangedWorldEvent.class)) {
            ModEvents.post(new PlayerChangedWorldEvent(ModRefs.player(player), ModRefs.world(from)));
        }
    }

    /** Runs a teleport Adapt starts itself under a cause, as Paper's API teleports carry PLUGIN. */
    public static <T> T withTeleportCause(TeleportCause cause, Supplier<T> action) {
        return TELEPORT_CAUSES.with(cause, action);
    }

    /** Opens a cause for the teleports vanilla runs until the matching {@link #teleportCauseEnd()}. */
    public static void teleportCause(TeleportCause cause) {
        TELEPORT_CAUSES.push(cause, false);
    }

    public static void teleportCauseEnd() {
        TELEPORT_CAUSES.pop();
    }

    static <T> T silently(Supplier<T> action) {
        silentTeleports++;
        try {
            return action.get();
        } finally {
            silentTeleports--;
        }
    }

    /** True when a teleport of this player posts an event: no silent teleport runs, the player is in a world and a handler listens. */
    public static boolean teleportWatched(ServerPlayer player) {
        return silentTeleports == 0 && !player.isRemoved() && ModEvents.listening(PlayerTeleportEvent.class);
    }

    /**
     * Posts a teleport of a watched player to a target in a level. Returns the target itself when handlers left it,
     * the location they chose otherwise, or null when they cancelled.
     */
    public static Loc teleporting(ServerPlayer player, ServerLevel to, Loc target) {
        PlayerTeleportEvent event = ModEvents.post(new PlayerTeleportEvent(ModRefs.player(player), ModEvents.loc(player), target,
            cause(HookCompat.level(player), to)));
        if (event.isCancelled()) {
            return null;
        }
        Loc chosen = event.to();
        return chosen.equals(target) ? target : chosen;
    }

    /** Same-level position teleports (chorus fruit). True when vanilla must not move the player. */
    public static boolean teleportingTo(ServerPlayer player, double x, double y, double z) {
        if (silentTeleports > 0 || !ModEvents.listening(PlayerTeleportEvent.class)) {
            return false;
        }
        Loc target = ModEvents.loc(player, x, y, z, player.getYRot(), player.getXRot());
        TeleportCause current = TELEPORT_CAUSES.current();
        TeleportCause cause = current != null ? current : TeleportCause.UNKNOWN;
        PlayerTeleportEvent event = ModEvents.post(new PlayerTeleportEvent(ModRefs.player(player), ModEvents.loc(player), target, cause));
        if (event.isCancelled()) {
            return true;
        }
        Loc chosen = event.to();
        if (chosen.equals(target)) {
            return false;
        }
        ServerLevel level = ModRefs.world(chosen.world());
        if (level == null) {
            return true;
        }
        silently(() -> HookCompat.teleport(player, level, chosen));
        return true;
    }

    private static TeleportCause cause(ServerLevel from, ServerLevel to) {
        TeleportCause current = TELEPORT_CAUSES.current();
        if (current != null) {
            return current;
        }
        if (from != to) {
            if (from.dimension() == Level.END || to.dimension() == Level.END) {
                return TeleportCause.END_PORTAL;
            }
            if (from.dimension() == Level.NETHER || to.dimension() == Level.NETHER) {
                return TeleportCause.NETHER_PORTAL;
            }
        }
        return TeleportCause.UNKNOWN;
    }

    /** True when a handler cancelled the game mode change. */
    public static boolean gameModeChanging(ServerPlayer player, GameType mode) {
        if (player.gameMode.getGameModeForPlayer() == mode || !ModEvents.listening(PlayerGameModeChangeEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerGameModeChangeEvent(ModRefs.player(player), ModConversions.gameMode(mode))).isCancelled();
    }

    /** Before vanilla applies a position packet: remembers where the player stood. */
    public static void movePrepared(ServerPlayer player, MoveState state) {
        state.previousX = player.getX();
        state.previousY = player.getY();
        state.previousZ = player.getZ();
        state.previousYaw = player.getYRot();
        state.previousPitch = player.getXRot();
        state.jumped = false;
    }

    /**
     * After vanilla accepted a position packet. Posts a move event past Paper's thresholds; a cancel
     * sends the player back to the last posted position and a changed target teleports there. True
     * when the rest of vanilla's packet handling must be skipped.
     */
    public static boolean moved(ServerGamePacketListenerImpl connection, ServerPlayer player, MoveState state) {
        if (state.jumped && jumped(connection, player, state)) {
            return true;
        }
        if (!state.known) {
            state.x = state.previousX;
            state.y = state.previousY;
            state.z = state.previousZ;
            state.yaw = state.previousYaw;
            state.pitch = state.previousPitch;
            state.known = true;
        }
        double dx = player.getX() - state.x;
        double dy = player.getY() - state.y;
        double dz = player.getZ() - state.z;
        float rotation = Math.abs(player.getYRot() - state.yaw) + Math.abs(player.getXRot() - state.pitch);
        if (dx * dx + dy * dy + dz * dz <= MOVE_DISTANCE_SQUARED && rotation <= MOVE_ROTATION || player.isDeadOrDying() || player.isSleeping()) {
            return false;
        }
        double fromX = state.x;
        double fromY = state.y;
        double fromZ = state.z;
        float fromYaw = state.yaw;
        float fromPitch = state.pitch;
        state.x = player.getX();
        state.y = player.getY();
        state.z = player.getZ();
        state.yaw = player.getYRot();
        state.pitch = player.getXRot();
        if (!ModEvents.listening(PlayerMoveEvent.class)) {
            return false;
        }
        Loc from = ModEvents.loc(player, fromX, fromY, fromZ, fromYaw, fromPitch);
        Loc to = ModEvents.loc(player);
        PlayerMoveEvent event = ModEvents.post(new PlayerMoveEvent(ModRefs.player(player), from, to));
        if (event.isCancelled()) {
            state.x = fromX;
            state.y = fromY;
            state.z = fromZ;
            state.yaw = fromYaw;
            state.pitch = fromPitch;
            connection.teleport(fromX, fromY, fromZ, fromYaw, fromPitch);
            return true;
        }
        Loc chosen = event.to();
        if (chosen.equals(to)) {
            return false;
        }
        state.x = chosen.x();
        state.y = chosen.y();
        state.z = chosen.z();
        state.yaw = chosen.yaw();
        state.pitch = chosen.pitch();
        ServerLevel level = ModRefs.world(chosen.world());
        if (level == null || level == player.level()) {
            connection.teleport(chosen.x(), chosen.y(), chosen.z(), chosen.yaw(), chosen.pitch());
        } else {
            silently(() -> HookCompat.teleport(player, level, chosen));
        }
        return true;
    }

    /**
     * Vanilla's server-side jump detection fired. The jump event waits until vanilla accepted the
     * move, so it carries the landing position as Paper's does.
     */
    public static void jumping(ServerPlayer player, MoveState state) {
        if (!ModEvents.listening(PlayerJumpEvent.class)) {
            return;
        }
        state.jumped = true;
        if (state.known) {
            state.jumpX = state.x;
            state.jumpY = state.y;
            state.jumpZ = state.z;
            state.jumpYaw = state.yaw;
            state.jumpPitch = state.pitch;
        } else {
            state.jumpX = state.previousX;
            state.jumpY = state.previousY;
            state.jumpZ = state.previousZ;
            state.jumpYaw = state.previousYaw;
            state.jumpPitch = state.previousPitch;
        }
    }

    /** Posts the jump vanilla accepted. True when a handler cancelled; the player is sent back. */
    private static boolean jumped(ServerGamePacketListenerImpl connection, ServerPlayer player, MoveState state) {
        state.jumped = false;
        Loc from = ModEvents.loc(player, state.jumpX, state.jumpY, state.jumpZ, state.jumpYaw, state.jumpPitch);
        PlayerJumpEvent event = ModEvents.post(new PlayerJumpEvent(ModRefs.player(player), from, ModEvents.loc(player)));
        if (!event.isCancelled()) {
            return false;
        }
        Loc back = event.from();
        state.x = back.x();
        state.y = back.y();
        state.z = back.z();
        state.yaw = back.yaw();
        state.pitch = back.pitch();
        state.known = true;
        connection.teleport(back.x(), back.y(), back.z(), back.yaw(), back.pitch());
        return true;
    }

    /** True when a handler cancelled the sneak change. */
    public static boolean sneaking(ServerPlayer player, boolean sneaking) {
        if (player.isShiftKeyDown() == sneaking || !ModEvents.listening(PlayerToggleSneakEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerToggleSneakEvent(ModRefs.player(player), sneaking)).isCancelled();
    }

    /** True when a handler cancelled the sprint change. */
    public static boolean sprinting(ServerPlayer player, boolean sprinting) {
        if (player.isSprinting() == sprinting || !ModEvents.listening(PlayerToggleSprintEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerToggleSprintEvent(ModRefs.player(player), sprinting)).isCancelled();
    }

    /** True when a handler cancelled the flight change; the client is told its real abilities. */
    public static boolean flying(ServerPlayer player, boolean requested) {
        if (!player.getAbilities().mayfly || player.getAbilities().flying == requested
            || !ModEvents.listening(PlayerToggleFlightEvent.class)) {
            return false;
        }
        if (!ModEvents.post(new PlayerToggleFlightEvent(ModRefs.player(player), requested)).isCancelled()) {
            return false;
        }
        player.onUpdateAbilities();
        return true;
    }

    /** True when a handler cancelled the hotbar change; the client is moved back to the current slot. */
    public static boolean holding(ServerPlayer player, int slot) {
        if (!ModEvents.listening(PlayerItemHeldEvent.class)) {
            return false;
        }
        int current = HookCompat.selectedSlot(player);
        if (!ModEvents.post(new PlayerItemHeldEvent(ModRefs.player(player), current, slot)).isCancelled()) {
            return false;
        }
        player.connection.send(HookCompat.selectedSlotPacket(current));
        return true;
    }

    /** The swap-hands key. True when this hook performed or refused the swap and vanilla must not. */
    public static boolean swapping(ServerPlayer player) {
        if (player.isSpectator() || !ModEvents.listening(PlayerSwapHandItemsEvent.class)) {
            return false;
        }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        PlayerSwapHandItemsEvent event = ModEvents.post(new PlayerSwapHandItemsEvent(ModRefs.player(player), ModRefs.item(off),
            ModRefs.item(main)));
        if (event.isCancelled()) {
            player.inventoryMenu.sendAllDataToRemote();
            return true;
        }
        player.setItemInHand(InteractionHand.OFF_HAND, ModRefs.item(event.offHand()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ModRefs.item(event.mainHand()));
        player.stopUsingItem();
        return true;
    }

    /** A player threw an item. True when a handler cancelled and the stack went back to the inventory. */
    public static boolean dropping(ServerPlayer player, ItemEntity item) {
        if (item.getOwner() != player || !ModEvents.listening(PlayerDropItemEvent.class)) {
            return false;
        }
        if (!ModEvents.post(new PlayerDropItemEvent(ModRefs.player(player), ModEvents.itemEntity(item))).isCancelled()) {
            return false;
        }
        ItemStack stack = item.getItem();
        player.getInventory().add(stack);
        player.containerMenu.sendAllDataToRemote();
        return stack.isEmpty();
    }

    /** Null when no handler listens; otherwise the posted consume event. */
    public static PlayerItemConsumeEvent consuming(ServerPlayer player, ItemStack using, InteractionHand hand) {
        if (!ModEvents.listening(PlayerItemConsumeEvent.class)) {
            return null;
        }
        return ModEvents.post(new PlayerItemConsumeEvent(ModRefs.player(player), ModRefs.item(using.copy()), ModConversions.hand(hand), null));
    }

    /** The item vanilla must finish using: the original unless a handler changed the event's copy. */
    public static ItemStack consumed(PlayerItemConsumeEvent event, ItemStack using) {
        ItemStack chosen = ModRefs.item(event.item());
        return ItemStack.matches(chosen, using) ? using : chosen;
    }

    public static ItemStack consumeResult(PlayerItemConsumeEvent event, ItemStack result) {
        ItemRef replacement = event.replacement();
        return replacement == null ? result : ModRefs.item(replacement);
    }

    /** Durability loss after Unbreaking. Returns the loss to apply; 0 when a handler cancelled. */
    public static int itemDamaging(ServerPlayer player, ItemStack stack, int damage, int original) {
        if (player == null || damage <= 0 || !ModEvents.listening(PlayerItemDamageEvent.class)) {
            return damage;
        }
        PlayerItemDamageEvent event = ModEvents.post(new PlayerItemDamageEvent(ModRefs.player(player), ModRefs.item(stack), damage, original));
        return event.isCancelled() ? 0 : event.damage();
    }

    public static int experienceGained(Player player, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || !ModEvents.listening(PlayerExpChangeEvent.class)) {
            return amount;
        }
        return ModEvents.post(new PlayerExpChangeEvent(ModRefs.player(serverPlayer), amount)).amount();
    }

    /** Food being eaten until the matching {@link #eatingEnd()}; the next food level change carries it. */
    public static void eating(ItemStack stack) {
        FOOD_ITEMS.push(stack, false);
    }

    public static void eatingEnd() {
        FOOD_ITEMS.pop();
    }

    /**
     * Every food gain: food items, cake and anything else that feeds a player through {@code FoodData}.
     * Returns the nutrition to add, or {@link Integer#MIN_VALUE} when a handler cancelled.
     */
    public static int feeding(Player owner, FoodData food, int nutrition) {
        if (!(owner instanceof ServerPlayer player) || !ModEvents.listening(FoodLevelChangeEvent.class)) {
            return nutrition;
        }
        int current = food.getFoodLevel();
        int target = Math.max(0, Math.min(20, current + nutrition));
        ItemStack stack = FOOD_ITEMS.current();
        FoodLevelChangeEvent event = ModEvents.post(new FoodLevelChangeEvent(ModRefs.player(player), target,
            stack == null ? null : ModRefs.item(stack.copy())));
        return event.isCancelled() ? Integer.MIN_VALUE : event.foodLevel() - current;
    }

    /** Hunger loss from exhaustion and peaceful regeneration. Returns the food level handlers chose, or -1 when they cancelled. */
    public static int hungering(ServerPlayer player, int level) {
        if (player == null || !ModEvents.listening(FoodLevelChangeEvent.class)) {
            return level;
        }
        FoodLevelChangeEvent event = ModEvents.post(new FoodLevelChangeEvent(ModRefs.player(player), level, null));
        return event.isCancelled() ? -1 : event.foodLevel();
    }

    /** Casting a rod: the hook is built but not spawned. True when a handler cancelled. */
    public static boolean fishing(Player owner, FishingHook hook) {
        if (!(owner instanceof ServerPlayer player) || !ModEvents.listening(PlayerFishEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerFishEvent(ModRefs.player(player), null, ModRefs.entity(hook), PlayerFishEvent.State.FISHING, 0))
            .isCancelled();
    }

    /** A fish bites, lures in, or gets away. True when a handler cancelled a bite or a lure. */
    public static boolean fishState(FishingHook hook, PlayerFishEvent.State state) {
        if (!(hook.getPlayerOwner() instanceof ServerPlayer player) || !ModEvents.listening(PlayerFishEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerFishEvent(ModRefs.player(player), null, ModRefs.entity(hook), state, 0)).isCancelled();
    }

    /**
     * Reeling in, before the hook is removed: IN_GROUND when it rests on a block, REEL_IN when nothing
     * was caught. True when a handler cancelled; the hook then stays.
     */
    public static boolean reeling(FishingHook hook, int caught) {
        PlayerFishEvent.State state;
        if (hook.onGround()) {
            state = PlayerFishEvent.State.IN_GROUND;
        } else if (caught == 0) {
            state = PlayerFishEvent.State.REEL_IN;
        } else {
            return false;
        }
        return fishState(hook, state);
    }

    /** Reeling in a hooked entity. True when a handler cancelled. */
    public static boolean fishCaughtEntity(FishingHook hook, Player owner, Entity caught) {
        if (!(owner instanceof ServerPlayer player) || !ModEvents.listening(PlayerFishEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerFishEvent(ModRefs.player(player), caught == null ? null : ModRefs.entity(caught), ModRefs.entity(hook),
            PlayerFishEvent.State.CAUGHT_ENTITY, 0)).isCancelled();
    }

    /**
     * A caught item is about to spawn. True when a handler cancelled. The experience handlers chose
     * replaces the orb vanilla spawns next.
     */
    public static boolean fishCaught(FishingHook hook, Player owner, ItemEntity caught) {
        pendingFishExperience = -1;
        if (!(owner instanceof ServerPlayer player) || !ModEvents.listening(PlayerFishEvent.class)) {
            return false;
        }
        int experience = HookCompat.random(hook).nextInt(6) + 1;
        PlayerFishEvent event = ModEvents.post(new PlayerFishEvent(ModRefs.player(player), ModRefs.entity(caught), ModRefs.entity(hook),
            PlayerFishEvent.State.CAUGHT_FISH, experience));
        if (event.isCancelled()) {
            return true;
        }
        pendingFishExperience = event.experience();
        return false;
    }

    /** The orb to spawn for a catch, or null when handlers set the experience to 0. */
    public static ExperienceOrb fishExperience(ExperienceOrb orb) {
        int pending = pendingFishExperience;
        pendingFishExperience = -1;
        if (pending < 0 || pending == orb.getValue()) {
            return orb;
        }
        if (pending == 0) {
            return null;
        }
        return new ExperienceOrb(orb.level(), orb.getX(), orb.getY(), orb.getZ(), pending);
    }
}
