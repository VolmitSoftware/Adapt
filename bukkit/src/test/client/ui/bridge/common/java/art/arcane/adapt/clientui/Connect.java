package art.arcane.adapt.clientui;

import net.minecraft.client.Minecraft;

final class Connect {
    private Connect() {
    }

    static void to(Minecraft client, String address) {
        throw new UnsupportedOperationException("connect is only bridged on 26.x clients");
    }
}
