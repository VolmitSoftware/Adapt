package art.arcane.adapt.clientui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

final class TextRuns {
    private TextRuns() {
    }

    static JsonArray describe(Component component) {
        JsonArray runs = new JsonArray();
        component.visit((Style style, String text) -> {
            JsonObject run = new JsonObject();
            run.addProperty("text", text);
            run.addProperty("font", String.valueOf(style.getFont()));
            run.addProperty("color", style.getColor() == null ? null : style.getColor().serialize());
            runs.add(run);
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }
}
