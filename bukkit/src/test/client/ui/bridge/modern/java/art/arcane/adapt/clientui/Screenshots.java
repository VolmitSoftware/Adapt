package art.arcane.adapt.clientui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class Screenshots {
    private Screenshots() {
    }

    static void capture(RenderTarget target, Path path, CompletableFuture<JsonObject> result) {
        Screenshot.takeScreenshot(target, image -> {
            try (NativeImage owned = image) {
                owned.writeToFile(path);
                JsonObject done = UiBridge.ok();
                done.addProperty("path", path.toString());
                done.addProperty("width", owned.getWidth());
                done.addProperty("height", owned.getHeight());
                result.complete(done);
            } catch (IOException | RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
    }
}
