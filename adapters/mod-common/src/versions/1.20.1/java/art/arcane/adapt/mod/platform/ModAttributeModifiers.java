package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Attribute modifiers on this version are keyed by UUID, not by id. Adapt derives the UUID from its modifier id, so
 * the same id always maps to the same modifier (also across restarts, for modifiers saved with the player), and
 * stores the id as the modifier name so namespace sweeps can find its modifiers again.
 */
public final class ModAttributeModifiers {
    private ModAttributeModifiers() {
    }

    public static UUID uuid(Id key) {
        return UUID.nameUUIDFromBytes(("adapt:attribute_modifier/" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static AttributeModifier create(Id key, double amount, AttributeOperation operation) {
        return new AttributeModifier(uuid(key), key.toString(), amount, operation(operation));
    }

    /** The modifier id Adapt stored as the name, or null for modifiers whose name is not an id. */
    public static Id key(AttributeModifier modifier) {
        return Id.tryParse(modifier.getName());
    }

    public static AttributeModifier.Operation operation(AttributeOperation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeModifier.Operation.ADDITION;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
        };
    }

    public static AttributeOperation operation(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADDITION -> AttributeOperation.ADD_VALUE;
            case MULTIPLY_BASE -> AttributeOperation.ADD_MULTIPLIED_BASE;
            case MULTIPLY_TOTAL -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
        };
    }
}
