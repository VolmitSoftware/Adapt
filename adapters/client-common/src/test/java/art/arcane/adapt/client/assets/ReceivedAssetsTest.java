package art.arcane.adapt.client.assets;

import art.arcane.adapt.engine.asset.AssetHashes;
import art.arcane.adapt.client.protocol.AssetRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReceivedAssetsTest {
    private static final String ICON = "alchemist:textures/gui/skill/alchemy.png";
    private static final String KIT = "alchemist:themes/alchemy.json";
    private static final String BUILTIN = "adapt:textures/gui/skill/agility.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JSON = {'{', '}'};

    private final List<String> calls = new ArrayList<>();
    private final ReceivedAssets assets = new ReceivedAssets();

    ReceivedAssetsTest() {
        assets.textures(new AssetTextures() {
            @Override
            public boolean local(String id) {
                return Set.of(BUILTIN).contains(id);
            }

            @Override
            public boolean register(String id, byte[] png) {
                calls.add("register " + id);
                return true;
            }

            @Override
            public void release(String id) {
                calls.add("release " + id);
            }
        });
    }

    @Test
    void aManifestAsksOnlyForWhatNeitherThePacksNorEarlierFilesHold() {
        List<AssetRef> manifest = List.of(new AssetRef(BUILTIN, AssetHashes.sha1(PNG)), new AssetRef(ICON, AssetHashes.sha1(PNG)),
                new AssetRef(KIT, AssetHashes.sha1(JSON)));

        assertEquals(List.of(ICON, KIT), assets.manifest(manifest));
        assets.asset(ICON, AssetHashes.sha1(PNG), PNG);
        assertEquals(List.of(KIT), assets.manifest(manifest));
    }

    @Test
    void texturesAreRegisteredAndEveryFileIsServedByItsId() {
        assets.manifest(List.of(new AssetRef(ICON, AssetHashes.sha1(PNG)), new AssetRef(KIT, AssetHashes.sha1(JSON))));
        int before = assets.revision();

        assets.asset(ICON, AssetHashes.sha1(PNG), PNG);
        assets.asset(KIT, AssetHashes.sha1(JSON), JSON);

        assertArrayEquals(PNG, assets.bytes(ICON));
        assertArrayEquals(JSON, assets.bytes(KIT));
        assertEquals(List.of("register " + ICON), calls);
        assertNotEquals(before, assets.revision());
    }

    @Test
    void aFileThatWasNotAskedForOrDoesNotMatchItsHashIsDropped() {
        assets.manifest(List.of(new AssetRef(ICON, AssetHashes.sha1(PNG))));

        assets.asset(KIT, AssetHashes.sha1(JSON), JSON);
        assets.asset(ICON, AssetHashes.sha1(PNG), JSON);

        assertNull(assets.bytes(KIT));
        assertNull(assets.bytes(ICON));
    }

    @Test
    void resetReleasesTexturesAndForgetsEveryFile() {
        assets.manifest(List.of(new AssetRef(ICON, AssetHashes.sha1(PNG))));
        assets.asset(ICON, AssetHashes.sha1(PNG), PNG);

        assets.reset();

        assertNull(assets.bytes(ICON));
        assertEquals(List.of("register " + ICON, "release " + ICON), calls);
    }
}
