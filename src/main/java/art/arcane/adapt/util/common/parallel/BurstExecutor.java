package art.arcane.adapt.util.common.parallel;

import art.arcane.adapt.Adapt;
import java.util.concurrent.ExecutorService;

public class BurstExecutor extends art.arcane.volmlib.util.parallel.BurstExecutorSupport {
  public BurstExecutor(ExecutorService executor, int burstSizeEstimate) {
    super(executor, burstSizeEstimate, Adapt::error);
  }
}
