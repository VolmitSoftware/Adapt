package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayDataAccess {
    @Accessor("DATA_TRANSLATION_ID")
    static EntityDataAccessor<Vector3fc> adapt$translation() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_SCALE_ID")
    static EntityDataAccessor<Vector3fc> adapt$scale() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_LEFT_ROTATION_ID")
    static EntityDataAccessor<Quaternionfc> adapt$leftRotation() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_RIGHT_ROTATION_ID")
    static EntityDataAccessor<Quaternionfc> adapt$rightRotation() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID")
    static EntityDataAccessor<Integer> adapt$interpolationDelay() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID")
    static EntityDataAccessor<Integer> adapt$interpolationDuration() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_POS_ROT_INTERPOLATION_DURATION_ID")
    static EntityDataAccessor<Integer> adapt$teleportDuration() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID")
    static EntityDataAccessor<Byte> adapt$billboard() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_BRIGHTNESS_OVERRIDE_ID")
    static EntityDataAccessor<Integer> adapt$brightness() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_VIEW_RANGE_ID")
    static EntityDataAccessor<Float> adapt$viewRange() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_SHADOW_RADIUS_ID")
    static EntityDataAccessor<Float> adapt$shadowRadius() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_GLOW_COLOR_OVERRIDE_ID")
    static EntityDataAccessor<Integer> adapt$glowColor() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
