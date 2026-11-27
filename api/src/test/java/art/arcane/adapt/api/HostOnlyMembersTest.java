package art.arcane.adapt.api;

import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonInfo;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.api.internal.InstalledAddon;
import art.arcane.adapt.api.service.ServiceRegistration;
import art.arcane.adapt.platform.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What code compiled against the API alone tries, with no host key, and how each attempt is refused. */
class HostOnlyMembersTest {
  private final List<AddonHandle> installed = new ArrayList<>();

  @AfterEach
  void uninstall() {
    for (AddonHandle handle : installed) {
      handle.uninstall();
    }
  }

  @Test
  void aSecondClaimOfTheHostKeyNamesTheFirstClaimant() {
    TestHost.key();

    assertThatThrownBy(() -> Engine.claimHost())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already claimed by " + TestHost.class.getName())
        .hasMessageContaining(HostOnlyMembersTest.class.getName());
  }

  @Test
  void startingACoreOrInstallingTheBuiltinAddonNeedsTheKey() {
    EngineBridge bridge = TestStubs.stub(EngineBridge.class, Map.of());

    assertThatThrownBy(() -> Engine.start(null, bridge)).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.addons().installBuiltin(null, new Empty("adapt")))
        .hasMessage("Only Adapt's platform host may do this");
    assertThat(Engine.isRunning()).isFalse();
    assertThat(AdaptApi.addons().find("adapt")).isEmpty();
  }

  @Test
  void readingOtherPartiesRegistrationsNeedsTheKey() {
    assertThatThrownBy(() -> Engine.addons().snapshot(null)).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.addons().handle(null, "adapt")).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.events().snapshot(null)).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.protectors().protectors(null)).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.abilities().policies(null)).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.abilities().costProviders(null))
        .hasMessage("Only Adapt's platform host may do this");
  }

  @Test
  void foundAddonsAreDetachedValuesThatCannotUninstall() {
    AddonHandle handle = install(new Empty("victim"));

    AddonInfo found = AdaptApi.addons().find("victim").orElseThrow();
    List<AddonInfo> listed = AdaptApi.addons().installed();

    assertThat(found).isNotInstanceOf(AddonHandle.class);
    assertThat(listed).noneMatch((AddonInfo info) -> info instanceof AddonHandle);
    assertThat(found.namespace()).isEqualTo("victim");
    assertThat(found.state()).isEqualTo(handle.state());
  }

  @Test
  void anAddonCannotReportItsOwnApplyOutcome() {
    InstalledAddon handle = (InstalledAddon) install(new Empty("liar"));

    assertThatThrownBy(() -> handle.applied(null)).hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> handle.rejected(null, "forged")).hasMessage("Only Adapt's platform host may do this");
    assertThat(handle.rejection()).isEmpty();
  }

  @Test
  void aServiceTakeoverLogsBothOwnersAndUninstallWithdrawsTheProvider() {
    List<String> warnings = new ArrayList<>();
    Handler capture = new Handler() {
      @Override
      public void publish(LogRecord record) {
        warnings.add(record.getMessage());
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    Logger logger = Logger.getLogger("Adapt");
    logger.addHandler(capture);
    AddonHandle first = install(new Empty("firstsvc"));
    install(new Empty("secondsvc"));
    Greeter firstGreeter = () -> "first";
    Greeter secondGreeter = () -> "second";
    try {
      ServiceRegistration kept = AdaptApi.services().provide("secondsvc", Greeter.class, secondGreeter);
      AdaptApi.services().provide("firstsvc", Greeter.class, firstGreeter);

      assertThat(warnings).anyMatch((String line) -> line.contains(Greeter.class.getName())
          && line.contains("firstsvc") && line.contains("secondsvc"));
      assertThat(AdaptApi.services().find(Greeter.class)).contains(firstGreeter);

      first.uninstall();
      installed.remove(first);

      assertThat(AdaptApi.services().find(Greeter.class)).contains(secondGreeter);
      kept.withdraw();
      assertThat(AdaptApi.services().find(Greeter.class)).isEqualTo(Optional.empty());
    } finally {
      logger.removeHandler(capture);
    }
  }

  @Test
  void withoutACoreRunAsyncReturnsACancelledHandle() {
    List<String> ran = new ArrayList<>();

    TaskHandle handle = AdaptApi.scheduler().runAsync(() -> ran.add("async"));

    assertThat(handle.isCancelled()).isTrue();
    assertThat(ran).isEmpty();
  }

  private AddonHandle install(AdaptAddon addon) {
    AddonHandle handle = AdaptApi.addons().install(addon);
    installed.add(handle);
    return handle;
  }

  interface Greeter {
    String greet();
  }

  private record Empty(String namespace) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
    }
  }
}
