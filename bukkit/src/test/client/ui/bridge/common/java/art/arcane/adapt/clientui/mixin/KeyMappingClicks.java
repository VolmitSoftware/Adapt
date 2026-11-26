package art.arcane.adapt.clientui.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingClicks {
    @Accessor("clickCount")
    int adaptUiClicks();

    @Accessor("clickCount")
    void adaptUiSetClicks(int clicks);
}
