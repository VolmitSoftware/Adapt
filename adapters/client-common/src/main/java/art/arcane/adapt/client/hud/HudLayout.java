package art.arcane.adapt.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Every saved profile, which one is in use, and settings shared by all profiles. Never empty. */
public record HudLayout(String active, boolean reducedMotion, List<HudProfile> profiles) {
    public static final int SCHEMA = 4;

    public HudLayout {
        Objects.requireNonNull(active, "active");
        profiles = List.copyOf(profiles);
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("a layout needs a profile");
        }
    }

    public HudProfile activeProfile() {
        HudProfile profile = profile(active);
        return profile == null ? profiles.get(0) : profile;
    }

    public HudProfile profile(String name) {
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).name().equals(name)) {
                return profiles.get(index);
            }
        }
        return null;
    }

    public HudLayout withActive(String name) {
        return new HudLayout(name, reducedMotion, profiles);
    }

    public HudLayout withReducedMotion(boolean value) {
        return new HudLayout(active, value, profiles);
    }

    /** Replaces the active profile with {@code profile}, following a rename. */
    public HudLayout withActiveProfile(HudProfile profile) {
        String current = activeProfile().name();
        List<HudProfile> next = new ArrayList<>(profiles.size());
        for (HudProfile existing : profiles) {
            next.add(existing.name().equals(current) ? profile : existing);
        }
        return new HudLayout(profile.name(), reducedMotion, next);
    }

    /** Adds {@code profile} and makes it active. */
    public HudLayout withAdded(HudProfile profile) {
        List<HudProfile> next = new ArrayList<>(profiles);
        next.add(profile);
        return new HudLayout(profile.name(), reducedMotion, next);
    }

    /** Removes the active profile; the last profile cannot be removed. */
    public HudLayout withoutActive() {
        if (profiles.size() <= 1) {
            return this;
        }
        String current = activeProfile().name();
        List<HudProfile> next = new ArrayList<>(profiles.size());
        int removed = 0;
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).name().equals(current)) {
                removed = index;
            } else {
                next.add(profiles.get(index));
            }
        }
        return new HudLayout(next.get(Math.max(0, removed - 1)).name(), reducedMotion, next);
    }

    /** {@code base}, or {@code base (n)} with the smallest n that no profile uses. */
    public String freshName(String base) {
        String trimmed = base.length() > HudProfile.MAX_NAME_LENGTH - 5 ? base.substring(0, HudProfile.MAX_NAME_LENGTH - 5) : base;
        if (profile(trimmed) == null) {
            return trimmed;
        }
        int number = 2;
        while (profile(trimmed + " (" + number + ")") != null) {
            number++;
        }
        return trimmed + " (" + number + ")";
    }

    /** The profile after (or before, for a negative step) the active one, wrapping around. */
    public HudLayout cycled(int step) {
        int index = profiles.indexOf(activeProfile());
        int next = Math.floorMod(index + step, profiles.size());
        return withActive(profiles.get(next).name());
    }
}
