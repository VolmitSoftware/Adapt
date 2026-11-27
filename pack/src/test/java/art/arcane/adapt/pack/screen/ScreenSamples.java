package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationIcons;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationWords;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.link.MenuStyle;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.surface.PackPrompts;
import art.arcane.adapt.pack.surface.PackSession;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.PackWindows;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.platform.value.Id;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Sample views modelled on the design's stills (Skills with twelve seats, Hunter, Herbalism's cobweb recipes, the
 * mutated Pickaxe and Brewing skill, the Pickaxe and Brewing Mutate spread) with ids and line keys in the forms views
 * carry them, on built-in sprites named the way the view builders name them ({@code IconSprites}, texture form), and the
 * in-memory
 * server, windows and viewer the pack screens draw for.
 */
final class ScreenSamples implements PackViews, PackActions {
  static final String HUNTER = skill("hunter");
  static final String HERBALISM = skill("herbalism");
  static final String PICKAXE = skill("pickaxe");
  static final String BREWING = skill("brewing");
  static final String CHRONOS = skill("chronos");
  static final String TRAGOUL = skill("tragoul");
  static final String RANGED = skill("ranged");
  static final String ADRENALINE = adaptation("hunter", "adrenaline");
  static final String COBWEB = adaptation("herbalism", "cobweb");
  static final String MUTATED = new SkillPair(Id.adapt("pickaxe"), Id.adapt("brewing")).key();
  /** The mutated sample's id, which its own adaptations' ids extend. */
  static final String MUTATED_ID = Id.adapt("mutated/pickaxe/adapt/brewing").toString();
  /** The built-in kits, by skill name. */
  static final List<String> KITS = List.of("agility", "architect", "axes", "blocking", "brewing", "chronos", "crafting", "discovery", "enchanting",
      "excavation", "golemancy", "herbalism", "hunter", "kinetics", "nether", "pickaxe", "ranged", "rift", "seaborne", "stealth", "swords", "taming",
      "tragoul", "unarmed");
  static final int KIT_ADAPTATIONS = 12;
  private static final Object[][] SEATS = {
      {"agility", "Agility", 0x2A5E18, 24}, {"architect", "Architect", 0x7A4A1E, 0}, {"axes", "Axes", 0x6B440C, 0},
      {"blocking", "Blocking", 0x3E5F80, 0}, {"brewing", "Brewing", 0x763078, 35}, {"chronos", "Chronos", 0x1C6B5A, 23},
      {"crafting", "Crafting", 0x6B440C, 0}, {"discovery", "Discovery", 0x3E5F80, 0}, {"enchanting", "Enchanting", 0x763078, 0},
      {"excavation", "Excavation", 0x5E5A18, 27}, {"golemancy", "Golemancy", 0x2E5F6B, 28}, {"herbalism", "Herbalism", 0x2A6E18, 46},
      {"hunter", "Hunter", 0x8D261D, 31}, {"pickaxe", "Pickaxe", 0x23406E, 40}, {"ranged", "Ranged", 0x6B440C, 12},
      {"tragoul", "Tragoul", 0x8D261D, 17}};
  private static final String[] HUNTER_ADAPTATIONS = {"adrenaline", "big-game", "blood-trail", "invis", "jumpboost", "luck",
      "predator-focus", "regen", "resistance", "snare-line", "speed", "strength", "trophy-skinner"};
  /** Herbalism as the design still shows it: group, id, catalog name, short name (or null), max level, level, locked. */
  private static final Object[][] HERBALISM_ROWS = {
      {"Farming", "bee-shepherd", "Bee Shepherd", null, 4, 4, false}, {"Farming", "cobweb", "Webby Creator", null, 4, 1, true},
      {"Farming", "compost-cascade", "Compost Cascade", null, 5, 0, false},
      {"Farming", "drop-to-inventory", "Hoe Drop-To-Inventory", "Hoe Drop to Inventory", 4, 1, false},
      {"Farming", "growth-aura", "Growth Aura", null, 3, 2, false},
      {"Survival", "hippo", "Herbalist's Hippo", null, 3, 0, true}, {"Survival", "hungry-shield", "Hungry Shield", null, 4, 1, false},
      {"Survival", "luck", "Herbalist's Luck", null, 4, 2, false}, {"Survival", "mushroom-blocks", "Mushroom Maker", null, 3, 0, true},
      {"Survival", "myconid", "Herbalist's Myconid", null, 6, 3, false},
      {"Recipes", "replant", "Harvest & Replant", null, 6, 0, false}, {"Recipes", "rooted-footing", "Rooted Footing", null, 3, 2, false},
      {"Recipes", "seed-sower", "Seed Sower", null, 5, 3, false}, {"Recipes", "spore-bloom", "Spore Bloom", null, 5, 0, false},
      {"Recipes", "terralid", "Herbalist's Terralid", "Terralid", 3, 0, false}};
  private static final String[] PICKAXE_ADAPTATIONS = {"autosmelt", "chisel", "deep-core", "veinminer", "silk-spawner"};
  private static final String[] BREWING_ADAPTATIONS = {"absorption", "darkness", "haste", "lingering"};
  private static final String[] CHRONOS_ADAPTATIONS = {"accelerate", "borrowed-time", "deja-vu", "hourglass-guard", "instant-recall", "overtime",
      "pocket-watch", "rewind", "stasis-field", "time-bomb"};
  private static final String[] TRAGOUL_ADAPTATIONS = {"blood-pact", "bone-harvest", "corpse-explosion", "death-sense", "globe", "healing",
      "lance", "last-rites", "marrow-armor", "thorns"};
  private static final String[] RANGED_ADAPTATIONS = {"fetch-shot", "floaters", "force", "heartseeker", "heavy-draw", "lunge-shot", "piercing",
      "recovery", "ricochet-bolt", "webshot"};

