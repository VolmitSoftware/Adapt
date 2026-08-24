package art.arcane.adapt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShutdownCleanupBarrierTest {
  @Test
  void startupReopensTheShutdownDrainLatch() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/Adapt.java"));
    int start = source.indexOf("public void start()");
    int reopen = source.indexOf("alreadyDrained.set(false);", start);
    int startup = source.indexOf("runStartupPhaseVoid", start);

    assertThat(start).isPositive();
    assertThat(reopen).isGreaterThan(start);
    assertThat(startup).isGreaterThan(reopen);
  }

  @Test
  void cleanupRunsAfterProducersStopAndBeforeSchedulerCancellation() throws IOException {
    String adapt = Files.readString(Path.of("src/main/java/art/arcane/adapt/Adapt.java"));
    int stopSim = adapt.indexOf("public void stopSim()");
    int ticker = adapt.indexOf("ticker::shutdown", stopSim);
    int attributeGate = adapt.indexOf("AdaptAttributeService::beginShutdown", ticker);
    int minionGate = adapt.indexOf("MinionBurden::beginShutdown", ticker);
    int server = adapt.indexOf("adaptServer.unregister()", stopSim);
    int displays = adapt.indexOf("ViewerDisplayDirector.clearAllAndAwait", stopSim);
    int minions = adapt.indexOf("MinionBurden.shutdown", stopSim);
    int attributes = adapt.indexOf("AdaptAttributeService.shutdown", stopSim);

    assertThat(ticker).isGreaterThan(stopSim);
    assertThat(attributeGate).isGreaterThan(ticker).isLessThan(server);
    assertThat(minionGate).isGreaterThan(ticker).isLessThan(server);
    assertThat(server).isGreaterThan(ticker);
    assertThat(displays).isGreaterThan(server);
    assertThat(minions).isGreaterThan(displays);
    assertThat(attributes).isGreaterThan(minions);

    String plugin = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/util/common/plugin/VolmitPlugin.java"));
    int onDisable = plugin.indexOf("public void onDisable()");
    int stop = plugin.indexOf("stop();", onDisable);
    int cancel = plugin.indexOf("J::cancelPluginTasks", onDisable);

    assertThat(stop).isGreaterThan(onDisable);
    assertThat(cancel).isGreaterThan(stop);
  }

  @Test
  void pluginDisableEventStartsCleanupWhileAdaptIsStillEnabled() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/Adapt.java"));
    int handler = source.indexOf("public void onPluginDisable(PluginDisableEvent event)");
    int ownPluginGuard = source.indexOf("event.getPlugin() != this", handler);
    int stop = source.indexOf("stop();", handler);

    assertThat(handler).isPositive();
    assertThat(ownPluginGuard).isGreaterThan(handler);
    assertThat(stop).isGreaterThan(ownPluginGuard);
  }
}
