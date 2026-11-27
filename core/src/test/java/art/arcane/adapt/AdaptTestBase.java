package art.arcane.adapt;

import art.arcane.adapt.engine.tick.Ticker;
import art.arcane.adapt.localization.BuiltInText;
import art.arcane.adapt.platform.PlatformLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Installs a spy of an unstarted core (stub its getters per test) with a mocked ticker, a temporary data directory and
 * the built-in content text for each test.
 */
public abstract class AdaptTestBase {

  @TempDir
  protected File dataFolder;

  protected AdaptCore core;
  protected Ticker ticker;
  private AdaptCore previousCore;
  private PlatformLog previousLog;

  @BeforeEach
  void installTestCore() {
    previousCore = AdaptCore.current();
    previousLog = AdaptEnvironment.log();
    AdaptEnvironment.install(previousLog, dataFolder::toPath);
    ticker = mock(Ticker.class);
    core = spy(TestCores.install());
    lenient().when(core.getTicker()).thenReturn(ticker);
    lenient().when(core.getPlatform().host().dataDirectory()).thenReturn(dataFolder.toPath());
    TestCores.use(core);
    BuiltInText.install();
  }

  @AfterEach
  void restoreTestCore() {
    BuiltInText.uninstall();
    AdaptEnvironment.install(previousLog, () -> null);
    TestCores.use(previousCore);
  }
}
