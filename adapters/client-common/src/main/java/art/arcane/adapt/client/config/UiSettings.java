package art.arcane.adapt.client.config;

/** Section {@code ui} of {@code adapt-client.json}. */
public final class UiSettings {
    private boolean nativeUi = true;

    /** True to offer the native screens, panels and HUD to servers; false keeps the vanilla chest and action bar UI. */
    public boolean nativeUi() {
        return nativeUi;
    }

    public void nativeUi(boolean enabled) {
        nativeUi = enabled;
    }
}