  final PlayerRef viewer = viewer(UUID.fromString("00000000-0000-0000-0000-0000000000a7"));
  final Windows windows = new Windows();
  final CatalogIndex catalog;
  final PlayerView player;
  /** Whether every skill of the catalog may lead a Mutate spread, not only Pickaxe. */
  private final boolean anyLead;
  PackScreens screens;
  long now = 1_000L;

  ScreenSamples() {
    this(catalog(), false);
  }

  private ScreenSamples(CatalogIndex catalog, boolean anyLead) {
    this.catalog = catalog;
    this.player = player(catalog);
    this.anyLead = anyLead;
  }

  /**
   * Samples over every built-in kit: each kit's skill with {@link #KIT_ADAPTATIONS} adaptations, the first named by
   * {@link #kitAdaptation}, and the mutated skill {@link #kitMutation} led by each kit with the next kit in {@link #KITS}
   * as partner, long enough that the partner's adaptations fill the second leaf; any kit's skill leads a Mutate spread.
   */
  static ScreenSamples everyKit() {
    List<SkillView> skills = new ArrayList<>();
    for (String kit : KITS) {
      String id = skill(kit);
      String name = Character.toUpperCase(kit.charAt(0)) + kit.substring(1);
      skills.add(new SkillView(id, name, "Track and bring down prey.", skillIcon(id), 0x3E5F80, true, List.of(), List.of(enabled()),
          kitAdaptationViews(kit)));
    }
    for (int index = 0; index < KITS.size(); index++) {
      String lead = KITS.get(index);
      String partner = KITS.get((index + 1) % KITS.size());
      String key = kitMutation(lead);
      skills.add(new SkillView(key, "Mutated " + lead, "Two skills in one.",
          IconSprites.withSprite(IconRef.item("minecraft:iron_pickaxe"), MutationIcons.tile(PairKeys.parse(key))), 0x23406E, true,
          List.of(ids(kitAdaptationViews(lead)), ids(kitAdaptationViews(partner))), List.of(enabled()), both(lead, partner)));
    }
    return new ScreenSamples(new CatalogIndex(new CatalogView(1L, "en_US", skills), Map.of()), true);
  }

  /** The one adaptation of built-in kit {@code kit}'s skill in {@link #everyKit()}. */
  static String kitAdaptation(String kit) {
    return adaptation(kit, "ghost-armor");
  }

  /** The mutated skill in {@link #everyKit()} led by {@code kit}. */
  static String kitMutation(String kit) {
    int index = KITS.indexOf(kit);
    return new SkillPair(Id.adapt(kit), Id.adapt(KITS.get((index + 1) % KITS.size()))).key();
  }

  private static List<AdaptationView> both(String lead, String partner) {
    List<AdaptationView> both = new ArrayList<>(kitAdaptationViews(lead));
    both.addAll(kitAdaptationViews(partner));
    return both;
  }

