package art.arcane.adapt.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

class HudToastTest {
    @Test
    void aPillWithoutBodyShowsTheTitle() {
        HudToast toast = new HudToast(ToastKind.LEVEL_UP, IconRef.item("minecraft:feather"), "Agility 3", "", "agility");
        assertEquals("Agility 3", toast.pillText());
    }

    @Test
    void aPillKeepsTheBodySoAMasterLevelSaysWhatRose() {
        HudToast toast = new HudToast(ToastKind.LEVEL_UP, IconRef.item("minecraft:experience_bottle"), "Level 3", "Max Power 3", "");
        assertEquals("Level 3  Max Power 3", toast.pillText());
    }
}
