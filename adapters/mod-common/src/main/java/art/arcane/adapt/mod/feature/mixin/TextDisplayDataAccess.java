package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayDataAccess {
    @Accessor("DATA_TEXT_ID")
    static EntityDataAccessor<Component> adapt$text() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_LINE_WIDTH_ID")
    static EntityDataAccessor<Integer> adapt$lineWidth() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_BACKGROUND_COLOR_ID")
    static EntityDataAccessor<Integer> adapt$background() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_STYLE_FLAGS_ID")
    static EntityDataAccessor<Byte> adapt$styleFlags() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
