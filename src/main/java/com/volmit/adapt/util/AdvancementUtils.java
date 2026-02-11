package com.volmit.adapt.util;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementFrameTypeWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.google.common.base.Preconditions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AdvancementUtils {

    private static volatile boolean unavailable;
    private static volatile boolean warned;

    /**
     * Displays a custom toast to a player.
     *
     * @param player A player to show the toast.
     * @param icon The displayed item of the toast.
     * @param title The displayed title of the toast.
     * @param frame The frame type of the toast.
     * @see UltimateAdvancementAPI#displayCustomToast(Player, ItemStack, String, com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType)
     */
    public static void displayToast(@NotNull Player player, @NotNull ItemStack icon, @NotNull String title, @NotNull String description, @NotNull AdaptAdvancementFrame frame) {
        if (unavailable) {
            return;
        }

        Preconditions.checkNotNull(player, "Player is null.");
        Preconditions.checkNotNull(icon, "Icon is null.");
        Preconditions.checkNotNull(title, "Title is null.");
        Preconditions.checkNotNull(frame, "AdvancementFrameType is null.");
        Preconditions.checkArgument(icon.getType() != Material.AIR, "ItemStack is air.");

        try {
            MinecraftKeyWrapper rootKey = MinecraftKeyWrapper.craft("com.fren_gor", "root");
            MinecraftKeyWrapper notificationKey = MinecraftKeyWrapper.craft("com.fren_gor", "notification");
            AdvancementDisplayWrapper rootDisplay = AdvancementDisplayWrapper.craft(
                    new ItemStack(Material.GRASS_BLOCK),
                    "§f§lNotifications§1§2§3§4§5§6§7§8§9§0",
                    "§7Notification page.\n§7Close and reopen advancements to hide.",
                    AdvancementFrameTypeWrapper.TASK,
                    0,
                    0,
                    "textures/block/stone.png"
            );
            AdvancementWrapper root = AdvancementWrapper.craftRootAdvancement(rootKey, rootDisplay, 1);
            AdvancementDisplayWrapper display = AdvancementDisplayWrapper.craft(icon, title, description, frame.toUaaFrame().getNMSWrapper(), 1, 0, true, false, false);
            AdvancementWrapper notification = AdvancementWrapper.craftBaseAdvancement(notificationKey, root, display, 1);
            PacketPlayOutAdvancementsWrapper.craftSendPacket(Map.of(
                    root, 1,
                    notification, 1
            )).sendTo(player);
            PacketPlayOutAdvancementsWrapper.craftRemovePacket(Set.of(rootKey, notificationKey)).sendTo(player);
        } catch (Throwable e) {
            unavailable = true;
            warnOnce(e);
        }
    }

    private static void warnOnce(Throwable throwable) {
        if (warned) {
            return;
        }

        warned = true;
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        Adapt.warn("Advancement notifications are unavailable: " + Objects.toString(root.getMessage(), root.getClass().getSimpleName()));
    }
}
