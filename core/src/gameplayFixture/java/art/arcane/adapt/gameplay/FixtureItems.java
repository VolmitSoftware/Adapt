package art.arcane.adapt.gameplay;

import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;

final class FixtureItems {
    private FixtureItems() {
    }

    static String legacy(String markup) {
        if (markup == null || markup.indexOf('<') < 0) {
            return markup;
        }
        return MarkupPort.current().legacy(markup);
    }

    static String material(Id type) {
        return type.path().toUpperCase(Locale.ROOT);
    }

    static String name(ItemRef item) {
        return item.hasDisplayName() ? legacy(item.displayName()) : "";
    }

    static boolean decorated(ItemRef item) {
        return item != null && !item.isEmpty() && (item.hasDisplayName() || !item.lore().isEmpty());
    }

    static int count(PlayerRef player, String type) {
        Id id = Fixtures.id(type);
        int count = 0;
        for (ItemRef item : player.inventory().contents()) {
            if (item != null && !item.isEmpty() && item.type().equals(id)) {
                count += item.amount();
            }
        }
        return count;
    }

    static JsonArray menuSlots(PlayerRef player) {
        JsonArray slots = new JsonArray();
        InventoryRef inventory = player.openMenuInventory();
        if (inventory == null) {
            return slots;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemRef item = inventory.item(slot);
            if (!decorated(item)) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot);
            entry.addProperty("material", material(item.type()));
            entry.addProperty("name", name(item));
            slots.add(entry);
        }
        return slots;
    }
}
