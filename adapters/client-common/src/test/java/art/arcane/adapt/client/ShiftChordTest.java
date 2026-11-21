package art.arcane.adapt.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftChordTest {
    @Test
    void onlyOpenSkillsDefaultsToShift() {
        assertTrue(AdaptKey.OPEN_SKILLS.shiftByDefault());
        assertFalse(AdaptKey.OPEN_HUD_EDITOR.shiftByDefault());
        assertFalse(AdaptKey.TOGGLE_HUD.shiftByDefault());
    }

    @Test
    void atDefaultOnlyAShiftPressIsClaimed() {
        assertTrue(ShiftChord.claims(true, true));
        assertFalse(ShiftChord.claims(true, false));
    }

    @Test
    void atDefaultThePlainKeyKeepsItsVanillaMeaning() {
        assertFalse(ShiftChord.plainKey(true));
    }

    @Test
    void reboundTheBindingIsAPlainKeyWithoutShift() {
        assertTrue(ShiftChord.plainKey(false));
        assertFalse(ShiftChord.claims(false, true));
        assertFalse(ShiftChord.claims(false, false));
    }
}
