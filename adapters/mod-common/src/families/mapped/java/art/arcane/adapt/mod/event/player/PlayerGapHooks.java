package art.arcane.adapt.mod.event.player;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerBedEnterEvent;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.event.player.PlayerInputEvent;
import art.arcane.adapt.platform.event.player.PlayerInventorySlotChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerItemBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerPreLoginEvent;
import art.arcane.adapt.platform.event.player.PlayerRecipeBookClickEvent;
import art.arcane.adapt.platform.event.player.PlayerShearEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerStopUsingItemEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Chat, login, input, swings, beds, item use and wear, inventory slots, recipe book clicks and shearing. */
public final class PlayerGapHooks {
    private static final Map<ServerPlayer, PlayerInput> INPUTS = new WeakHashMap<>();
    private static InteractionHand swingHand = InteractionHand.MAIN_HAND;
    private static boolean bedPosted;
    private static ServerPlayer interacting;
    private static Entity interactTarget;
    private static InteractionHand interactHand;

    private PlayerGapHooks() {
    }

    /** True when a handler cancelled the chat message; it is then not broadcast. */
    public static boolean chatting(ServerPlayer player, PlayerChatMessage message) {
        if (!ModEvents.listening(PlayerChatEvent.class)) {
            return false;
        }
        return ModEvents.post(new PlayerChatEvent(ModRefs.player(player), message.signedContent(), false)).isCancelled();
    }

    /** The vanilla login check ran; reports its outcome. */
    public static void loginChecked(GameProfile profile, Component error) {
        if (ModEvents.listening(PlayerPreLoginEvent.class)) {
            ModEvents.post(new PlayerPreLoginEvent(profile.getId(), profile.getName(), error == null));
        }
    }

    public static void recipeClicked(ServerPlayer player, ResourceLocation recipe, boolean makeAll) {
        if (recipe == null || !ModEvents.listening(PlayerRecipeBookClickEvent.class)) {
            return;
        }
        ModEvents.post(new PlayerRecipeBookClickEvent(ModRefs.player(player), ModRefs.id(recipe), makeAll));
    }

    /**
     * The vehicle steering packet arrived, before vanilla stores it. Clients before 1.21.2 send movement keys only
     * while riding and never send the sprint key.
     */
    public static void input(ServerPlayer player, ServerboundPlayerInputPacket packet) {
        if (!ModEvents.listening(PlayerInputEvent.class)) {
            return;
        }
        PlayerInput input = new PlayerInput(packet.getZza() > 0.0F, packet.getZza() < 0.0F, packet.getXxa() > 0.0F,
            packet.getXxa() < 0.0F, packet.isJumping(), packet.isShiftKeyDown(), false);
        if (input.equals(INPUTS.put(player, input))) {
            return;
        }
        ModEvents.post(new PlayerInputEvent(ModRefs.player(player), input));
    }

    public static void swingHand(InteractionHand hand) {
        swingHand = hand;
    }

    /** True when a handler cancelled the arm swing of the swing packet being handled. */
    public static boolean animating(ServerPlayer player) {
        InteractionHand hand = swingHand;
        swingHand = InteractionHand.MAIN_HAND;
        if (!ModEvents.listening(PlayerAnimationEvent.class)) {
            return false;
        }
        PlayerAnimationEvent.Animation animation = hand == InteractionHand.OFF_HAND
            ? PlayerAnimationEvent.Animation.OFF_ARM_SWING
            : PlayerAnimationEvent.Animation.ARM_SWING;
        return ModEvents.post(new PlayerAnimationEvent(ModRefs.player(player), animation)).isCancelled();
    }

    /** Vanilla is about to put the player to sleep. True when a handler cancelled. */
    public static boolean sleeping(ServerPlayer player, BlockPos pos) {
        if (!ModEvents.listening(PlayerBedEnterEvent.class)) {
            bedPosted = true;
            return false;
        }
        boolean cancelled = ModEvents.post(new PlayerBedEnterEvent(ModRefs.player(player), ModRefs.block(player.serverLevel(), pos),
            PlayerBedEnterEvent.Result.OK)).isCancelled();
        bedPosted = !cancelled;
        return cancelled;
    }

