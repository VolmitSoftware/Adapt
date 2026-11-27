package art.arcane.adapt.engine.mutation.tool;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.engine.bridge.ItemTexts;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
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
    registry.refreshSignatures();
    List<MutationTool<?>> tools = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> owner : SkillHost.ownedAdaptations(skill)) {
        tools.addAll(handedOut(owner));
      }
    }
    for (MutatedSkill mutated : registry.getBuiltMutatedSkills()) {
      for (Signature<?> signature : mutated.getSignatures()) {
        tools.addAll(signature.getTools());
      }
    }
    return List.copyOf(tools);
  }

  /** The tools {@code adaptation} hands out: an upgrade's or signature's own, or the signature's a mutated adaptation stands for. */
  public static List<MutationTool<?>> handedOut(Adaptation<?> adaptation) {
    if (adaptation instanceof SkillUpgrade<?> upgrade) {
      return upgrade.getTools();
    }
    if (adaptation instanceof Signature<?> signature) {
      return signature.getTools();
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
      names.add(name(tool));
    }
    return C.AQUA + AdaptLanguage.text(GuiMessages.TOOLS, trusted("tools", String.join(", ", names)));
  }

  /** The tool's name in the language of the current render. */
  public static String name(MutationTool<?> tool) {
    String name = ItemTexts.name(tool.item().id());
    return name == null ? tool.item().id().toString() : name;
  }

  /** The registered tool with this id, or null. */
  public static @Nullable MutationTool<?> find(@Nullable Id id) {
    SkillRegistry registry = id == null ? null : registry();
    return registry == null ? null : index(registry).byId().get(id);
  }

  /** The item definitions of the registered tools; the same list until the tools change. */
  public static List<CustomItem.Definition<?>> items() {
    SkillRegistry registry = registry();
    return registry == null ? List.of() : index(registry).items();
  }

  /** Drops the index, for when a mutated skill's signatures change. */
  public static void forget() {
    index = null;
  }

  private static ToolIndex index(SkillRegistry registry) {
    long revision = registry.getCatalogRevision();
    ToolIndex current = index;
    if (current != null && current.registry() == registry && current.revision() == revision) {
      return current;
    }
    Map<Id, MutationTool<?>> byId = new HashMap<>();
    Map<Id, MutationTool<?>> byRecipe = new HashMap<>();
    Map<Id, List<MutationTool<?>>> byBase = new HashMap<>();
    List<CustomItem.Definition<?>> items = new ArrayList<>();
    for (MutationTool<?> tool : all(registry)) {
      if (byId.putIfAbsent(tool.item().id(), tool) != null) {
        continue;
      }
      items.add(tool.item());
      byBase.computeIfAbsent(tool.item().baseItem(), (Id ignored) -> new ArrayList<>()).add(tool);
      for (RecipeSpec recipe : tool.recipes()) {
        byRecipe.putIfAbsent(recipe.key(), tool);
      }
    }
    current = new ToolIndex(registry, revision, Map.copyOf(byId), Map.copyOf(byRecipe), Map.copyOf(byBase),
        List.copyOf(items));
    index = current;
    return current;
  }

  private record ToolIndex(SkillRegistry registry, long revision, Map<Id, MutationTool<?>> byId,
                           Map<Id, MutationTool<?>> byRecipe, Map<Id, List<MutationTool<?>>> byBase,
                           List<CustomItem.Definition<?>> items) {
  }

  /**
   * Whether {@code recipe} makes a mutation tool: a registered tool's recipe, or any recipe keyed below a tool id
   * ({@code <owner>/tool/<name>/...}), registered on this server or not.
   */
  public static boolean isToolRecipe(@Nullable Id recipe) {
    return recipe != null && (recipe.path().contains("/tool/") || ofRecipe(recipe) != null);
  }

  /** The registered tool {@code recipe} makes, or null. */
  public static @Nullable MutationTool<?> ofRecipe(@Nullable Id recipe) {
    SkillRegistry registry = recipe == null ? null : registry();
    return registry == null ? null : index(registry).byRecipe().get(recipe);
  }

  /** The registered tool this stack is, or null. */
  public static @Nullable MutationTool<?> of(@Nullable ItemRef item) {
    SkillRegistry registry = item == null || item.isEmpty() ? null : registry();
    if (registry == null) {
      return null;
    }
    List<MutationTool<?>> candidates = index(registry).byBase().get(item.type());
    if (candidates == null) {
      return null;
    }
    for (MutationTool<?> tool : candidates) {
      if (tool.is(item)) {
        return tool;
      }
    }
    return null;
  }

  /** Whether the stack is a registered mutation tool. */
  public static boolean isTool(@Nullable ItemRef item) {
    return of(item) != null;
  }

  private static @Nullable SkillRegistry registry() {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry();
  }
}
