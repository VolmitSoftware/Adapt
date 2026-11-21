package art.arcane.adapt.clientui.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInput {
    @Invoker("onButton")
    void adaptUiButton(long window, MouseButtonInfo button, int action);

    @Invoker("onMove")
    void adaptUiMove(long window, double x, double y);

    @Invoker("onScroll")
    void adaptUiScroll(long window, double horizontal, double vertical);
}
