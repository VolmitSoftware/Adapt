package art.arcane.adapt.client;

import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.client.net.ClientNet;
import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.client.ui.RecordingDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiScreen;
import art.arcane.adapt.client.ui.UiWorld;
import art.arcane.adapt.mod.AdaptModIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptClientTest {
    private static final UiHost NO_HOST = new UiHost() {
        @Override
        public void open(UiScreen screen) {
        }

        @Override
        public void close() {
        }

        @Override
        public UiScreen current() {
            return null;
        }

        @Override
        public void playClick() {
        }

        @Override
        public String clipboard() {
            return "";
        }

        @Override
        public void setClipboard(String text) {
        }

        @Override
        public String translate(String key) {
            return key;
        }
    };

    private static final class QueuedKeys implements ClientKeys {
        private final List<AdaptKey> pending = new ArrayList<>();

        @Override
        public void poll(Consumer<AdaptKey> pressed) {
            pending.forEach(pressed);
            pending.clear();
        }
    }

    private static ClientNet offline() {
        return new ClientNet(new ClientTransport() {
            @Override
            public boolean ready() {
                return false;
            }

            @Override
            public void send(byte[] frame) {
            }
        }, ClientConfig.memory());
    }

    @Test
    void debugLineRendersOnlyWhenEnabled() {
        RecordingDraw hidden = new RecordingDraw();
        new AdaptClient(NO_HOST, new QueuedKeys(), offline(), UiWorld.NONE, false).renderHud(hidden, 0.0F);
        assertTrue(hidden.calls().isEmpty());

        RecordingDraw shown = new RecordingDraw();
        new AdaptClient(NO_HOST, new QueuedKeys(), offline(), UiWorld.NONE, true).renderHud(shown, 0.0F);
        String line = "Adapt client " + AdaptModIdentity.VERSION + " (" + AdaptModIdentity.MINECRAFT_TARGET + ")";
        assertEquals(List.of("fill 2,2," + (4 + line.length() * 6 + 2) + ",13 80000000", "text 4,4 ffffffff true " + line), shown.calls());
    }

    @Test
    void toggleHudKeyHidesAndShowsTheHud() {
        QueuedKeys keys = new QueuedKeys();
        AdaptClient client = new AdaptClient(NO_HOST, keys, offline(), UiWorld.NONE, true);
        keys.pending.add(AdaptKey.TOGGLE_HUD);
        client.tick();
        assertFalse(client.hudVisible());
        RecordingDraw draw = new RecordingDraw();
        client.renderHud(draw, 0.0F);
        assertTrue(draw.calls().isEmpty());

        keys.pending.add(AdaptKey.TOGGLE_HUD);
        client.tick();
        assertTrue(client.hudVisible());
    }

    @Test
    void boundKeysRunTheirActionPerPress() {
        QueuedKeys keys = new QueuedKeys();
        AdaptClient client = new AdaptClient(NO_HOST, keys, offline(), UiWorld.NONE, false);
        List<String> opened = new ArrayList<>();
        client.bindKey(AdaptKey.OPEN_SKILLS, () -> opened.add("skills"));
        keys.pending.add(AdaptKey.OPEN_SKILLS);
        keys.pending.add(AdaptKey.OPEN_HUD_EDITOR);
        keys.pending.add(AdaptKey.OPEN_SKILLS);
        client.tick();
        assertEquals(List.of("skills", "skills"), opened);
    }

    @Test
    void connectionStateFollowsTheLoaderEvents() {
        AdaptClient client = new AdaptClient(NO_HOST, new QueuedKeys(), offline(), UiWorld.NONE, false);
        assertFalse(client.isConnected());
        client.connected();
        assertTrue(client.isConnected());
        client.disconnected();
        assertFalse(client.isConnected());
    }
}
