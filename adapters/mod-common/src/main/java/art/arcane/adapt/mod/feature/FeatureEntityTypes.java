package art.arcane.adapt.mod.feature;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;

/** Vanilla entity types by id; the static constants moved between classes across the supported versions. */
public final class FeatureEntityTypes {
    private FeatureEntityTypes() {
    }

    @SuppressWarnings("unchecked")
    public static EntityType<Display.ItemDisplay> itemDisplay() {
        return (EntityType<Display.ItemDisplay>) type("item_display");
    }

    @SuppressWarnings("unchecked")
    public static EntityType<Display.BlockDisplay> blockDisplay() {
        return (EntityType<Display.BlockDisplay>) type("block_display");
    }

    @SuppressWarnings("unchecked")
    public static EntityType<Display.TextDisplay> textDisplay() {
        return (EntityType<Display.TextDisplay>) type("text_display");
    }

    @SuppressWarnings("unchecked")
    public static EntityType<Interaction> interaction() {
        return (EntityType<Interaction>) type("interaction");
    }

    public static EntityType<?> player() {
        return type("player");
    }

    private static EntityType<?> type(String path) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(path));
    }
}
