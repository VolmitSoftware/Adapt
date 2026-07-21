package art.arcane.adapt.content.integration.iris;

import art.arcane.iris.api.tree.IrisTreeFellerService;
import art.arcane.iris.api.tree.TreeFellerAccess;
import art.arcane.iris.api.tree.TreeFellerOptions;
import art.arcane.iris.api.tree.TreeFellerRunHooks;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IrisTreeFellerBridgeTest {
  @Test
  void tryFellUsesIntegrationOverrideAndDelegatesEveryRunHook() {
    IrisTreeFellerService service = mock(IrisTreeFellerService.class);
    BlockBreakEvent event = mock(BlockBreakEvent.class);
    IrisTreeFellerLink.RunHooks runHooks = mock(IrisTreeFellerLink.RunHooks.class);
    ArgumentCaptor<TreeFellerOptions> options = ArgumentCaptor.forClass(TreeFellerOptions.class);
    when(service.tryFell(any(), any())).thenReturn(true);
    when(runHooks.reserveLogCost()).thenReturn(true);

    boolean accepted = IrisTreeFellerBridge.tryFell(service, event, 75, runHooks);

    assertThat(accepted).isTrue();
    verify(service).tryFell(same(event), options.capture());
    assertThat(options.getValue().access()).isEqualTo(TreeFellerAccess.INTEGRATION_OVERRIDE);
    assertThat(options.getValue().durabilityPreservationChance()).isEqualTo(75);
    TreeFellerRunHooks forwarded = options.getValue().runHooks();
    forwarded.onActivationAccepted();
    assertThat(forwarded.reserveLogCost()).isTrue();
    forwarded.commitLogCost();
    forwarded.refundLogCost();
    verify(runHooks).onActivationAccepted();
    verify(runHooks).reserveLogCost();
    verify(runHooks).commitLogCost();
    verify(runHooks).refundLogCost();
  }

  @Test
  void managedBreakAndTreeRecognitionDelegateToIris() {
    IrisTreeFellerService service = mock(IrisTreeFellerService.class);
    BlockBreakEvent event = mock(BlockBreakEvent.class);
    Block block = mock(Block.class);
    when(service.isManagedBreak(event)).thenReturn(true);
    when(service.isTreeBlock(block)).thenReturn(true);

    assertThat(IrisTreeFellerBridge.isManagedBreak(service, event)).isTrue();
    assertThat(IrisTreeFellerBridge.isTreeBlock(service, block)).isTrue();
  }

  @Test
  void missingServiceDeclinesWithoutClaiming() {
    BlockBreakEvent event = mock(BlockBreakEvent.class);
    Block block = mock(Block.class);
    IrisTreeFellerLink.RunHooks runHooks = mock(IrisTreeFellerLink.RunHooks.class);

    assertThat(IrisTreeFellerBridge.tryFell(null, event, 25, runHooks)).isFalse();
    assertThat(IrisTreeFellerBridge.isManagedBreak(null, event)).isFalse();
    assertThat(IrisTreeFellerBridge.isTreeBlock(null, block)).isFalse();
  }
}