    /** The bed attempt finished; a refusal that was not posted yet is reported with its reason. */
    public static void sleepAttempted(ServerPlayer player, BlockPos pos, Player.BedSleepingProblem problem) {
        boolean posted = bedPosted;
        bedPosted = false;
        if (posted || problem == null || !ModEvents.listening(PlayerBedEnterEvent.class)) {
            return;
        }
        ModEvents.post(new PlayerBedEnterEvent(ModRefs.player(player), ModRefs.block(player.serverLevel(), pos), bedResult(problem)));
    }

    private static PlayerBedEnterEvent.Result bedResult(Player.BedSleepingProblem problem) {
        if (problem == Player.BedSleepingProblem.TOO_FAR_AWAY) {
            return PlayerBedEnterEvent.Result.TOO_FAR_AWAY;
        }
        if (problem == Player.BedSleepingProblem.OBSTRUCTED) {
            return PlayerBedEnterEvent.Result.OBSTRUCTED;
        }
        if (problem == Player.BedSleepingProblem.NOT_SAFE) {
            return PlayerBedEnterEvent.Result.NOT_SAFE;
        }
        Component message = problem.getMessage();
        if (message == null) {
            return PlayerBedEnterEvent.Result.OTHER_PROBLEM;
        }
        if (message.getContents() instanceof TranslatableContents translatable && translatable.getKey().endsWith("no_sleep")) {
            return PlayerBedEnterEvent.Result.NOT_POSSIBLE_NOW;
        }
        return PlayerBedEnterEvent.Result.NOT_POSSIBLE_HERE;
    }

    /** A player lets go of a drawn bow, trident, crossbow or other item in use. */
    public static void releasing(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !ModEvents.listening(PlayerStopUsingItemEvent.class)) {
            return;
        }
        ItemStack using = player.getUseItem();
        if (using.isEmpty() || !ItemStack.isSameItem(player.getItemInHand(player.getUsedItemHand()), using)) {
            return;
        }
        ModEvents.post(new PlayerStopUsingItemEvent(ModRefs.player(player), ModRefs.item(using.copy()), player.getTicksUsingItem()));
    }

    /** Wraps the break callback of a player's damaged item so the break is reported before the callback runs. */
    public static <T> Consumer<T> breaking(ItemStack stack, ServerPlayer player, Consumer<T> onBreak) {
        if (player == null || onBreak == null || stack.isEmpty() || !stack.isDamageableItem()
            || !ModEvents.listening(PlayerItemBreakEvent.class)) {
            return onBreak;
        }
        ItemStack snapshot = stack.copyWithCount(1);
        return broken -> {
            ModEvents.post(new PlayerItemBreakEvent(ModRefs.player(player), ModRefs.item(snapshot)));
            onBreak.accept(broken);
        };
    }

    /** A synchronized menu slot is about to record a new stack; reports changes to the player's own inventory slots. */
    public static void slotChanging(AbstractContainerMenu menu, int index, ItemStack previous, ItemStack current) {
        if (index < 0 || index >= menu.slots.size() || ItemStack.matches(previous, current)) {
            return;
        }
        Slot slot = menu.slots.get(index);
        if (slot instanceof ResultSlot || !(slot.container instanceof Inventory inventory)
            || !(inventory.player instanceof ServerPlayer player) || !ModEvents.listening(PlayerInventorySlotChangeEvent.class)) {
            return;
        }
        ModEvents.post(new PlayerInventorySlotChangeEvent(ModRefs.player(player), index, slot.getContainerSlot(),
            ModRefs.item(previous.copy()), ModRefs.item(current.copy())));
    }

    public static void interacting(Player player, Entity target, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            interacting = serverPlayer;
            interactTarget = target;
            interactHand = hand;
        }
    }

    public static void interacted(Player player) {
        if (player == interacting) {
            interacting = null;
            interactTarget = null;
            interactHand = null;
        }
    }

    /** A shearable mob is about to be sheared. True when a handler cancelled; the mob then keeps its wool, mushrooms or flower. */
    public static boolean shearing(Entity entity, ServerLevel level, ItemStack tool) {
        ServerPlayer player = interacting;
        if (player == null || interactTarget != entity || !ModEvents.listening(PlayerShearEntityEvent.class)) {
            return false;
        }
        Hand hand = ModConversions.hand(interactHand == null ? InteractionHand.MAIN_HAND : interactHand);
        return ModEvents.post(new PlayerShearEntityEvent(ModRefs.player(player), ModRefs.entity(entity), ModRefs.item(tool), hand))
            .isCancelled();
    }
}
