package art.arcane.adapt.mod.event.player;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.player.PlayerTradeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

/** Villager and wandering trader trades taken from the result slot. */
public final class TradeHooks {
    private static PlayerTradeEvent pending;
    private static boolean suppressXp;

    private TradeHooks() {
    }

    /**
     * The player took the result of a trade. Posts the trade when the payment covers the offer. True when a
     * handler cancelled and the taken result could be put back (picked up on the cursor); the trade is then undone.
     */
    public static boolean taking(Player player, ItemStack carried, Merchant merchant, MerchantContainer slots) {
        pending = null;
        if (!(player instanceof ServerPlayer serverPlayer) || !(merchant instanceof AbstractVillager villager)
            || !ModEvents.listening(PlayerTradeEvent.class)) {
            return false;
        }
        MerchantOffer offer = slots.getActiveOffer();
        if (offer == null) {
            return false;
        }
        ItemStack buyA = slots.getItem(0);
        ItemStack buyB = slots.getItem(1);
        if (!offer.satisfiedBy(buyA, buyB) && !offer.satisfiedBy(buyB, buyA)) {
            return false;
        }
        PlayerTradeEvent event = ModEvents.post(new PlayerTradeEvent(ModRefs.player(serverPlayer), ModRefs.entity(villager),
            ModRefs.item(offer.getResult().copy()), true, true));
        if (event.isCancelled() && serverPlayer.containerMenu.getCarried() == carried) {
            serverPlayer.containerMenu.setCarried(ItemStack.EMPTY);
            slots.updateSellItem();
            serverPlayer.containerMenu.sendAllDataToRemote();
            return true;
        }
        pending = event;
        return false;
    }

    /** Runs the merchant's trade bookkeeping as the trade event chose: uses and experience can each be skipped. */
    public static void notifying(Merchant merchant, MerchantOffer offer, Runnable original) {
        PlayerTradeEvent event = pending;
        pending = null;
        if (event == null || (event.rewardExp() && event.increaseTradeUses())) {
            original.run();
            return;
        }
        int uses = offer.getUses();
        suppressXp = !event.rewardExp();
        try {
            original.run();
        } finally {
            suppressXp = false;
        }
        if (!event.increaseTradeUses()) {
            ((MerchantOfferUses) offer).adapt$uses(uses);
        }
    }

    /** True while a trade's experience reward was turned off by a handler. */
    public static boolean xpSuppressed() {
        return suppressXp;
    }

    /** Writes a trade offer's use count. */
    public interface MerchantOfferUses {
        void adapt$uses(int uses);
    }
}
