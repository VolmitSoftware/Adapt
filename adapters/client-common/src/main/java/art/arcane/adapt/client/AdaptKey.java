package art.arcane.adapt.client;

/**
 * The client key bindings, listed under the "Adapt" category in Controls. Open skills defaults to Shift + /; the others
 * start unbound. Each version's {@code ClientPlatform} maps these to its own key codes.
 */
public enum AdaptKey {
    OPEN_SKILLS("key.adapt.open_skills", true),
    OPEN_HUD_EDITOR("key.adapt.open_hud_editor", false),
    TOGGLE_HUD("key.adapt.toggle_hud", false);

    private final String translationKey;
    private final boolean shiftByDefault;

    AdaptKey(String translationKey, boolean shiftByDefault) {
        this.translationKey = translationKey;
        this.shiftByDefault = shiftByDefault;
    }

    public String translationKey() {
        return translationKey;
    }

    /** Whether the default binding is Shift plus the default key; see {@link ShiftChord}. */
    public boolean shiftByDefault() {
        return shiftByDefault;
    }
}
