package art.arcane.adapt.command;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CommandVersionTest {
  @Test
  void versionIsDiscoverableUnderDebugAndHiddenAtTheRoot() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandAdapt());
    DirectorSender sender = mock(DirectorSender.class);
    DirectorMiniMenu.DirectorHelpPage debug = DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow();

    assertTrue(engine.getRoot().getChildren().stream()
        .anyMatch(node -> node.getDescriptor().getName().equals("version")));
    assertTrue(debug.entries().stream().anyMatch(node -> node.getDescriptor().getName().equals("version")));
    assertFalse(engine.tabComplete(new DirectorInvocation(sender, "adapt", List.of("version"))).contains("version"));
    assertTrue(engine.tabComplete(new DirectorInvocation(sender, "adapt", List.of("debug", "ver"))).contains("version"));
  }
}
