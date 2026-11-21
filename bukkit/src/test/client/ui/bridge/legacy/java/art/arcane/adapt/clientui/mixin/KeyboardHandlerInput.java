package art.arcane.adapt.clientui.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInput {
    @Invoker("keyPress")
    void adaptUiKey(long window, int key, int scancode, int action, int modifiers);

    @Invoker("charTyped")
    void adaptUiChar(long window, int codepoint, int modifiers);
}
