package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.config.UiSettings;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.kit.UiMotion;

import java.util.Objects;
import java.util.function.Supplier;

/** The client's page themes and the book settings: themes on, held still, and inactive skills shown. */
public final class PageThemes {
    private final ThemeLibrary library = new ThemeLibrary();
    private final Supplier<UiSettings> settings;
    private final Runnable save;

    /** Themes as {@code settings} say; {@code save} writes them after a change. */
    public PageThemes(Supplier<UiSettings> settings, Runnable save) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.save = Objects.requireNonNull(save, "save");
    }

    /** Themes on, motion as the settings say, nothing saved; for previews and tests. */
    public static PageThemes defaults() {
        UiSettings settings = new UiSettings();
        return new PageThemes(() -> settings, () -> {
        });
    }

    public boolean enabled() {
        return settings.get().themedPages();
    }

    public void setEnabled(boolean enabled) {
        settings.get().themedPages(enabled);
        save.run();
    }

    /** Whether the reduced motion setting is on, whatever the system's motion. */
    public boolean reducedMotion() {
        return settings.get().reducedMotion();
    }

    public void setReducedMotion(boolean reduced) {
        settings.get().reducedMotion(reduced);
        save.run();
    }

    /** Whether the player wants inactive skills shown greyed out in their seats. */
    public boolean inactiveSkills() {
        return settings.get().inactiveSkills();
    }

    public void setInactiveSkills(boolean shown) {
        settings.get().inactiveSkills(shown);
        save.run();
    }

    /** Whether themes hold still, by the setting or because all motion is reduced. */
    public boolean still(UiMotion motion) {
        return motion.reduced() || settings.get().reducedMotion();
    }

    /** The theme of skill {@code skillId} as the server names it, or null when themes are off or it has none. */
    public PageTheme theme(UiSprites sprites, String skillId) {
        return enabled() ? library.theme(sprites, skillId) : null;
    }
}
