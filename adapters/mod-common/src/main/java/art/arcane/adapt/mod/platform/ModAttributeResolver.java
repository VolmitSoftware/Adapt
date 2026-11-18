package art.arcane.adapt.mod.platform;

import art.arcane.adapt.api.attribute.AdaptAttributeResolver;
import art.arcane.adapt.mod.platform.entity.ModLivingRef;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/** Attribute access that can list modifiers, so modifiers left over from earlier sessions are cleared too. */
public final class ModAttributeResolver implements AdaptAttributeResolver {
    @Override
    public boolean setTransientModifier(LivingRef entity, Id attribute, AttributeMod modifier) {
        return LIVING_REF.setTransientModifier(entity, attribute, modifier);
    }

    @Override
    public void removeModifier(LivingRef entity, Id attribute, Id key) {
        LIVING_REF.removeModifier(entity, attribute, key);
    }

    @Override
    public int removeAllInNamespace(LivingRef entity, Id attribute, String namespace) {
        if (!(entity.unwrap() instanceof LivingEntity living)) {
            return 0;
        }
        AttributeInstance instance = ModLivingRef.existing(living, attribute);
        if (instance == null) {
            return 0;
        }
        List<AttributeModifier> matching = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().getNamespace().equals(namespace)) {
                matching.add(modifier);
            }
        }
        for (AttributeModifier modifier : matching) {
            instance.removeModifier(modifier.id());
        }
        return matching.size();
    }
}
