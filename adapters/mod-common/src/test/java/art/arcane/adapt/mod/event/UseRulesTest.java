package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.player.UseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UseRulesTest {
    @Test
    void aDeniedBlockUseStopsTheItemOnThatBlockEvenWhenTheItemIsAllow() {
        assertFalse(UseRules.usesItemOnBlock(UseResult.DENY, UseResult.DEFAULT));
        assertFalse(UseRules.usesItemOnBlock(UseResult.DENY, UseResult.ALLOW));
    }

    @Test
    void aDeniedItemUseIsNeverForcedByTheBlock() {
        assertFalse(UseRules.usesItemOnBlock(UseResult.ALLOW, UseResult.DENY));
        assertFalse(UseRules.usesItemOnBlock(UseResult.DEFAULT, UseResult.DENY));
    }

    @Test
    void otherwiseVanillaDecides() {
        assertTrue(UseRules.usesItemOnBlock(UseResult.DEFAULT, UseResult.DEFAULT));
        assertTrue(UseRules.usesItemOnBlock(UseResult.ALLOW, UseResult.DEFAULT));
        assertTrue(UseRules.usesItemOnBlock(UseResult.DEFAULT, UseResult.ALLOW));
    }
}
