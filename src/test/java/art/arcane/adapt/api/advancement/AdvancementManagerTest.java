package art.arcane.adapt.api.advancement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancementManagerTest {

    @Test
    @DisplayName("false to true advancement hotload enables the manager before grants")
    void falseToTrueHotloadEnablesBeforeGranting() {
        AdvancementManager.SynchronizationAction action = AdvancementManager.selectSynchronizationAction(
            true,
            true,
            false,
            false,
            -1L,
            7L
        );

        assertThat(action).isEqualTo(AdvancementManager.SynchronizationAction.ENABLE);
        assertThat(AdvancementManager.canRenderGrant(false, false, true)).isFalse();
        assertThat(AdvancementManager.canRenderGrant(true, true, true)).isTrue();
    }

    @Test
    @DisplayName("a newly enabled skill revision rebuilds the catalog before its keys grant")
    void newlyEnabledSkillRebuildsCatalogBeforeGranting() {
        AdvancementManager.SynchronizationAction action = AdvancementManager.selectSynchronizationAction(
            true,
            true,
            true,
            true,
            12L,
            13L
        );

        assertThat(action).isEqualTo(AdvancementManager.SynchronizationAction.REBUILD);
        assertThat(AdvancementManager.canRenderGrant(true, false, true)).isFalse();
        assertThat(AdvancementManager.canRenderGrant(true, true, false)).isFalse();
        assertThat(AdvancementManager.selectSynchronizationAction(true, true, true, true, 13L, 13L))
            .isEqualTo(AdvancementManager.SynchronizationAction.READY);
    }

    @Test
    @DisplayName("disabled and unavailable runtimes do not render one shot grants")
    void disabledAndUnavailableRuntimesDoNotRenderGrants() {
        assertThat(AdvancementManager.selectSynchronizationAction(false, true, true, true, 4L, 4L))
            .isEqualTo(AdvancementManager.SynchronizationAction.DISABLE);
        assertThat(AdvancementManager.selectSynchronizationAction(true, false, false, false, -1L, 4L))
            .isEqualTo(AdvancementManager.SynchronizationAction.UNAVAILABLE);
        assertThat(AdvancementManager.canRenderGrant(false, true, true)).isFalse();
    }

    @Test
    @DisplayName("player restores are deterministically spread across multiple ticks")
    void playerRestoresAreStaggered() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertThat(AdvancementManager.restoreInitialDelay(first)).isEqualTo(21);
        assertThat(AdvancementManager.restoreInitialDelay(second)).isEqualTo(26);
    }
}
