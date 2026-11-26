package art.arcane.adapt.client.config;

/** Section {@code ui} of {@code adapt-client.json}. */
public final class UiSettings {
    private boolean nativeUi = true;
    private boolean themedPages = true;
    private boolean reducedMotion = false;
    private boolean inactiveSkills = true;

    /** True to offer the native screens, panels and HUD to servers; false keeps the vanilla chest and action bar UI. */
    public boolean nativeUi() {
        return nativeUi;
    }

    public void nativeUi(boolean enabled) {
        nativeUi = enabled;
    }

    /** True dresses each skill's page and tiles in the skill's own theme; false keeps the plain book. */
    public boolean themedPages() {
        return themedPages;
    }

    public void themedPages(boolean enabled) {
        themedPages = enabled;
    }

    /** True holds themed pages and hover frames still: no idle motion, frames shown built at once. */
    public boolean reducedMotion() {
        return reducedMotion;
    }

    public void reducedMotion(boolean enabled) {
        reducedMotion = enabled;
    }

    /** True shows skills not started yet and merged partners greyed out in their seats, where the server allows it. */
    public boolean inactiveSkills() {
        return inactiveSkills;
    }

    public void inactiveSkills(boolean shown) {
        inactiveSkills = shown;
    }
}
