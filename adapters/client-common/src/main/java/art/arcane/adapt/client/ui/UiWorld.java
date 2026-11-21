package art.arcane.adapt.client.ui;

/** The client's world as screens and HUD widgets may read it, without Minecraft types. Called on the render thread. */
public interface UiWorld {
    UiWorld NONE = new UiWorld() {
        @Override
        public UiCamera camera(float partialTick) {
            return null;
        }

        @Override
        public UiEntityPose entity(int entityId, float partialTick) {
            return null;
        }
    };

    /** The camera of the frame being drawn, or null while no level is loaded. */
    UiCamera camera(float partialTick);

    /** The entity with this network id, interpolated to the partial tick; null when the client does not track it. */
    UiEntityPose entity(int entityId, float partialTick);
}
