package art.arcane.adapt.util.common.plugin;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.xp.XpProvenanceListener;
import art.arcane.adapt.content.mutation.runtime.MutationRuntimeRouter;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProtectionEventListenerGuardTest {
  @Test
  void skillRegistryDoesNotReactToProtectionProbe() {
    SkillRegistry registry = mock(SkillRegistry.class, CALLS_REAL_METHODS);
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);

    dispatch(event, () -> registry.on(event));

    verify(event, never()).getPlayer();
  }

  @Test
  void mutationRuntimeDoesNotReactToProtectionProbe() {
    MutationRuntimeRouter router = mock(MutationRuntimeRouter.class, CALLS_REAL_METHODS);
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);

    dispatch(event, () -> router.on(event));

    verify(event, never()).getPlayer();
  }

  @Test
  void mutationRuntimeDoesNotReactToBlockProtectionProbes() {
    MutationRuntimeRouter router = mock(MutationRuntimeRouter.class, CALLS_REAL_METHODS);
    BlockBreakEvent breakEvent = mock(BlockBreakEvent.class);
    BlockPlaceEvent placeEvent = mock(BlockPlaceEvent.class);

    dispatch(breakEvent, () -> {
      router.on(breakEvent);
      router.onSuccessful(breakEvent);
    });
    dispatch(placeEvent, () -> router.on(placeEvent));

    verify(breakEvent, never()).getPlayer();
    verify(breakEvent, never()).getBlock();
    verify(placeEvent, never()).getPlayer();
    verify(placeEvent, never()).getBlock();
  }

  @Test
  void xpProvenanceDoesNotReactToBlockProtectionProbes() {
    XpProvenanceListener listener = new XpProvenanceListener();
    BlockBreakEvent breakEvent = mock(BlockBreakEvent.class);
    BlockPlaceEvent placeEvent = mock(BlockPlaceEvent.class);

    dispatch(breakEvent, () -> listener.on(breakEvent));
    dispatch(placeEvent, () -> listener.on(placeEvent));

    verify(breakEvent, never()).getBlock();
    verify(placeEvent, never()).getBlock();
  }

  private void dispatch(Event event, Runnable listener) {
    PluginManager pluginManager = mock(PluginManager.class);
    doAnswer(invocation -> {
      listener.run();
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      ProtectionEventProbe.dispatch(event);
    }
  }
}
