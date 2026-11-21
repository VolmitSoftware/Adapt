package art.arcane.adapt.clientui;

import art.arcane.adapt.clientui.mixin.ContainerScreenAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class ScreenInfo {
    private static final List<Map.Entry<Class<?>, String>> KINDS = List.of(
            Map.entry(InventoryScreen.class, "InventoryScreen"),
            Map.entry(CreativeModeInventoryScreen.class, "CreativeModeInventoryScreen"),
            Map.entry(ContainerScreen.class, "ContainerScreen"),
            Map.entry(CraftingScreen.class, "CraftingScreen"),
            Map.entry(AbstractContainerScreen.class, "AbstractContainerScreen"),
            Map.entry(ChatScreen.class, "ChatScreen"),
            Map.entry(PauseScreen.class, "PauseScreen"),
            Map.entry(TitleScreen.class, "TitleScreen"),
            Map.entry(DeathScreen.class, "DeathScreen"));

    private ScreenInfo() {
    }

    static JsonElement describe(Screen screen) {
        if (screen == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject result = new JsonObject();
        result.addProperty("class", screen.getClass().getName());
        JsonArray kinds = new JsonArray();
        for (Map.Entry<Class<?>, String> kind : KINDS) {
            if (kind.getKey().isInstance(screen)) {
                kinds.add(kind.getValue());
            }
        }
        result.add("kinds", kinds);
        result.addProperty("title", screen.getTitle().getString());
        result.addProperty("width", screen.width);
        result.addProperty("height", screen.height);
        JsonArray widgets = new JsonArray();
        for (GuiEventListener child : screen.children()) {
            JsonObject widget = new JsonObject();
            widget.addProperty("class", child.getClass().getName());
            if (child instanceof AbstractWidget component) {
                widget.addProperty("x", component.getX());
                widget.addProperty("y", component.getY());
                widget.addProperty("width", component.getWidth());
                widget.addProperty("height", component.getHeight());
                widget.addProperty("message", component.getMessage().getString());
                widget.addProperty("visible", component.visible);
                widget.addProperty("active", component.active);
            }
            widgets.add(widget);
        }
        result.add("widgets", widgets);
        if (screen instanceof AbstractContainerScreen<?> container) {
            ContainerScreenAccessor accessor = (ContainerScreenAccessor) container;
            result.addProperty("left", accessor.adaptUiLeftPos());
            result.addProperty("top", accessor.adaptUiTopPos());
            JsonArray slots = new JsonArray();
            for (Slot slot : container.getMenu().slots) {
                JsonObject entry = new JsonObject();
                entry.addProperty("index", slot.index);
                entry.addProperty("x", accessor.adaptUiLeftPos() + slot.x + 8);
                entry.addProperty("y", accessor.adaptUiTopPos() + slot.y + 8);
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    entry.addProperty("item", String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
                    entry.addProperty("count", stack.getCount());
                    entry.addProperty("name", stack.getHoverName().getString());
                }
                slots.add(entry);
            }
            result.add("slots", slots);
        }
        return result;
    }

    static boolean matches(JsonElement screen, String expectedClass, String expectedTitle, boolean none) {
        if (none) {
            return screen == null || screen.isJsonNull();
        }
        if (screen == null || !screen.isJsonObject()) {
            return false;
        }
        JsonObject object = screen.getAsJsonObject();
        String type = object.get("class").getAsString();
        boolean classMatches = expectedClass == null || type.equals(expectedClass) || type.endsWith("." + expectedClass)
                || hasKind(object, expectedClass);
        boolean titleMatches = expectedTitle == null || object.get("title").getAsString().contains(expectedTitle);
        return classMatches && titleMatches;
    }

    private static boolean hasKind(JsonObject screen, String kind) {
        JsonElement kinds = screen.get("kinds");
        if (kinds == null || !kinds.isJsonArray()) {
            return false;
        }
        for (JsonElement entry : kinds.getAsJsonArray()) {
            if (entry.getAsString().equals(kind)) {
                return true;
            }
        }
        return false;
    }
}
