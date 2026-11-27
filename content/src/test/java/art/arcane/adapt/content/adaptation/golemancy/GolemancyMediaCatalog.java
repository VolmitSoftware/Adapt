package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.item.ConfiguredShapedRecipe;
import art.arcane.adapt.content.golem.item.GolemItemLooks;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.plinth.InfusionRecipe;
import art.arcane.adapt.util.common.format.C;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class GolemancyMediaCatalog {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

  private GolemancyMediaCatalog() {
  }

  static List<AdaptationSource> adaptationSources() {
    return List.of(
        new AdaptationSource("adapt:golem/animus_binding", Text.key("adapt.golemancy.animus_binding.name"), new GolemAnimusBinding.Config()),
        new AdaptationSource("adapt:golem/node_weaving", Text.key("adapt.golemancy.node_weaving.name"), new GolemNodeWeaving.Config()),
        new AdaptationSource("adapt:golem/frost_frame", Text.key("adapt.golemancy.frost_frame.name"), new GolemFrostFrame.Config()),
        new AdaptationSource("adapt:golem/copper_frame", Text.key("adapt.golemancy.copper_frame.name"), new GolemCopperFrame.Config()),
        new AdaptationSource("adapt:golem/iron_frame", Text.key("adapt.golemancy.iron_frame.name"), new GolemIronFrame.Config()),
        new AdaptationSource("adapt:golem/bone_frame", Text.key("adapt.golemancy.bone_frame.name"), new GolemBoneFrame.Config()),
        new AdaptationSource("adapt:golem/guardian_core", Text.key("adapt.golemancy.guardian_core.name"), new GolemGuardianCore.Config()),
        new AdaptationSource("adapt:golem/gatherer_core", Text.key("adapt.golemancy.gatherer_core.name"), new GolemGathererCore.Config()),
        new AdaptationSource("adapt:golem/harvester_core", Text.key("adapt.golemancy.harvester_core.name"), new GolemHarvesterCore.Config()),
        new AdaptationSource("adapt:golem/lumber_core", Text.key("adapt.golemancy.lumber_core.name"), new GolemLumberCore.Config()),
        new AdaptationSource("adapt:golem/breaker_core", Text.key("adapt.golemancy.breaker_core.name"), new GolemBreakerCore.Config()),
        new AdaptationSource("adapt:golem/rancher_core", Text.key("adapt.golemancy.rancher_core.name"), new GolemRancherCore.Config()),
        new AdaptationSource("adapt:golem/sorter_core", Text.key("adapt.golemancy.sorter_core.name"), new GolemSorterCore.Config()),
        new AdaptationSource("adapt:golem/angler_core", Text.key("adapt.golemancy.angler_core.name"), new GolemAnglerCore.Config()),
        new AdaptationSource("adapt:golem/runic_augments", Text.key("adapt.golemancy.runic_augments.name"), new GolemRunicAugments.Config()),
        new AdaptationSource("adapt:golem/overseer", Text.key("adapt.golemancy.overseer.name"), new GolemOverseer.Config()),
        new AdaptationSource("adapt:golem/mending", Text.key("adapt.golemancy.mending.name"), new GolemMending.Config()),
        new AdaptationSource("adapt:golem/stasis", Text.key("adapt.golemancy.stasis.name"), new GolemStasis.Config()),
        new AdaptationSource("adapt:golem/masterwork", Text.key("adapt.golemancy.masterwork.name"), new GolemMasterwork.Config()));
  }

  static List<RecipeSource> recipeSources(List<AdaptationSource> adaptations) {
    GolemAnimusBinding.Config animus = config(adaptations, "adapt:golem/animus_binding", GolemAnimusBinding.Config.class);
    GolemNodeWeaving.Config weaving = config(adaptations, "adapt:golem/node_weaving", GolemNodeWeaving.Config.class);
    GolemGuardianCore.Config guardian = config(adaptations, "adapt:golem/guardian_core", GolemGuardianCore.Config.class);
    GolemGathererCore.Config gatherer = config(adaptations, "adapt:golem/gatherer_core", GolemGathererCore.Config.class);
    GolemHarvesterCore.Config harvester = config(adaptations, "adapt:golem/harvester_core", GolemHarvesterCore.Config.class);
    GolemLumberCore.Config lumber = config(adaptations, "adapt:golem/lumber_core", GolemLumberCore.Config.class);
    GolemBreakerCore.Config breaker = config(adaptations, "adapt:golem/breaker_core", GolemBreakerCore.Config.class);
    GolemRancherCore.Config rancher = config(adaptations, "adapt:golem/rancher_core", GolemRancherCore.Config.class);
    GolemSorterCore.Config sorter = config(adaptations, "adapt:golem/sorter_core", GolemSorterCore.Config.class);
    GolemAnglerCore.Config angler = config(adaptations, "adapt:golem/angler_core", GolemAnglerCore.Config.class);
    GolemRunicAugments.Config runes = config(adaptations, "adapt:golem/runic_augments", GolemRunicAugments.Config.class);
    return List.of(
        new RecipeSource(GolemAnimusBinding.CROOK_RECIPE, animus.crookRecipe),
        new RecipeSource(GolemAnimusBinding.BLANK_CORE_RECIPE, animus.blankCoreRecipe),
        new RecipeSource(GolemNodeWeaving.BLANK_NODE_RECIPE, weaving.blankNodeRecipe),
        new RecipeSource(GolemNodeWeaving.DEPOSIT_NODE_RECIPE, weaving.depositNodeRecipe),
        new RecipeSource(GolemGuardianCore.CORE_RECIPE, guardian.guardianCoreRecipe),
        new RecipeSource(GolemGuardianCore.NODE_RECIPE, guardian.guardNodeRecipe),
        new RecipeSource(GolemGathererCore.CORE_RECIPE, gatherer.gathererCoreRecipe),
        new RecipeSource(GolemGathererCore.NODE_RECIPE, gatherer.collectionNodeRecipe),
        new RecipeSource(GolemHarvesterCore.CORE_RECIPE, harvester.harvesterCoreRecipe),
        new RecipeSource(GolemHarvesterCore.NODE_RECIPE, harvester.harvestNodeRecipe),
        new RecipeSource(GolemLumberCore.CORE_RECIPE, lumber.lumberCoreRecipe),
        new RecipeSource(GolemLumberCore.NODE_RECIPE, lumber.lumberNodeRecipe),
        new RecipeSource(GolemBreakerCore.CORE_RECIPE, breaker.breakerCoreRecipe),
        new RecipeSource(GolemBreakerCore.NODE_RECIPE, breaker.breakerNodeRecipe),
        new RecipeSource(GolemRancherCore.CORE_RECIPE, rancher.rancherCoreRecipe),
        new RecipeSource(GolemRancherCore.NODE_RECIPE, rancher.pastureNodeRecipe),
        new RecipeSource(GolemSorterCore.CORE_RECIPE, sorter.sorterCoreRecipe),
        new RecipeSource(GolemSorterCore.INPUT_RECIPE, sorter.inputNodeRecipe),
        new RecipeSource(GolemSorterCore.FILTER_RECIPE, sorter.filterNodeRecipe),
        new RecipeSource(GolemAnglerCore.CORE_RECIPE, angler.anglerCoreRecipe),
        new RecipeSource(GolemAnglerCore.NODE_RECIPE, angler.fishingNodeRecipe),
        new RecipeSource(GolemRunicAugments.BLANK_RUNE_RECIPE, runes.blankRuneRecipe));
  }

  static Map<String, Object> build(GolemItemLooks looks) {
    List<AdaptationSource> adaptations = adaptationSources();
    Map<String, Map<String, Object>> items = new TreeMap<>();
    List<Map<String, Object>> recipes = new ArrayList<>();
    for (RecipeSource source : recipeSources(adaptations)) {
      recipes.add(recipe(looks, items, source));
    }
    List<Map<String, Object>> infusions = new ArrayList<>();
    for (Map.Entry<String, InfusionRecipe> entry : InfusionRecipe.defaults().entrySet()) {
      if (entry.getValue().isEnabled()) {
        infusions.add(infusion(looks, items, entry.getKey(), entry.getValue()));
      }
    }
    Map<String, Object> catalog = new LinkedHashMap<>();
    catalog.put("items", items);
    catalog.put("recipes", recipes);
    catalog.put("infusions", infusions);
    catalog.put("adaptations", adaptations.stream().map(GolemancyMediaCatalog::adaptation).toList());
    return catalog;
  }

  static String toJson(Map<String, Object> catalog) {
    return GSON.toJson(catalog) + "\n";
  }

  private static Map<String, Object> recipe(GolemItemLooks looks, Map<String, Map<String, Object>> items, RecipeSource source) {
    ConfiguredShapedRecipe spec = source.spec();
    Map<String, Object> recipe = new LinkedHashMap<>();
    recipe.put("id", source.id());
    recipe.put("result", spec.getResult());
    recipe.put("yield", spec.getAmount());
    recipe.put("pattern", List.copyOf(spec.getRows()));
    recipe.put("key", new LinkedHashMap<>(spec.getKeys()));
    describe(looks, items, spec.getResult());
    for (String ingredient : spec.getKeys().values()) {
      describe(looks, items, ingredient);
    }
    return recipe;
  }

  private static Map<String, Object> infusion(GolemItemLooks looks, Map<String, Map<String, Object>> items, String id, InfusionRecipe spec) {
    Map<String, Object> infusion = new LinkedHashMap<>();
    infusion.put("id", id);
    infusion.put("center", spec.getCenter());
    infusion.put("pedestals", List.copyOf(spec.getPedestals()));
    infusion.put("result", spec.getResult());
    infusion.put("essenceLevels", spec.getEssenceLevels());
    infusion.put("durationTicks", spec.getDurationTicks());
    describe(looks, items, spec.getResult());
    describe(looks, items, spec.getCenter());
    for (String pedestal : spec.getPedestals()) {
      describe(looks, items, pedestal);
    }
    return infusion;
  }

  private static Map<String, Object> adaptation(AdaptationSource source) {
    List<Map<String, Object>> requires = new ArrayList<>();
    String namespace = Id.parseAdapt(source.id()).namespace();
    for (String line : source.config().requires) {
      AdaptationRequirement requirement = AdaptationRequirement.parse(line, namespace).orElse(null);
      if (requirement == null) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("id", requirement.adaptation().toString());
      entry.put("level", requirement.level());
      requires.add(entry);
    }
    Map<String, Object> adaptation = new LinkedHashMap<>();
    adaptation.put("id", source.id());
    adaptation.put("name", C.stripColor(source.name().resolve()));
    adaptation.put("requires", requires);
    return adaptation;
  }

  private static void describe(GolemItemLooks looks, Map<String, Map<String, Object>> items, String item) {
    Id id = item.indexOf(':') < 0 ? null : Id.parse(item.toLowerCase(Locale.ROOT));
    if (id == null || !GolemItems.NAMESPACE.equals(id.namespace()) || items.containsKey(item)) {
      return;
    }
    GolemItemLooks.Look look = looks.look(id);
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", look.name());
    entry.put("base", look.base());
    entry.put("tint", look.tint());
    entry.put("tier", look.tier());
    items.put(item, entry);
  }

  private static <T extends AdaptationConfig> T config(List<AdaptationSource> adaptations, String id, Class<T> type) {
    for (AdaptationSource source : adaptations) {
      if (source.id().equals(id)) {
        return type.cast(source.config());
      }
    }
    throw new IllegalArgumentException("No Golemancy adaptation " + id);
  }

  record AdaptationSource(String id, Text name, AdaptationConfig config) {
  }

  record RecipeSource(String id, ConfiguredShapedRecipe spec) {
  }
}
