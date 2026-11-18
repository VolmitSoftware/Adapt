package art.arcane.adapt.clientqa.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("searchBox")
    EditBox adaptSearchBox();

    @Accessor("recipeBookPage")
    RecipeBookPage adaptRecipeBookPage();

    @Accessor("filterButton")
    CycleButton<Boolean> adaptFilterButton();

    @Accessor("tabButtons")
    List<RecipeBookTabButton> adaptTabButtons();

    @Invoker("checkSearchStringUpdate")
    void adaptCheckSearchStringUpdate();
}
