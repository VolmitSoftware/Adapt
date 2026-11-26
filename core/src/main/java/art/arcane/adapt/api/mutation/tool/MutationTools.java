package art.arcane.adapt.api.mutation.tool;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.format.C;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/** Finds the registered mutation tools through the skill registry: every upgrade's and signature's tools. */
public final class MutationTools {
  private static volatile ToolIndex index;

  private MutationTools() {
  }

  public static List<MutationTool<?>> all(SkillRegistry registry) {
    List<MutationTool<?>> tools = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> owner : Skill.ownedAdaptations(skill)) {
        if (owner instanceof ToolOwner holder) {
          tools.addAll(holder.getTools());
        }
      }
    }
    return List.copyOf(tools);
  }

  /** The tools {@code adaptation} hands out: an upgrade's own, or the signature's a mutated adaptation stands for. */
  public static List<MutationTool<?>> handedOut(Adaptation<?> adaptation) {
    if (adaptation instanceof ToolOwner owner) {
      return owner.getTools();
    }
    Signature<?> signature = adaptation instanceof MutationAdaptation mutated ? mutated.getSignature() : null;
    return signature == null ? List.of() : signature.getTools();
  }

  /** The tools {@code adaptation} hands out named on one lore line, or empty for none. */
  public static String lore(Adaptation<?> adaptation) {
    List<MutationTool<?>> tools = handedOut(adaptation);
    if (tools.isEmpty()) {
      return "";
    }
    List<String> names = new ArrayList<>(tools.size());
    for (MutationTool<?> tool : tools) {
      names.add(AdaptLanguage.text(tool.nameKey()));
    }
    return C.AQUA + AdaptLanguage.text(GuiMessages.TOOLS, trusted("tools", String.join(", ", names)));
  }

  /** The registered tool with this id, or null. */
  public static @Nullable MutationTool<?> find(@Nullable String id) {
    AdaptServer server = id == null ? null : AdaptCore.get().getAdaptServer();
    if (server == null) {
      return null;
    }
    return index(server.getSkillRegistry()).byId().get(id);
  }

  private static ToolIndex index(SkillRegistry registry) {
    long revision = registry.getCatalogRevision();
    ToolIndex current = index;
    if (current != null && current.registry() == registry && current.revision() == revision) {
      return current;
    }
    Map<String, MutationTool<?>> byId = new HashMap<>();
    for (MutationTool<?> tool : all(registry)) {
      byId.putIfAbsent(tool.id(), tool);
    }
    current = new ToolIndex(registry, revision, Map.copyOf(byId));
    index = current;
    return current;
  }

  private record ToolIndex(SkillRegistry registry, long revision, Map<String, MutationTool<?>> byId) {
  }

  /** The registered tool this stack is, or null. */
  public static @Nullable MutationTool<?> of(@Nullable ItemRef item) {
    return find(MutationTool.idOf(item));
  }

  /** Whether the stack is any mutation tool, registered on this server or not. */
  public static boolean isTool(@Nullable ItemRef item) {
    return MutationTool.idOf(item) != null;
  }
}