  private static List<String> ids(List<AdaptationView> adaptations) {
    return adaptations.stream().map(AdaptationView::id).toList();
  }

  private static List<AdaptationView> kitAdaptationViews(String kit) {
    List<AdaptationView> views = new ArrayList<>();
    for (int index = 0; index < KIT_ADAPTATIONS; index++) {
      views.add(kitAdaptationView(index == 0 ? kitAdaptation(kit) : adaptation(kit, "ghost-armor-" + index), kit));
    }
    return views;
  }

  private static AdaptationView kitAdaptationView(String id, String kit) {
    return new AdaptationView(id, skill(kit), "Ghost's Armor", "Gain strength as your health drops.", adaptationIcon(adaptation("hunter", "adrenaline")),
        true, 3, false, List.of(2, 4, 6), List.of(2, 4, 6), Collections.nCopies(3, 0), List.of(), List.of(List.of("+5% Armor"), List.of("+10% Armor"),
        List.of("+15% Armor")), List.of(), List.of());
  }

  /** The screens over {@code resources}, for one viewer who reads the book. */
  PackScreens screens(PackResources resources) {
    HostMenus plain = (HostMenus) Proxy.newProxyInstance(HostMenus.class.getClassLoader(), new Class<?>[]{HostMenus.class},
        (Object proxy, Method method, Object[] args) -> null);
    PackMenuHost host = new PackMenuHost(plain, windows, (PlayerRef ignored) -> true, () -> resources);
    screens = new PackScreens(host, this, this, new Prompts(), new PackClock() {
      @Override
      public long now() {
        return now;
      }

      @Override
      public void later(PlayerRef ignored, int ticks, Runnable task) {
      }
    });
    return screens;
  }

  /** The id string of built-in skill {@code name}. */
  static String skill(String name) {
    return Id.adapt(name).toString();
  }

  /** The id string of built-in adaptation {@code name} of skill {@code skill}. */
  static String adaptation(String skill, String name) {
    return Id.adapt(skill + "/" + name.replace('-', '_')).toString();
  }

  /** An icon as the view builders make it ({@code ViewIcons}): an item fallback under the sprite's texture-form id. */
  private static IconRef icon(String sprite) {
    return IconSprites.withSprite(IconRef.item("minecraft:paper"), sprite);
  }

  private static IconRef skillIcon(String id) {
    return icon(IconSprites.skill(Id.parse(id)));
  }

  private static IconRef adaptationIcon(String id) {
    return icon(IconSprites.adaptation(Id.parse(id)));
  }

  private static CatalogIndex catalog() {
    List<SkillView> skills = new ArrayList<>();
    for (Object[] seat : SEATS) {
      String name = (String) seat[0];
      String id = skill(name);
      List<AdaptationView> adaptations = switch (name) {
        case "hunter" -> adaptations(name, HUNTER_ADAPTATIONS);
        case "herbalism" -> herbalism();
        case "pickaxe" -> adaptations(name, PICKAXE_ADAPTATIONS);
        case "brewing" -> adaptations(name, BREWING_ADAPTATIONS);
        case "chronos" -> adaptations(name, CHRONOS_ADAPTATIONS);
        case "tragoul" -> adaptations(name, TRAGOUL_ADAPTATIONS);
        case "ranged" -> adaptations(name, RANGED_ADAPTATIONS);
        default -> List.of();
      };
      skills.add(new SkillView(id, (String) seat[1], "Track and bring down prey.", skillIcon(id), (int) seat[2], true, List.of(),
          List.of(enabled()), adaptations));
    }
    List<AdaptationView> mutated = new ArrayList<>(adaptations("pickaxe", PICKAXE_ADAPTATIONS));
    mutated.addAll(adaptations("brewing", BREWING_ADAPTATIONS));
    mutated.addAll(own());
    skills.add(new SkillView(MUTATED, "Alchemic Prospector", "Ores that brew, potions that dig.",
        IconSprites.withSprite(IconRef.item("minecraft:iron_pickaxe"), MutationIcons.tile(PairKeys.parse(MUTATED))), 0x23406E, true,
        mutatedRows(), List.of(enabled()), mutated));
    Map<String, String> shortNames = new HashMap<>();
    for (Object[] row : HERBALISM_ROWS) {
      if (row[3] != null) {
        shortNames.put(adaptation("herbalism", (String) row[1]), (String) row[3]);
      }
    }
    return new CatalogIndex(new CatalogView(1L, "en_US", skills), shortNames);
  }

