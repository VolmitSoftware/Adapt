package art.arcane.adapt.api.mutation.tool;

import java.util.List;

/** An adaptation that hands out mutation tools: an upgrade or a signature. */
public interface ToolOwner {
  /** The tools registered with {@code registerTool}, in registration order. */
  List<MutationTool<?>> getTools();
}
