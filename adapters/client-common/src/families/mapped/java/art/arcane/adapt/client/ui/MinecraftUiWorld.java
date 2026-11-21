package art.arcane.adapt.client.ui;

import art.arcane.adapt.mod.platform.mixin.client.GameRendererFovAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** {@link UiWorld} over the running client's level and main camera. */
public final class MinecraftUiWorld implements UiWorld {
    @Override
    public UiCamera camera(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (minecraft.level == null || !camera.isInitialized()) {
            return null;
        }
        Vec3 position = camera.getPosition();
        double fov = ((GameRendererFovAccess) minecraft.gameRenderer).adapt$fov(camera, partialTick, true);
        return new UiCamera(position.x, position.y, position.z, camera.getYRot(), camera.getXRot(), fov);
    }

    @Override
    public UiEntityPose entity(int entityId, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null || entity.isRemoved()) {
            return null;
        }
        Vec3 position = entity.getPosition(partialTick);
        return new UiEntityPose(position.x, position.y, position.z, entity.getBbWidth(), entity.getBbHeight());
    }
}
