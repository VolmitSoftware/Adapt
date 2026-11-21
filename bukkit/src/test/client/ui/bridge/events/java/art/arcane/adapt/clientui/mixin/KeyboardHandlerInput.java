package art.arcane.adapt.clientui.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInput {
    @Invoker("keyPress")
    void adaptUiKey(long window, int action, KeyEvent event);

    @Invoker("charTyped")
    void adaptUiChar(long window, CharacterEvent event);
}
