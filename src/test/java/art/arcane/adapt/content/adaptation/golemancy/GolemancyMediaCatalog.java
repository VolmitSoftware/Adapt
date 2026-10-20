package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationRequirement;
import art.arcane.adapt.api.golem.item.GolemItemLooks;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.NamespacedKey;

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
        new AdaptationSource("golem-animus-binding", GolemancyMessages.ANIMUS_BINDING_NAME, new GolemAnimusBinding.Config()),
        new AdaptationSource("golem-node-weaving", GolemancyMessages.NODE_WEAVING_NAME, new GolemNodeWeaving.Config()),
        new AdaptationSource("golem-frost-frame", GolemancyMessages.FROST_FRAME_NAME, new GolemFrostFrame.Config()),
        new AdaptationSource("golem-copper-frame", GolemancyMessages.COPPER_FRAME_NAME, new GolemCopperFrame.Config()),
        new AdaptationSource("golem-iron-frame", GolemancyMessages.IRON_FRAME_NAME, new GolemIronFrame.Config()),
        new AdaptationSource("golem-bone-frame", GolemancyMessages.BONE_FRAME_NAME, new GolemBoneFrame.Config()),
        new AdaptationSource("golem-guardian-core", GolemancyMessages.GUARDIAN_CORE_NAME, new GolemGuardianCore.Config()),
        new AdaptationSource("golem-gatherer-core", GolemancyMessages.GATHERER_CORE_NAME, new GolemGathererCore.Config()),
        new AdaptationSource("golem-harvester-core", GolemancyMessages.HARVESTER_CORE_NAME, new GolemHarvesterCore.Config()),
        new AdaptationSource("golem-lumber-core", GolemancyMessages.LUMBER_CORE_NAME, new GolemLumberCore.Config()),
        new AdaptationSource("golem-breaker-core", GolemancyMessages.BREAKER_CORE_NAME, new GolemBreakerCore.Config()),
        new AdaptationSource("golem-rancher-core", GolemancyMessages.RANCHER_CORE_NAME, new GolemRancherCore.Config()),
        new AdaptationSource("golem-sorter-core", GolemancyMessages.SORTER_CORE_NAME, new GolemSorterCore.Config()),
        new AdaptationSource("golem-angler-core", GolemancyMessages.ANGLER_CORE_NAME, new GolemAnglerCore.Config()),
        new AdaptationSource("golem-runic-augments", GolemancyMessages.RUNIC_AUGMENTS_NAME, new GolemRunicAugments.Config()),
        new AdaptationSource("golem-overseer", GolemancyMessages.OVERSEER_NAME, new GolemOverseer.Config()),
        new AdaptationSource("golem-mending", GolemancyMessages.MENDING_NAME, new GolemMending.Config()),
        new AdaptationSource("golem-stasis", GolemancyMessages.STASIS_NAME, new GolemStasis.Config()),
        new AdaptationSource("golem-masterwork", GolemancyMessages.MASTERWORK_NAME, new GolemMasterwork.Config()));
  }

  static List<RecipeSource> recipeSources(List<AdaptationSource> adaptations) {
    GolemAnimusBinding.Config animus = config(adaptations, "golem-animus-binding", GolemAnimusBinding.Config.class);
    GolemNodeWeaving.Config weaving = config(adaptations, "golem-node-weaving", GolemNodeWeaving.Config.class);
    GolemGuardianCore.Config guardian = config(adaptations, "golem-guardian-core", GolemGuardianCore.Config.class);
    GolemGathererCore.Config gatherer = config(adaptations, "golem-gatherer-core", GolemGathererCore.Config.class);
    GolemHarvesterCore.Config harvester = config(adaptations, "golem-harvester-core", GolemHarvesterCore.Config.class);
    GolemLumberCore.Config lumber = config(adaptations, "golem-lumber-core", GolemLumberCore.Config.class);
    GolemBreakerCore.Config breaker = config(adaptations, "golem-breaker-core", GolemBreakerCore.Config.class);
    GolemRancherCore.Config rancher = config(adaptations, "golem-rancher-core", GolemRancherCore.Config.class);
    GolemSorterCore.Config sorter = config(adaptations, "golem-sorter-core", GolemSorterCore.Config.class);
    GolemAnglerCore.Config angler = config(adaptations, "golem-angler-core", GolemAnglerCore.Config.class);
    GolemRunicAugments.Config runes = config(adaptations, "golem-runic-augments", GolemRunicAugments.Config.class);
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
    for (AdaptationRequirement requirement : AdaptationRequirement.parseAll(source.id(), source.config().requires)) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("id", requirement.adaptationId());
      entry.put("level", requirement.level());
      requires.add(entry);
    }
    Map<String, Object> adaptation = new LinkedHashMap<>();
    adaptation.put("id", source.id());
    adaptation.put("name", C.stripColor(AdaptLanguage.text(source.name())));
    adaptation.put("requires", requires);
    return adaptation;
  }

  private static void describe(GolemItemLooks looks, Map<String, Map<String, Object>> items, String item) {
    NamespacedKey id = item.indexOf(':') < 0 ? null : NamespacedKey.fromString(item.toLowerCase(Locale.ROOT));
    if (id == null || !GolemItems.NAMESPACE.equals(id.getNamespace()) || items.containsKey(item)) {
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

  record AdaptationSource(String id, TextKey name, AdaptationConfig config) {
  }

  record RecipeSource(String id, ConfiguredShapedRecipe spec) {
  }
}
