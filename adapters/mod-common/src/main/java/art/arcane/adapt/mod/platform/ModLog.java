package art.arcane.adapt.mod.platform;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.platform.PlatformLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModLog implements PlatformLog {
    private final Logger logger;
    private volatile boolean verbose;

    public ModLog(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message, Throwable error) {
        if (error == null) {
            logger.error(message);
        } else {
            logger.error(message, error);
        }
    }

    @Override
    public void verbose(String message) {
        if (verbose || AdaptConfig.get().isVerbose()) {
            logger.info(message);
        }
    }
}
