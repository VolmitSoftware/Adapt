package art.arcane.adapt;

import art.arcane.adapt.testkit.MockedEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** {@link AdaptTestBase} with the engine bridge running over its core, so tests can build content through {@code TestContent}. */
public abstract class EngineTestBase extends AdaptTestBase {
  private MockedEngine engine;

  @BeforeEach
  void startEngine() {
    engine = new MockedEngine(core);
  }

  @AfterEach
  void stopEngine() {
    if (engine != null) {
      engine.close();
      engine = null;
    }
  }
}
