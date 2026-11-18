package art.arcane.adapt.util.common.plugin;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.xp.XpProvenanceListener;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProtectionEventListenerGuardTest {
  @Test
  void skillRegistryDoesNotReactToProtectionProbe() {
    SkillRegistry registry = mock(SkillRegistry.class, CALLS_REAL_METHODS);
    PlayerRef player = mock(PlayerRef.class);
    PlayerInteractEvent event = new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, null,
        mock(BlockRef.class), null, null, UseResult.DEFAULT, UseResult.DEFAULT);
    event.markProtectionProbe();

    registry.on(event);

    verifyNoInteractions(player);
  }

  @Test
  void xpProvenanceDoesNotReactToBlockProtectionProbes() {
    XpProvenanceListener listener = new XpProvenanceListener();
    BlockRef broken = mock(BlockRef.class);
    BlockRef placed = mock(BlockRef.class);
    BlockBreakEvent breakEvent = new BlockBreakEvent(mock(PlayerRef.class), broken, true, 0);
    BlockPlaceEvent placeEvent = new BlockPlaceEvent(mock(PlayerRef.class), placed, mock(BlockStateRef.class), placed,
        mock(ItemRef.class), Hand.MAIN_HAND);
    breakEvent.markProtectionProbe();
    placeEvent.markProtectionProbe();

    listener.on(breakEvent);
    listener.on(placeEvent);

    verifyNoInteractions(broken, placed);
  }
}
