package com.fren_gor.ultimateAdvancementAPI.util;

import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.fren_gor.ultimateAdvancementAPI.exceptions.AsyncExecutionException;
import com.fren_gor.ultimateAdvancementAPI.exceptions.UserNotLoadedException;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.VanillaAdvancementDisablerWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementFrameTypeWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import com.google.common.base.Preconditions;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdvancementUtils {

    /**
     * The {@code show_advancement_messages} game rule (previously called {@code announceAdvancements}).
     */
    public static final GameRule<Boolean> SHOW_ADVANCEMENT_MESSAGES_GAMERULE = getShowAdvancementMessagesGamerule();

    public static final MinecraftKeyWrapper ROOT_KEY, NOTIFICATION_KEY;
    private static final String ADV_DESCRIPTION = "\n§7A notification.";
    private static final AdvancementWrapper ROOT;

    static {
        try {
            ROOT_KEY = MinecraftKeyWrapper.craft("com.fren_gor", "root");
            NOTIFICATION_KEY = MinecraftKeyWrapper.craft("com.fren_gor", "notification");
            AdvancementDisplayWrapper display = AdvancementDisplayWrapper.craft(new ItemStack(Material.GRASS_BLOCK), "§f§lNotifications§1§2§3§4§5§6§7§8§9§0", "§7Notification page.\n§7Close and reopen advancements to hide.", AdvancementFrameTypeWrapper.TASK, 0, 0, "textures/block/stone.png");
            ROOT = AdvancementWrapper.craftRootAdvancement(ROOT_KEY, display, 1);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Displays a custom toast to a player.
     *
     * @param player A player to show the toast.
     * @param icon The displayed item of the toast.
     * @param title The displayed title of the toast.
     * @param frame The {@link AdvancementFrameType} of the toast.
     * @see UltimateAdvancementAPI#displayCustomToast(Player, ItemStack, String, AdvancementFrameType)
     */
    public static void displayToast(@NotNull Player player, @NotNull ItemStack icon, @NotNull String title, @NotNull AdvancementFrameType frame) {
        Preconditions.checkNotNull(player, "Player is null.");
        Preconditions.checkNotNull(icon, "Icon is null.");
        Preconditions.checkNotNull(title, "Title is null.");
        Preconditions.checkNotNull(frame, "AdvancementFrameType is null.");
        Preconditions.checkArgument(icon.getType() != Material.AIR, "ItemStack is air.");

        try {
            AdvancementDisplayWrapper display = AdvancementDisplayWrapper.craft(icon, title, ADV_DESCRIPTION, frame.getNMSWrapper(), 1, 0, true, false, false);
            AdvancementWrapper notification = AdvancementWrapper.craftBaseAdvancement(NOTIFICATION_KEY, ROOT, display, 1);
            PacketPlayOutAdvancementsWrapper.craftSendPacket(Map.of(
                    ROOT, 1,
                    notification, 1
            )).sendTo(player);
            PacketPlayOutAdvancementsWrapper.craftRemovePacket(Set.of(ROOT_KEY, NOTIFICATION_KEY)).sendTo(player);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    /*public static void displayToast(@NotNull Player player, @NotNull ItemStack icon, @NotNull String title, @NotNull AdvancementFrameType frame, @NotNull Advancement base) {
        Preconditions.checkNotNull(player, "Player is null.");
        Preconditions.checkNotNull(icon, "Icon is null.");
        Preconditions.checkNotNull(title, "Title is null.");
        Preconditions.checkNotNull(frame, "AdvancementFrameType is null.");
        Preconditions.checkNotNull(base, "Advancement is null.");
        Preconditions.checkArgument(base.isValid(), "Advancement isn't valid.");
        Preconditions.checkArgument(icon.getType() != Material.AIR, "ItemStack is air.");

        final MinecraftKeyWrapper key = getUniqueKey(base.getAdvancementTab()).getNMSWrapper();

        try {
            AdvancementDisplayWrapper display = AdvancementDisplayWrapper.craft(icon, title, ADV_DESCRIPTION, frame.getNMSWrapper(), base.getDisplay().getX() + 1, base.getDisplay().getY(), true, false, false);
            AdvancementWrapper adv = AdvancementWrapper.craftBaseAdvancement(key, base.getNMSWrapper(), display, 1);

            PacketPlayOutSelectAdvancementTabWrapper.craftSelectNone().sendTo(player);
            PacketPlayOutAdvancementsWrapper.craftSendPacket(Map.of(adv, 1)).sendTo(player);
            PacketPlayOutAdvancementsWrapper.craftRemovePacket(Set.of(key)).sendTo(player);
            PacketPlayOutSelectAdvancementTabWrapper.craftSelect(key).sendTo(player);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }*/

    public static void displayToastDuringUpdate(@NotNull Player player, @NotNull Advancement advancement) {
        Preconditions.checkNotNull(player, "Player is null.");
        Preconditions.checkNotNull(advancement, "Advancement is null.");
        Preconditions.checkArgument(advancement.isValid(), "Advancement isn't valid.");

        final AdvancementDisplay display = advancement.getDisplay();
        final MinecraftKeyWrapper keyWrapper = getUniqueKey(advancement.getAdvancementTab()).getNMSWrapper();

        try {
            AdvancementDisplayWrapper displayWrapper = AdvancementDisplayWrapper.craft(display.getIcon(), display.getTitle(), ADV_DESCRIPTION, display.getFrame().getNMSWrapper(), 0, 0, true, false, false);
            AdvancementWrapper advWrapper = AdvancementWrapper.craftBaseAdvancement(keyWrapper, advancement.getNMSWrapper(), displayWrapper, 1);

            PacketPlayOutAdvancementsWrapper.craftSendPacket(Map.of(advWrapper, 1)).sendTo(player);
            PacketPlayOutAdvancementsWrapper.craftRemovePacket(Set.of(keyWrapper)).sendTo(player);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static AdvancementKey getUniqueKey(@NotNull AdvancementTab tab) {
        final String namespace = tab.getNamespace();
        StringBuilder builder = new StringBuilder("i");
        AdvancementKey key;
        while (tab.getAdvancement(key = new AdvancementKey(namespace, builder.toString())) != null) {
            builder.append('i');
        }
        return key;
    }

    /**
     * Disables vanilla advancements.
     * 
     * @throws Exception If disabling fails.
     * @see UltimateAdvancementAPI#disableVanillaAdvancements()
     */
    public static void disableVanillaAdvancements() throws Exception {
        VanillaAdvancementDisablerWrapper.disableVanillaAdvancements(true, false);
    }

    /**
     * Disables vanilla recipe advancements (i.e. the advancements which unlock recipes).
     *
     * @throws Exception If disabling fails.
     * @see UltimateAdvancementAPI#disableVanillaRecipeAdvancements()
     */
    public static void disableVanillaRecipeAdvancements() throws Exception {
        VanillaAdvancementDisablerWrapper.disableVanillaAdvancements(false, true);
    }

    @NotNull
    public static BaseComponent[] fromStringList(@NotNull List<String> list) {
        return fromStringList(null, list);
    }

    @NotNull
    public static BaseComponent[] fromStringList(@Nullable String title, @NotNull List<String> list) {
        Preconditions.checkNotNull(list);
        ComponentBuilder builder = new ComponentBuilder();
        if (title != null) {
            builder.append(TextComponent.fromLegacyText(title), FormatRetention.NONE);
            if (list.isEmpty()) {
                return builder.create();
            }
            builder.append("\n", FormatRetention.NONE);
        } else if (list.isEmpty()) {
            return builder.create();
        }
        int i = 0;
        for (String s : list) {
            builder.append(TextComponent.fromLegacyText(s), FormatRetention.NONE);
            if (++i < list.size()) // Don't append \n at the end
                builder.append("\n", FormatRetention.NONE);
        }
        return builder.create();
    }

    public static boolean startsWithEmptyLine(@NotNull String text) {
        Preconditions.checkNotNull(text);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++; // Skip next character since it is a color code
            } else {
                return c == '\n';
            }
        }
        return false;
    }

    @Contract("_ -> param1")
    public static int validateProgressionValue(int progression) {
        if (progression < 0) {
            throw new IllegalArgumentException("Progression value cannot be < 0");
        }
        return progression;
    }

    public static void validateProgressionValueStrict(int progression, int maxProgression) {
        validateProgressionValue(progression);
        if (progression > maxProgression) {
            throw new IllegalArgumentException("Progression value cannot be greater than the maximum progression (" + maxProgression + ')');
        }
    }

    public static void validateIncrement(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("Increment cannot be zero or less.");
        }
    }

    @Contract("null -> fail; !null -> param1")
    public static TeamProgression validateTeamProgression(TeamProgression pro) {
        Preconditions.checkNotNull(pro, "TeamProgression is null.");
        Preconditions.checkArgument(pro.isValid(), "Invalid TeamProgression.");
        return pro;
    }

    public static void checkTeamProgressionNotNull(TeamProgression progression) {
        if (progression == null) {
            throw new UserNotLoadedException();
        }
    }

    public static void checkTeamProgressionNotNull(TeamProgression progression, UUID uuid) {
        if (progression == null) {
            throw new UserNotLoadedException(uuid);
        }
    }

    public static void checkSync() {
        if (J.isFoliaThreading() || hasFoliaScheduler()) {
            return;
        }

        if (!Bukkit.isPrimaryThread())
            throw new AsyncExecutionException("Illegal async method call. This method can be called only from the main thread.");
    }

    public static void runSync(@NotNull AdvancementMain main, @NotNull Runnable runnable) {
        runSync(main.getOwningPlugin(), runnable);
    }

    public static void runSync(@NotNull Plugin plugin, @NotNull Runnable runnable) {
        runSync(plugin, 1, runnable);
    }

    public static void runSync(@NotNull AdvancementMain main, long delay, @NotNull Runnable runnable) {
        runSync(main.getOwningPlugin(), delay, runnable);
    }

    public static void runSync(@NotNull Plugin plugin, long delay, @NotNull Runnable runnable) {
        Preconditions.checkNotNull(plugin, "Plugin is null.");
        Preconditions.checkNotNull(runnable, "Runnable is null.");
        int safeDelay = sanitizeDelay(delay);
        if (hasFoliaScheduler()) {
            if (scheduleFoliaSync(plugin, runnable, safeDelay)) {
                return;
            }

            if (safeDelay <= 0) {
                J.s(runnable);
            } else {
                J.s(runnable, safeDelay);
            }
            return;
        }

        try {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable, safeDelay);
        } catch (UnsupportedOperationException ex) {
            // Folia rejects BukkitScheduler sync calls; retry with Adapt's Folia-aware scheduler path.
            if (safeDelay <= 0) {
                J.s(runnable);
            } else {
                J.s(runnable, safeDelay);
            }
        }
    }

    private static int sanitizeDelay(long delay) {
        if (delay <= 0) {
            return 0;
        }

        if (delay >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) delay;
    }

    private static boolean scheduleFoliaSync(@NotNull Plugin plugin, @NotNull Runnable runnable, int safeDelay) {
        Player player = extractPlayer(runnable);
        if (player != null && player.isOnline() && FoliaScheduler.runEntity(plugin, player, runnable, safeDelay)) {
            return true;
        }

        return FoliaScheduler.runGlobal(plugin, runnable, safeDelay);
    }

    private static boolean hasFoliaScheduler() {
        return FoliaScheduler.isFolia(Bukkit.getServer());
    }

    @Nullable
    private static Player extractPlayer(@NotNull Runnable runnable) {
        Class<?> current = runnable.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(runnable);
                    Player player = asPlayer(value);
                    if (player != null) {
                        return player;
                    }
                } catch (Throwable ex) {
                    // Ignore inaccessible synthetic fields while walking runnable captures.
                }
            }

            current = current.getSuperclass();
        }

        return null;
    }

    @Nullable
    private static Player asPlayer(@Nullable Object value) {
        if (value instanceof Player player) {
            return player;
        }

        if (value instanceof OfflinePlayer offlinePlayer) {
            return offlinePlayer.getPlayer();
        }

        if (value instanceof UUID uuid) {
            return Bukkit.getPlayer(uuid);
        }

        return null;
    }

    @NotNull
    public static UUID uuidFromPlayer(@NotNull Player player) {
        Preconditions.checkNotNull(player, "Player is null.");
        return player.getUniqueId();
    }

    @NotNull
    public static UUID uuidFromPlayer(@NotNull OfflinePlayer player) {
        Preconditions.checkNotNull(player, "OfflinePlayer is null.");
        return player.getUniqueId();
    }

    @NotNull
    public static TeamProgression progressionFromPlayer(@NotNull Player player, @NotNull Advancement advancement) {
        return progressionFromPlayer(player, advancement.getAdvancementTab());
    }

    @NotNull
    public static TeamProgression progressionFromUUID(@NotNull UUID uuid, @NotNull Advancement advancement) {
        return progressionFromUUID(uuid, advancement.getAdvancementTab());
    }

    @NotNull
    public static TeamProgression progressionFromPlayer(@NotNull Player player, @NotNull AdvancementTab tab) {
        return progressionFromUUID(uuidFromPlayer(player), tab);
    }

    @NotNull
    public static TeamProgression progressionFromUUID(@NotNull UUID uuid, @NotNull AdvancementTab tab) {
        Preconditions.checkNotNull(uuid, "UUID is null.");
        return tab.getDatabaseManager().getTeamProgression(uuid);
    }

    @SuppressWarnings("unchecked")
    private static GameRule<Boolean> getShowAdvancementMessagesGamerule() {
        try {
            // Spigot 1.21.11+
            return (GameRule<Boolean>) GameRule.class.getDeclaredField("SHOW_ADVANCEMENT_MESSAGES").get(null);
        } catch (NoSuchFieldException e) {
            // Spigot <= 1.21.10, Paper all versions
            try {
                return (GameRule<Boolean>) GameRule.class.getDeclaredField("ANNOUNCE_ADVANCEMENTS").get(null);
            } catch (NoSuchFieldException ex) {
                return null;
            } catch (ReflectiveOperationException inner) {
                throw new RuntimeException(inner);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private AdvancementUtils() {
        throw new UnsupportedOperationException("Utility class.");
    }
}
