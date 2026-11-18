package art.arcane.adapt;

import art.arcane.adapt.api.tick.Ticker;
import art.arcane.adapt.bukkit.platform.BukkitLog;
import art.arcane.adapt.bukkit.port.BukkitTextPorts;
import art.arcane.adapt.platform.PlatformLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public abstract class AdaptTestBase {

    @TempDir
    protected File dataFolder;

    protected Adapt plugin;
    protected Ticker ticker;
    protected AdaptCore core;
    private Adapt previousInstance;
    private AdaptCore previousCore;
    private PlatformLog previousLog;
    private Path previousDataDirectory;

    @BeforeEach
    void installMockPlugin() {
        previousInstance = Adapt.instance;
        plugin = mock(Adapt.class);
        ticker = mock(Ticker.class);
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder);
        lenient().when(plugin.getDataFile(any(String[].class))).thenAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            String[] path = new String[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                path[i] = String.valueOf(arguments[i]);
            }
            return new File(dataFolder, String.join(File.separator, path));
        });
        lenient().when(plugin.getTicker()).thenReturn(ticker);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("AdaptTest"));
        Adapt.instance = plugin;
        previousLog = AdaptEnvironment.log();
        previousDataDirectory = AdaptEnvironment.dataDirectory();
        AdaptEnvironment.install(new BukkitLog(), () -> dataFolder.toPath());
        previousCore = AdaptCore.current();
        core = spy(TestCores.install());
        lenient().when(core.getTicker()).thenReturn(ticker);
        TestCores.use(core);
        BukkitTextPorts.install(plugin);
    }

    @AfterEach
    void restoreInstance() {
        BukkitTextPorts.uninstall();
        Adapt.instance = previousInstance;
        Path restoredDataDirectory = previousDataDirectory;
        AdaptEnvironment.install(previousLog, () -> restoredDataDirectory);
        TestCores.use(previousCore);
    }
}
