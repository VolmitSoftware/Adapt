package art.arcane.adapt.clientqa.mixin;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StonecutterScreen.class)
public interface StonecutterScreenAccessor {
    @Accessor("startIndex")
    int adaptStartIndex();

    @Accessor("displayRecipes")
    boolean adaptDisplayRecipes();
}
