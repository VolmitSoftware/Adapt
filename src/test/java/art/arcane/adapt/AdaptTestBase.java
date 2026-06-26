package art.arcane.adapt;

import art.arcane.adapt.api.tick.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Logger;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public abstract class AdaptTestBase {

    @TempDir
    protected File dataFolder;

    protected Adapt plugin;
    protected Ticker ticker;
    private Adapt previousInstance;

    @BeforeEach
    void installMockPlugin() {
        previousInstance = Adapt.instance;
        plugin = mock(Adapt.class);
        ticker = mock(Ticker.class);
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder);
        lenient().when(plugin.getTicker()).thenReturn(ticker);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("AdaptTest"));
        Adapt.instance = plugin;
    }

    @AfterEach
    void restoreInstance() {
        Adapt.instance = previousInstance;
    }
}
