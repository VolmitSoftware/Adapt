package art.arcane.adapt.mod.feature.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(RecipeMap.class)
public interface RecipeMapAccess {
    @Accessor("byType")
    Multimap<RecipeType<?>, RecipeHolder<?>> adapt$byType();

    @Invoker("<init>")
    static RecipeMap adapt$create(Multimap<RecipeType<?>, RecipeHolder<?>> byType, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
