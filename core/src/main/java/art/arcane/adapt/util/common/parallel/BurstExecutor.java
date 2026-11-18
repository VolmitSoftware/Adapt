package art.arcane.adapt.util.common.parallel;

import art.arcane.adapt.AdaptEnvironment;

import java.util.concurrent.ExecutorService;

public class BurstExecutor extends art.arcane.volmlib.util.parallel.BurstExecutorSupport {
  public BurstExecutor(ExecutorService executor, int burstSizeEstimate) {
    super(executor, burstSizeEstimate, AdaptEnvironment::error);
  }
}
