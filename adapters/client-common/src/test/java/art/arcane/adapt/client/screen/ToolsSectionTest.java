package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.ToolView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolsSectionTest {
    @Test
    void recipeLinesCountEachIngredientAndNameTagsInWords() {
        ToolView whetstone = new ToolView("upgraded-swords-tempest-flow", "tool-swords-whetstone", "Whetstone",
                IconRef.item("minecraft:flint"), 2,
                List.of(new ToolView.Ingredient("minecraft:flint", 2), new ToolView.Ingredient("#minecraft:stone_crafting_materials", 1)));
        assertEquals(List.of("§72 × Flint", "§71 × Stone crafting materials"), ToolsSection.recipe(whetstone));
    }
}
