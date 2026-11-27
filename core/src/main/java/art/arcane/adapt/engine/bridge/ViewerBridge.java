package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.insight.InsightFeed;
import art.arcane.adapt.api.internal.ViewerRuntime;
import art.arcane.adapt.client.server.insight.InsightFrames;
import art.arcane.adapt.engine.fx.ViewerDisplayPort;
import art.arcane.adapt.engine.fx.ViewerGlowPort;

/** The viewer ports the platform installed, or the engine's fallbacks, and Insight frames, as the API sees them. */
final class ViewerBridge implements ViewerRuntime {
  @Override
  public art.arcane.adapt.api.viewer.ViewerGlowPort glow() {
    return ViewerGlowPort.current();
  }

  @Override
  public art.arcane.adapt.api.viewer.ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
  }

  @Override
  public InsightFeed insightFeed() {
    return InsightFrames.standard();
  }
}
