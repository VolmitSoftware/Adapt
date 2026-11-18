package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.world.BlockRef;

/**
 * Block break whose experience is only known once vanilla rolls it. The posted experience is 0;
 * a handler that sets it replaces whatever vanilla would have dropped.
 */
final class ModBlockBreakEvent extends BlockBreakEvent {
    private boolean experienceSet;

    ModBlockBreakEvent(PlayerRef player, BlockRef block) {
        super(player, block, true, 0);
    }

    @Override
    public void setExperience(int experience) {
        super.setExperience(experience);
        experienceSet = true;
    }

    boolean experienceSet() {
        return experienceSet;
    }
}
