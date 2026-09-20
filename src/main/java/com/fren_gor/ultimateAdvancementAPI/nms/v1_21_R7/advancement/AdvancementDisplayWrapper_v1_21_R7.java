package com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.advancement;

import com.fren_gor.ultimateAdvancementAPI.nms.v1_21_R7.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementFrameTypeWrapper;
import net.md_5.bungee.api.chat.BaseComponent;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

public class AdvancementDisplayWrapper_v1_21_R7 extends AdvancementDisplayWrapper {
    private static final MethodHandle ICON = accessor("icon", "getIcon", ItemStackTemplate.class);
    private static final MethodHandle TITLE = accessor("title", "getTitle", Component.class);
    private static final MethodHandle DESCRIPTION = accessor("description", "getDescription", Component.class);
    private static final MethodHandle BACKGROUND = accessor("background", "getBackground", Optional.class);
    private static final MethodHandle SHOW_TOAST = accessor("showToast", "shouldShowToast", boolean.class);
    private static final MethodHandle ANNOUNCE_CHAT = accessor("announceToChat", "shouldAnnounceChat", boolean.class);
    private static final MethodHandle HIDDEN = accessor("hidden", "isHidden", boolean.class);
    private static final MethodHandle SET_LOCATION = locationSetter();

    /**
     * CraftItemStack.asBukkitCopy takes the NMS ItemStack on 26.1.x; on 26.2+ that
     * overload is private and the public one takes ItemInstance (an ItemStack
     * supertype there). Probed once and cached; both parameter classes exist on both
     * versions, so the probe itself always links.
     */
    private static volatile MethodHandle asBukkitCopy;

    private final DisplayInfo display;
    private final float x;
    private final float y;
    private final AdvancementFrameTypeWrapper frameType;

    public AdvancementDisplayWrapper_v1_21_R7(@NotNull ItemStack icon, @NotNull String title, @NotNull String description, @NotNull AdvancementFrameTypeWrapper frameType, float x, float y, boolean showToast, boolean announceChat, boolean hidden, @Nullable String backgroundTexture) {
        ClientAsset.ResourceTexture clientAsset = Util.parseBackgroundTexture(backgroundTexture);
        this.display = new DisplayInfo(ItemStackTemplate.fromNonEmptyStack(CraftItemStack.asNMSCopy(icon)), Util.fromString(title), Util.fromString(description), Optional.ofNullable(clientAsset), (AdvancementType) frameType.toNMS(), showToast, announceChat, hidden);
        this.x = x;
        this.y = y;
        setLocation(x, y);
        this.frameType = frameType;
    }

    public AdvancementDisplayWrapper_v1_21_R7(@NotNull ItemStack icon, @NotNull BaseComponent title, @NotNull BaseComponent description, @NotNull AdvancementFrameTypeWrapper frameType, float x, float y, boolean showToast, boolean announceChat, boolean hidden, @Nullable String backgroundTexture) {
        ClientAsset.ResourceTexture clientAsset = Util.parseBackgroundTexture(backgroundTexture);
        this.display = new DisplayInfo(ItemStackTemplate.fromNonEmptyStack(CraftItemStack.asNMSCopy(icon)), Util.fromComponent(title), Util.fromComponent(description), Optional.ofNullable(clientAsset), (AdvancementType) frameType.toNMS(), showToast, announceChat, hidden);
        this.x = x;
        this.y = y;
        setLocation(x, y);
        this.frameType = frameType;
    }

    @Override
    @NotNull
    public ItemStack getIcon() {
        try {
            MethodHandle handle = asBukkitCopy;
            if (handle == null) {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                try {
                    handle = lookup.findStatic(CraftItemStack.class, "asBukkitCopy",
                            MethodType.methodType(ItemStack.class, net.minecraft.world.item.ItemInstance.class));
                } catch (NoSuchMethodException | IllegalAccessException absent) {
                    handle = lookup.findStatic(CraftItemStack.class, "asBukkitCopy",
                            MethodType.methodType(ItemStack.class, net.minecraft.world.item.ItemStack.class));
                }
                asBukkitCopy = handle;
            }
            return (ItemStack) handle.invoke(((ItemStackTemplate) read(ICON)).create());
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to copy the advancement icon.", error);
        }
    }

    @Override
    @NotNull
    public String getTitle() {
        return CraftChatMessage.fromComponent((Component) read(TITLE));
    }

    @Override
    @NotNull
    public String getDescription() {
        return CraftChatMessage.fromComponent((Component) read(DESCRIPTION));
    }

    @Override
    @NotNull
    public AdvancementFrameTypeWrapper getAdvancementFrameType() {
        return frameType;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public boolean doesShowToast() {
        return (boolean) read(SHOW_TOAST);
    }

    @Override
    public boolean doesAnnounceToChat() {
        return (boolean) read(ANNOUNCE_CHAT);
    }

    @Override
    public boolean isHidden() {
        return (boolean) read(HIDDEN);
    }

    @Override
    @Nullable
    public String getBackgroundTexture() {
        @SuppressWarnings("unchecked")
        Optional<ClientAsset.ResourceTexture> texture = (Optional<ClientAsset.ResourceTexture>) read(BACKGROUND);
        return texture.isEmpty() ? null : texture.get().texturePath().toString();
    }

    @Override
    @NotNull
    public DisplayInfo toNMS() {
        return display;
    }

    private static MethodHandle accessor(String recordName, String getterName, Class<?> resultType) {
        try {
            return MethodHandles.publicLookup().findVirtual(DisplayInfo.class,
                    DisplayInfo.class.isRecord() ? recordName : getterName, MethodType.methodType(resultType));
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static MethodHandle locationSetter() {
        if (DisplayInfo.class.isRecord()) {
            return null;
        }
        try {
            return MethodHandles.publicLookup().findVirtual(DisplayInfo.class, "setLocation",
                    MethodType.methodType(void.class, float.class, float.class));
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private void setLocation(float x, float y) {
        if (SET_LOCATION == null) {
            return;
        }
        try {
            SET_LOCATION.invoke(display, x, y);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to position the advancement display.", error);
        }
    }

    private Object read(MethodHandle accessor) {
        try {
            return accessor.invoke(display);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to read the advancement display.", error);
        }
    }
}
