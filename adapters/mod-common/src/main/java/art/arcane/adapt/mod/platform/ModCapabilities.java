package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.Capability;

import java.util.List;
import java.util.function.Predicate;

/** The optional capabilities a modded server can report: a claim mod that answers the loader's protection events. */
public final class ModCapabilities {
    static final List<String> CLAIM_MODS = List.of("ftbchunks", "openpartiesandclaims", "flan", "cadmus", "goml");

    private ModCapabilities() {
    }

    public static boolean has(Capability capability, Predicate<String> modPresent) {
        if (capability == Capability.PROTECTION_PLUGINS) {
            for (String mod : CLAIM_MODS) {
                if (modPresent.test(mod)) {
                    return true;
                }
            }
        }
        return false;
    }
}
