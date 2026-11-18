package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(AttributeMap.class)
public interface AttributeMapAccess {
    @Accessor("attributes")
    Map<Holder<Attribute>, AttributeInstance> adapt$attributes();
}
