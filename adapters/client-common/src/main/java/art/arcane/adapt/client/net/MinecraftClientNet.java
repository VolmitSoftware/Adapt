package art.arcane.adapt.client.net;

import art.arcane.adapt.client.assets.MinecraftAssetTextures;
import art.arcane.adapt.client.assets.ReceivedAssets;
import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/** Builds the {@link ClientNet} of a running client and routes the loaders' inbound frames to it. */
public final class MinecraftClientNet {
    private MinecraftClientNet() {
    }

    /** Loads {@code adapt-client.json} from {@code configDirectory}; frames arriving off the client thread hop onto it in order. */
    public static ClientNet create(ClientTransport transport, Path configDirectory) {
        ClientNet net = new ClientNet(transport, ClientConfig.load(configDirectory.resolve(ClientConfig.FILE_NAME)));
        ReceivedAssets.SHARED.textures(new MinecraftAssetTextures());
        net.install(net.sinks().withAssets(ReceivedAssets.SHARED));
        UiClientInbound.handler((byte[] frame) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isSameThread()) {
                net.receive(frame);
            } else {
                minecraft.execute(() -> net.receive(frame));
            }
        });
        return net;
    }
}