  /** The mutated sample's own adaptations, drawn with Pickaxe icons. */
  private static List<AdaptationView> own() {
    List<AdaptationView> own = new ArrayList<>();
    for (String[] entry : new String[][]{{"graft", "Distilled Graft", "chisel"}, {"echo", "Distilled Echo", "deep-core"}}) {
      String id = MUTATED_ID + "/" + entry[0];
      List<List<String>> stats = List.of(List.of("+5% Ore Yield"), List.of("+10% Ore Yield"), List.of("+15% Ore Yield"));
      own.add(new AdaptationView(id, MUTATED, entry[1], "A brew that works the stone.", adaptationIcon(adaptation("pickaxe", entry[2])), true, 3,
          false, List.of(4, 6, 8), List.of(4, 6, 8), Collections.nCopies(3, 0), List.of(), stats, List.of(), List.of()));
    }
    return own;
  }

  /** The mutated sample's row groups: the lead's upgrades, the partner's, and its own. */
  private static List<List<String>> mutatedRows() {
    List<List<String>> rows = new ArrayList<>();
    rows.add(adaptations("pickaxe", PICKAXE_ADAPTATIONS).stream().map(AdaptationView::id).toList());
    rows.add(adaptations("brewing", BREWING_ADAPTATIONS).stream().map(AdaptationView::id).toList());
    rows.add(own().stream().map(AdaptationView::id).toList());
    return rows;
  }

  private static List<AdaptationView> herbalism() {
    List<AdaptationView> list = new ArrayList<>();
    for (Object[] row : HERBALISM_ROWS) {
      String id = adaptation("herbalism", (String) row[1]);
      int max = (int) row[4];
      List<Integer> costs = new ArrayList<>();
      List<List<String>> stats = new ArrayList<>();
      for (int level = 1; level <= max; level++) {
        costs.add(level * 2);
        stats.add(List.of("+" + (level * 10) + "% Growth Chance", "+" + level + " Bee Radius"));
      }
      List<RequirementView> requirements = (boolean) row[6] ? List.of(new RequirementView(adaptation("herbalism", "bee-shepherd"), 5)) : List.of();
      list.add(new AdaptationView(id, HERBALISM, (String) row[2], "Gives you the ability to craft Cobwebs in a Crafting Table",
          adaptationIcon(id), true, max, false, costs, costs, Collections.nCopies(max, 0), requirements, stats, List.of(),
          List.of()));
    }
    return list;
  }

  private static List<AdaptationView> adaptations(String skill, String[] names) {
    List<AdaptationView> list = new ArrayList<>();
    for (String name : names) {
      String id = adaptation(skill, name);
      String title = Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('-', ' ');
      int max = id.equals(ADRENALINE) ? 5 : 3;
      List<Integer> costs = new ArrayList<>();
      List<List<String>> stats = new ArrayList<>();
      for (int level = 1; level <= max; level++) {
        costs.add(level * 2);
        stats.add(List.of(level * 5 + "% faster when hurt"));
      }
      List<PreferenceView> preferences = id.equals(ADRENALINE) ? List.of(enabled(), mode(), locked()) : List.of();
      list.add(new AdaptationView(id, skill(skill), title, "Gain strength as your health drops. Lower health means more damage dealt.",
          adaptationIcon(id), true, max, false, costs, costs, Collections.nCopies(max, 0), List.of(), stats, preferences,
          List.of()));
    }
    return list;
  }

