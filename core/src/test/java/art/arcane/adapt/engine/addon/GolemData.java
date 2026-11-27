package art.arcane.adapt.engine.addon;

import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.node.NodeCodec;
import art.arcane.adapt.content.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.platform.value.Id;

/**
 * Declares the built-in golemancy section and slices the way the built-in addon does, and makes them live, for tests
 * that run no core.
 */
public final class GolemData {
  private GolemData() {
  }

  public static void declare() {
    EngineRegistrar registrar = new EngineRegistrar(Id.ADAPT);
    GolemRoster.declare(registrar);
    NodeCodec.declare(registrar);
    PlinthSlotCodec.declare(registrar);
    AddonContent content = registrar.content();
    DeclaredData.commit(Id.ADAPT, content.sections(), content.slices());
  }
}
