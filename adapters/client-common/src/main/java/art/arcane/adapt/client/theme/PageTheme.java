package art.arcane.adapt.client.theme;

import java.util.Objects;

/**
 * What dresses a page: one skill's kit, or for a mutated skill the lead's and the partner's. The lead owns the left page
 * and everything on its side of the binding, the partner the right.
 */
public record PageTheme(ThemeKit lead, ThemeKit partner) {
    public PageTheme {
        Objects.requireNonNull(lead, "lead");
        if (partner == lead) {
            partner = null;
        }
    }

    public boolean mutated() {
        return partner != null;
    }

    /** Whose kit dresses the part of the page at {@code x}, given the spine at {@code fold}. */
    public ThemeKit owner(int x, int fold) {
        return partner != null && x >= fold ? partner : lead;
    }
}
