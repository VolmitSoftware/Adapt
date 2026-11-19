package art.arcane.adapt.mod.feature.viewer;

import art.arcane.adapt.platform.entity.EquipmentSlot;

/** Vanilla equipment slots of 1.21.1; the saddle slot arrived in 1.21.5. */
final class ViewerSlots {
    private ViewerSlots() {
    }

    /** The vanilla slot, or null when this version has none. */
    static net.minecraft.world.entity.EquipmentSlot vanilla(EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OFF_HAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case CHEST -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case LEGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case FEET -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case BODY -> net.minecraft.world.entity.EquipmentSlot.BODY;
            case SADDLE -> null;
        };
    }
}
