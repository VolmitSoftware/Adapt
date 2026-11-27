package art.arcane.adapt.client.assets;

/** The game's side of received art: what its resource packs hold and the textures it draws. Client thread only. */
public interface AssetTextures {
    AssetTextures NONE = new AssetTextures() {
        @Override
        public boolean local(String id) {
            return false;
        }

        @Override
        public boolean register(String id, byte[] png) {
            return false;
        }

        @Override
        public void release(String id) {
        }
    };

    /** Whether a resource pack or a mod already provides {@code id}. */
    boolean local(String id);

    /** Makes the PNG drawable as texture {@code id}; false when it cannot be decoded. */
    boolean register(String id, byte[] png);

    void release(String id);
}