  private static PreferenceView enabled() {
    return new PreferenceView("enabled", "Enabled", "ON", List.of(new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 1),
        new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 1)), true);
  }

  private static PreferenceView mode() {
    IconRef plain = IconRef.item("minecraft:paper");
    return new PreferenceView("mode", "Trigger", "LOW", List.of(new PreferenceChoiceView("LOW", "Below half health", plain, 1),
        new PreferenceChoiceView("ANY", "Any damage", plain, 2)), true);
  }

  private static PreferenceView locked() {
    return new PreferenceView("particles", "Particles", "ON", List.of(new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 1),
        new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 1)), false);
  }

  private static PlayerView player(CatalogIndex catalog) {
    List<SkillLineView> lines = new ArrayList<>();
    List<String> skills = new ArrayList<>();
    Map<String, List<List<String>>> rows = new LinkedHashMap<>();
    for (Object[] seat : SEATS) {
      int level = (int) seat[3];
      lines.add(new SkillLineView(skill((String) seat[0]), level * 100D, level, level == 0 ? 0D : 0.47D, 930D, level == 0 ? 0L : level * 2L + 10));
      skills.add(skill((String) seat[0]));
    }
    lines.add(new SkillLineView(MUTATED, 4000D, 40, 0.4D, 930D, 52L));
    skills.add(MUTATED);
    for (SkillView skill : catalog.catalog().skills()) {
      rows.put(skill.id(), skill.rowGroups().isEmpty() ? List.of(skill.adaptations().stream().map(AdaptationView::id).toList()) : skill.rowGroups());
    }
    Map<String, List<String>> groups = new LinkedHashMap<>();
    for (Object[] row : HERBALISM_ROWS) {
      groups.computeIfAbsent((String) row[0], (String ignored) -> new ArrayList<>()).add(adaptation("herbalism", (String) row[1]));
    }
    if (catalog.adaptation(COBWEB) != null) {
      rows.put(HERBALISM, new ArrayList<>(groups.values()));
    }
    Map<String, Integer> levels = new HashMap<>();
    levels.put(ADRENALINE, 2);
    levels.put(adaptation("hunter", "big-game"), 3);
    levels.put(adaptation("hunter", "blood-trail"), 1);
    for (Object[] row : HERBALISM_ROWS) {
      levels.put(adaptation("herbalism", (String) row[1]), (int) row[5]);
    }
    levels.put(adaptation("pickaxe", "autosmelt"), 2);
    return new PlayerView(new PlayerHeader(40, 0.5D, 3, 13, 1L, true), lines, levels,
        List.of(new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, HUNTER, "enabled"), "ON"),
            new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, ADRENALINE, "enabled"), "ON"),
            new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, ADRENALINE, "mode"), "LOW")),
        new PlayerAccess(false, true, true, skills, rows));
  }

  MutationSpreadView spread(String partner) {
    return spread(PICKAXE, partner);
  }

  private static MutationSpreadView spread(String leadId, String partner) {
    MutationWords words = new MutationWords("Mutate", "Lead", "Partner", "Gains", "Loses", "Click twice to mutate", "This cannot be undone",
        "Swap lead and partner", "Level {level}");
    List<MutationEntry> gains = List.of(entry(adaptation("brewing", "absorption")), entry(adaptation("brewing", "haste")),
        entry(adaptation("brewing", "lingering")));
    List<MutationEntry> loses = new ArrayList<>();
    for (String name : PICKAXE_ADAPTATIONS) {
      loses.add(entry(adaptation("pickaxe", name)));
    }
    MutationSide lead = new MutationSide(leadId, 40, gains, List.of(), List.of(), loses, "");
    MutationSide side = partner.isEmpty() ? MutationSide.NONE
        : new MutationSide(partner, 35, List.of(entry(adaptation("pickaxe", "veinminer"))), List.of(), List.of(),
        List.of(entry(adaptation("brewing", "darkness"))), "");
    List<MutationEntry> own = List.of(new MutationEntry("convergence", IconSprites.withSprite(IconRef.item("minecraft:paper"),
        IconSprites.stack(IconSprites.tinted(IconSprites.texture("glyph", "convergence-lead"), 0x23406E),
            IconSprites.tinted(IconSprites.texture("glyph", "convergence-partner"), 0x763078), IconSprites.texture("glyph", "convergence"))), "Convergence", "Both halves at once.", 0));
    List<MutationCandidate> candidates = new ArrayList<>();
    for (Object[] seat : SEATS) {
      String id = skill((String) seat[0]);
      if (!id.equals(leadId)) {
        boolean ready = (int) seat[3] >= 20;
        candidates.add(new MutationCandidate(id, (int) seat[3], ready ? MutationCandidate.State.ELIGIBLE : MutationCandidate.State.ALREADY_MUTATED,
            ready ? "" : "Needs level 20"));
      }
    }
    return new MutationSpreadView(leadId, partner, partner.isEmpty() ? "" : "Alchemic Prospector", partner.isEmpty() ? "" : "Lv 40",
        partner.isEmpty() ? List.of() : own, lead, side, candidates, words, partner.isEmpty() ? "" : MUTATED);
  }

  private static MutationEntry entry(String id) {
    String path = Id.parse(id).path();
    return new MutationEntry(id, adaptationIcon(id), path.substring(path.indexOf('/') + 1), "Changes with the mutation.", 1);
  }

  static List<RecipeView> recipes() {
    RecipeView.Item string = new RecipeView.Item(IconRef.item("minecraft:string"), "", "item.minecraft.string", 1, List.of());
    RecipeView.Item slime = new RecipeView.Item(IconRef.item("minecraft:slime_ball"), "", "item.minecraft.slime_ball", 1, List.of());
    RecipeView.Item cobweb = new RecipeView.Item(IconRef.item("minecraft:cobweb"), "Cobweb", "", 1, List.of());
    RecipeView.Item bone = new RecipeView.Item(IconRef.item("minecraft:bone_meal"), "Bone Meal", "", 3, List.of());
    return List.of(
        new RecipeView(COBWEB, RecipeView.Kind.SHAPED, 1, "web", 3, List.of(string, RecipeView.Item.NONE, string, RecipeView.Item.NONE, slime,
            RecipeView.Item.NONE, string, RecipeView.Item.NONE, string), cobweb),
        new RecipeView(COBWEB, RecipeView.Kind.SHAPELESS, 2, "", 0, List.of(string, string), bone));
  }

  @Override
  public CatalogIndex catalog(PlayerRef ignored) {
    return catalog;
  }

  @Override
  public PlayerView player(PlayerRef ignored) {
    return player;
  }

  @Override
  public long fingerprint(PlayerRef ignored) {
    return 1L;
  }

  @Override
  public List<RecipeView> recipes(PlayerRef ignored, String adaptationId) {
    return adaptationId.equals(COBWEB) ? recipes() : List.of();
  }

  @Override
  public XpGuideView guide(PlayerRef ignored, String skillId) {
    List<String> parents = skillId.equals(MUTATED) ? List.of(PICKAXE, BREWING) : List.of();
    return new XpGuideView(skillId, parents, List.of(new XpGuideView.Section(skillId, "Earn XP", List.of("Hunt animals"))));
  }

  @Override
  public List<KeptView> kept(PlayerRef ignored) {
    List<KeptView> kept = new ArrayList<>();
    for (String name : HUNTER_ADAPTATIONS) {
      if (kept.size() < 9) {
        kept.add(new KeptView(MUTATED, adaptation("hunter", name), 1));
      }
    }
    return kept;
  }

  @Override
  public List<String> groupOwners(PlayerRef ignored, String skillId) {
    return skillId.equals(MUTATED) ? List.of(PICKAXE, BREWING, MUTATED) : List.of();
  }

  @Override
  public List<ToolView> tools(PlayerRef ignored) {
    return List.of(tool(adaptation("pickaxe", "autosmelt"), "pickaxe/tool/prospectors_charge", "Prospector's Charge"),
        tool(adaptation("brewing", "absorption"), "brewing/tool/bandolier", "Bandolier"));
  }

  private static ToolView tool(String adaptationId, String path, String name) {
    Id id = Id.adapt(path);
    return new ToolView(adaptationId, id.toString(), name, IconSprites.withSprite(IconRef.item("minecraft:paper"), IconSprites.tool(id)), 2,
        List.of(new ToolView.Ingredient("minecraft:gunpowder", 2), new ToolView.Ingredient("#minecraft:logs", 1)));
  }

  @Override
  public List<String> groupLabels(PlayerRef ignored, String skillId) {
    if (!skillId.equals(HERBALISM)) {
      return List.of();
    }
    List<String> labels = new ArrayList<>();
    for (Object[] row : HERBALISM_ROWS) {
      if (!labels.contains((String) row[0])) {
        labels.add((String) row[0]);
      }
    }
    return labels;
  }

  @Override
  public List<SpendView> spending(PlayerRef ignored) {
    return List.of();
  }

  @Override
  public MenuStyle menuStyle(PlayerRef ignored) {
    return null;
  }

  @Override
  public void menus(PlayerRef ignored, MenuStyle style) {
  }

  @Override
  public boolean inactiveSkills(PlayerRef ignored) {
    return true;
  }

  @Override
  public boolean mutateOffered(PlayerRef ignored, String skillId) {
    return !skillId.equals(MUTATED);
  }

  @Override
  public boolean unmutateOffered(PlayerRef ignored, String skillId) {
    return skillId.equals(MUTATED);
  }

  @Override
  public List<String> mutationLeads(PlayerRef ignored) {
    List<String> leads = new ArrayList<>();
    for (Object[] seat : SEATS) {
      if ((int) seat[3] >= 20) {
        leads.add(skill((String) seat[0]));
      }
    }
    return leads;
  }

  @Override
  public MutationSpreadView spread(PlayerRef ignored, String lead, String partner) {
    return lead.equals(PICKAXE) || (anyLead && catalog.skill(lead) != null) ? spread(lead, partner) : null;
  }

  @Override
  public Outcome learn(PlayerRef ignored, String adaptationId, int level) {
    return new Outcome(false, "");
  }

  @Override
  public Outcome unlearn(PlayerRef ignored, String adaptationId, int level) {
    return new Outcome(false, "");
  }

  @Override
  public boolean setPreference(PlayerRef ignored, PreferenceOwner owner, String ownerId, String preferenceId, String value) {
    return false;
  }

  @Override
  public boolean resetPreference(PlayerRef ignored, PreferenceOwner owner, String ownerId) {
    return false;
  }

  @Override
  public Mutation mutate(PlayerRef ignored, String lead, String partner) {
    return new Mutation(false, "", spread(partner));
  }

  @Override
  public String unmutate(PlayerRef ignored, String skillId) {
    return null;
  }

  @Override
  public void open(PlayerRef ignored, Route route) {
  }

  private static PlayerRef viewer(UUID uuid) {
    return (PlayerRef) Proxy.newProxyInstance(PlayerRef.class.getClassLoader(), new Class<?>[]{PlayerRef.class},
        (Object proxy, Method method, Object[] args) -> switch (method.getName()) {
          case "uuid" -> uuid;
          case "name" -> "Viewer";
          case "hashCode" -> uuid.hashCode();
          case "equals" -> proxy == args[0];
          case "toString" -> "Viewer";
          default -> zero(method.getReturnType());
        });
  }

  /** The zero value of a primitive return type; null for any other. */
  private static Object zero(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    return type == double.class ? 0D : null;
  }

  /** Left-clicks {@code slot} of the open page as the viewer. */
  void click(PackSlot slot) {
    MenuButton button = slot.area() == PackSlot.Area.TOP ? windows.menu.button(slot.index()) : windows.surface.lower().get(slot.index());
    button.action().click(new MenuClick(viewer, windows.session, slot.clickSlot(windows.menu.size()), ClickKind.LEFT));
  }

  /** One window per viewer: the last page opened or updated, with or without the player slots. */
  static final class Windows implements PackWindows {
    PackSupport support = PackSupport.FULL;
    ChestMenu menu;
    PackSurface surface;
    PackSession session;

    @Override
    public PackSession open(PlayerRef viewer, ChestMenu opened, PackSurface drawn) {
      menu = opened;
      surface = drawn;
      Windows windows = this;
      session = new PackSession() {
        @Override
        public void update(ChestMenu next, PackSurface nextSurface) {
          windows.menu = next;
          windows.surface = nextSurface;
        }

        @Override
        public PackSurface surface() {
          return windows.surface;
        }

        @Override
        public void oneShot(PackSlot slot, int ticks) {
        }

        @Override
        public boolean lowerSlots() {
          return support.lowerSlots();
        }

        @Override
        public PlayerRef viewer() {
          return viewer;
        }

        @Override
        public ChestMenu menu() {
          return windows.menu;
        }

        @Override
        public boolean isOpen() {
          return true;
        }

        @Override
        public void update(ChestMenu next) {
          windows.menu = next;
        }

        @Override
        public void close() {
        }
      };
      return session;
    }

    @Override
    public PackSupport support(PlayerRef viewer) {
      return support;
    }
  }

  private static final class Prompts implements PackPrompts {
    @Override
    public boolean available(PlayerRef viewer) {
      return false;
    }

    @Override
    public void text(PlayerRef viewer, String title, String current, int maxLength, Consumer<String> answered, Runnable cancelled) {
      cancelled.run();
    }

    @Override
    public void number(PlayerRef viewer, String title, double current, Double min, Double max, Double step, DoubleConsumer answered,
                       Runnable cancelled) {
      cancelled.run();
    }

    @Override
    public void choice(PlayerRef viewer, String title, List<String> options, int current, IntConsumer answered, Runnable cancelled) {
      cancelled.run();
    }
  }
}
