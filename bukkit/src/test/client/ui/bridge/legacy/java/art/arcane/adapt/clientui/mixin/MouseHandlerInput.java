package art.arcane.adapt.clientui.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInput {
    @Invoker("onPress")
    void adaptUiPress(long window, int button, int action, int modifiers);

    @Invoker("onMove")
    void adaptUiMove(long window, double x, double y);

    @Invoker("onScroll")
    void adaptUiScroll(long window, double horizontal, double vertical);
}
