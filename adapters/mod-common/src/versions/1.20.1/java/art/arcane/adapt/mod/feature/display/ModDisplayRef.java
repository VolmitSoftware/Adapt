package art.arcane.adapt.mod.feature.display;

import art.arcane.adapt.mod.feature.mixin.DisplayDataAccess;
import art.arcane.adapt.mod.platform.entity.ModEntityRef;
import art.arcane.adapt.platform.entity.display.Billboard;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.entity.display.DisplayRef;
import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** A live vanilla display entity; every setter writes the synced data the client renders from. */
class ModDisplayRef<E extends Display> extends ModEntityRef implements DisplayRef {
    protected final E display;

    ModDisplayRef(E display) {
        super(display);
        this.display = display;
    }

    protected final SynchedEntityData synced() {
        return display.getEntityData();
    }

    @Override
    public Transform transform() {
        SynchedEntityData data = synced();
        return new Transform(vec(data.get(DisplayDataAccess.adapt$translation())), quat(data.get(DisplayDataAccess.adapt$leftRotation())),
                vec(data.get(DisplayDataAccess.adapt$scale())), quat(data.get(DisplayDataAccess.adapt$rightRotation())));
    }

    @Override
    public void setTransform(Transform transform) {
        SynchedEntityData data = synced();
        data.set(DisplayDataAccess.adapt$translation(), vector(transform.translation()));
        data.set(DisplayDataAccess.adapt$leftRotation(), quaternion(transform.leftRotation()));
        data.set(DisplayDataAccess.adapt$scale(), vector(transform.scale()));
        data.set(DisplayDataAccess.adapt$rightRotation(), quaternion(transform.rightRotation()));
    }

    @Override
    public Billboard billboard() {
        byte id = synced().get(DisplayDataAccess.adapt$billboard());
        Billboard[] values = Billboard.values();
        return id >= 0 && id < values.length ? values[id] : Billboard.FIXED;
    }

    @Override
    public void setBillboard(Billboard billboard) {
        synced().set(DisplayDataAccess.adapt$billboard(), (byte) billboard.ordinal());
    }

    @Override
    public Brightness brightness() {
        int packed = synced().get(DisplayDataAccess.adapt$brightness());
        if (packed == -1) {
            return null;
        }
        net.minecraft.util.Brightness brightness = net.minecraft.util.Brightness.unpack(packed);
        return new Brightness(brightness.block(), brightness.sky());
    }

    @Override
    public void setBrightness(Brightness brightness) {
        int packed = brightness == null ? -1 : new net.minecraft.util.Brightness(brightness.block(), brightness.sky()).pack();
        synced().set(DisplayDataAccess.adapt$brightness(), packed);
    }

    @Override
    public float viewRange() {
        return synced().get(DisplayDataAccess.adapt$viewRange());
    }

    @Override
    public void setViewRange(float range) {
        synced().set(DisplayDataAccess.adapt$viewRange(), range);
    }

    @Override
    public float shadowRadius() {
        return synced().get(DisplayDataAccess.adapt$shadowRadius());
    }

    @Override
    public void setShadowRadius(float radius) {
        synced().set(DisplayDataAccess.adapt$shadowRadius(), radius);
    }

    @Override
    public Rgb glowColorOverride() {
        int packed = synced().get(DisplayDataAccess.adapt$glowColor());
        return packed == -1 ? null : Rgb.of(packed);
    }

    @Override
    public void setGlowColorOverride(Rgb color) {
        synced().set(DisplayDataAccess.adapt$glowColor(), color == null ? -1 : color.packed());
    }

    @Override
    public int interpolationDuration() {
        return synced().get(DisplayDataAccess.adapt$interpolationDuration());
    }

    @Override
    public void setInterpolationDuration(int ticks) {
        synced().set(DisplayDataAccess.adapt$interpolationDuration(), ticks);
    }

    @Override
    public int interpolationDelay() {
        return synced().get(DisplayDataAccess.adapt$interpolationDelay());
    }

    @Override
    public void setInterpolationDelay(int ticks) {
        synced().set(DisplayDataAccess.adapt$interpolationDelay(), ticks, true);
    }

    /** Displays on 1.20.1 have no teleport interpolation; they jump to each new position. */
    @Override
    public int teleportDuration() {
        return 0;
    }

    @Override
    public void setTeleportDuration(int ticks) {
    }

    @Override
    public boolean isVisibleByDefault() {
        return DisplayVisibility.isVisibleByDefault(display);
    }

    @Override
    public void setVisibleByDefault(boolean visible) {
        DisplayVisibility.setVisibleByDefault(display, visible);
    }

    private static Vec3 vec(Vector3fc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static Quat quat(Quaternionfc quaternion) {
        return new Quat(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
    }

    private static Vector3f vector(Vec3 vector) {
        return new Vector3f((float) vector.x(), (float) vector.y(), (float) vector.z());
    }

    private static Quaternionf quaternion(Quat quat) {
        return new Quaternionf(quat.x(), quat.y(), quat.z(), quat.w());
    }
}
