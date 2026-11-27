package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.insight.InsightFeed;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.api.viewer.ViewerGlowPort;
import org.jetbrains.annotations.ApiStatus;

/** The engine's per-viewer glow and display ports and the Insight client feed. */
@ApiStatus.Internal
public interface ViewerRuntime {
  ViewerGlowPort glow();

  ViewerDisplayPort displays();

  InsightFeed insightFeed();
}
