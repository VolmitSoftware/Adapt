package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationEntry;
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
import art.arcane.adapt.pack.link.MenuStyle;
import art.arcane.adapt.pack.surface.PackPrompts;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/** Fixed view models for pack screen tests, and fakes for the server, the clock and prompts. */
final class ScreenFixtures {
  static final IconRef AGILITY_ICON = new IconRef("minecraft:feather", 0, null, "adapt:gui/skill/agility");
  static final IconRef RIFT_ICON = new IconRef("minecraft:ender_pearl", 0, null, "adapt:gui/skill/rift");
  static final IconRef JUMP_ICON = new IconRef("minecraft:rabbit_foot", 3, "adapt:jump", null);
  static final IconRef WALL_ICON = new IconRef("minecraft:cobblestone_wall", 0, null, "adapt:gui/adaptation/agility-wall-jump");
  static final IconRef GATE_ICON = IconRef.item("minecraft:end_portal_frame");
  static final IconRef PLAIN = IconRef.item("minecraft:paper");

  private ScreenFixtures() {
  }

  static PreferenceView enabled() {
    return new PreferenceView("enabled", "Enabled", "ON", List.of(new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 1),
        new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 1)), true);
  }

  static PreferenceView mode() {
    return new PreferenceView("mode", "Mode", "SOFT", List.of(new PreferenceChoiceView("SOFT", "Soft", PLAIN, 1),
        new PreferenceChoiceView("HARD", "Hard", PLAIN, 2), new PreferenceChoiceView("WILD", "Wild", PLAIN, 3)), true);
  }

  static AdaptationView superJump() {
    return superJump(false);
  }

  static AdaptationView superJump(boolean permanent) {
    return new AdaptationView("adapt:agility/super_jump", "adapt:agility", "Super Jump", "Jump higher.", JUMP_ICON, true, 3, permanent,
        List.of(2, 3, 4), List.of(2, 3, 4), List.of(1, 0, 0), List.of(),
        List.of(List.of("+1 block"), List.of("+2 blocks"), List.of("+3 blocks")), List.of(mode()), List.of());
  }

  static AdaptationView wallJump() {
    return new AdaptationView("adapt:agility/wall_jump", "adapt:agility", "Wall Jump", "Jump off walls.", WALL_ICON, true, 2, false,
        List.of(5, 7), List.of(5, 7), List.of(1, 0), List.of(new RequirementView("adapt:agility/super_jump", 2)),
        List.of(List.of("+1 jump"), List.of("+2 jumps")), List.of(), List.of());
  }

  static AdaptationView gate() {
    return new AdaptationView("adapt:rift/gate", "adapt:rift", "Gate", "Open a gate.", GATE_ICON, true, 1, true, List.of(9), List.of(0),
        List.of(1), List.of(), List.of(List.of("One gate")), List.of(), List.of());
  }

  static CatalogIndex catalog() {
    return catalog(false);
  }

  /** The catalog, with learning Super Jump permanent when {@code permanent}. */
  static CatalogIndex catalog(boolean permanent) {
    SkillView agility = new SkillView("adapt:agility", "Agility", "Move faster.", AGILITY_ICON, 0x55FFFF, true,
        List.of(), List.of(enabled()), List.of(superJump(permanent), wallJump()));
    SkillView rift = new SkillView("adapt:rift", "Rift", "Bend space.", RIFT_ICON, 0xAA55FF, true, List.of(), List.of(enabled()),
        List.of(gate()));
    return new CatalogIndex(new CatalogView(42L, "en_US", List.of(agility, rift)), Map.of("adapt:agility/wall_jump", "Walls"));
  }

  static PlayerView player(int superJump, long knowledge) {
    return player(superJump, knowledge, false);
  }

  /** {@link #player(int, long)} with debug mode on, where learning is free and never needs confirming. */
  static PlayerView debugPlayer(int superJump, long knowledge) {
    return player(superJump, knowledge, true);
  }

  private static PlayerView player(int superJump, long knowledge, boolean debug) {
    Map<String, Integer> levels = new HashMap<>();
    if (superJump > 0) {
      levels.put("adapt:agility/super_jump", superJump);
    }
    return new PlayerView(new PlayerHeader(12, 0.5D, superJump > 0 ? 1 : 0, 12, 1L, true),
        List.of(new SkillLineView("adapt:agility", 100D, 5, 0.25D, 50D, knowledge), new SkillLineView("adapt:rift", 0D, 0, 0D, 10D, 0L)),
        levels,
        List.of(new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, "adapt:agility", "enabled"), "ON"),
            new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, "adapt:agility/super_jump", "mode"), "SOFT")),
        new PlayerAccess(debug, true, true, List.of("adapt:agility", "adapt:rift"),
            Map.of("adapt:agility", List.of(List.of("adapt:agility/super_jump", "adapt:agility/wall_jump")), "adapt:rift", List.of(List.of("adapt:rift/gate")))));
  }

  static List<RecipeView> recipes() {
    RecipeView.Item stick = new RecipeView.Item(IconRef.item("minecraft:stick"), "", "item.minecraft.stick", 1, List.of());
    RecipeView.Item feather = new RecipeView.Item(IconRef.item("minecraft:feather"), "", "item.minecraft.feather", 2, List.of());
    RecipeView.Item boots = new RecipeView.Item(IconRef.item("minecraft:leather_boots"), "Springs", "", 1, List.of());
    RecipeView.Item pads = new RecipeView.Item(IconRef.item("minecraft:rabbit_hide"), "Pads", "", 3, List.of());
    return List.of(
        new RecipeView("adapt:agility/super_jump", RecipeView.Kind.SHAPED, 1, "springs", 2,
            List.of(stick, RecipeView.Item.NONE, feather, stick), boots),
        new RecipeView("adapt:agility/super_jump", RecipeView.Kind.SHAPELESS, 2, "springs", 0, List.of(feather, feather), boots),
        new RecipeView("adapt:agility/super_jump", RecipeView.Kind.SHAPELESS, 3, "", 0, List.of(feather), pads));
  }

  static MutationSpreadView spread(String partner) {
    MutationWords words = new MutationWords("Mutate", "Lead", "Partner", "Gains", "Loses", "Hold to mutate",
        "This cannot be undone", "Swap lead", "Level {level}");
    MutationEntry gain = new MutationEntry("adapt:agility/upgrade/blink_jump", JUMP_ICON, "Blink Jump", "Jump through space.\n13 knowledge", 0);
    MutationEntry lost = new MutationEntry("adapt:agility/super_jump", JUMP_ICON, "Super Jump I", "", 1);
    MutationSide lead = new MutationSide("adapt:agility", 5, List.of(gain), List.of(), List.of(), List.of(lost), "");
    MutationSide side = partner.isEmpty() ? MutationSide.NONE : new MutationSide(partner, 4, List.of(), List.of(), List.of(),
        List.of(), "1 Rift level");
    return new MutationSpreadView("adapt:agility", partner, partner.isEmpty() ? "" : "Blink Runner",
        partner.isEmpty() ? "" : "Level 4 · 3 knowledge", partner.isEmpty() ? List.of() : List.of(gain), lead, side,
        List.of(new MutationCandidate("adapt:rift", 4, MutationCandidate.State.ELIGIBLE, ""),
            new MutationCandidate("adapt:agility", 5, MutationCandidate.State.ALREADY_MUTATED, "Already mutated")),
        words, "");
  }

  /** The server stand-in: fixed views that a test can swap, and every action recorded. */
  static final class Server implements PackViews, PackActions {
    CatalogIndex catalog = ScreenFixtures.catalog();
    PlayerView player = ScreenFixtures.player(1, 3L);
    long fingerprint = 1L;
    boolean mutateOffered = true;
    boolean inactive = true;
    MenuStyle style;
    boolean unmutateOffered;
    List<KeptView> kept = List.of();
    Map<String, List<String>> owners = Map.of();
    Map<String, List<String>> labels = Map.of();
    List<ToolView> tools = List.of();
    List<RecipeView> recipes = ScreenFixtures.recipes();
    final List<String> calls = new ArrayList<>();
    final List<Route> routes = new ArrayList<>();
    PackScreens screens;
    Outcome outcome = new Outcome(true, "Done");
    /** What a learn does to the stored player before it answers, given the level learned to. */
    IntConsumer onLearn = (int level) -> { };

    @Override
    public CatalogIndex catalog(PlayerRef viewer) {
      return catalog;
    }

    @Override
    public PlayerView player(PlayerRef viewer) {
      return player;
    }

    @Override
    public long fingerprint(PlayerRef viewer) {
      return fingerprint;
    }

    @Override
    public List<RecipeView> recipes(PlayerRef viewer, String adaptationId) {
      return adaptationId.equals("adapt:agility/super_jump") ? recipes : List.of();
    }

    @Override
    public XpGuideView guide(PlayerRef viewer, String skillId) {
      return new XpGuideView(skillId, List.of(), List.of(new XpGuideView.Section(skillId, "Earn XP", List.of("Sprint", "Jump"))));
    }

    @Override
    public List<KeptView> kept(PlayerRef viewer) {
      return kept;
    }

    @Override
    public List<String> groupLabels(PlayerRef viewer, String skillId) {
      return labels.getOrDefault(skillId, List.of());
    }

    @Override
    public List<String> groupOwners(PlayerRef viewer, String skillId) {
      return owners.getOrDefault(skillId, List.of());
    }

    @Override
    public List<ToolView> tools(PlayerRef viewer) {
      return tools;
    }

    @Override
    public List<SpendView> spending(PlayerRef viewer) {
      return List.of(new SpendView("adapt:agility", "adapt:agility/super_jump", 1, 2, 1, SpendView.Source.LEARNED));
    }

    @Override
    public MenuStyle menuStyle(PlayerRef viewer) {
      return style;
    }

    @Override
    public void menus(PlayerRef viewer, MenuStyle chosen) {
      calls.add("menus " + chosen);
      style = chosen;
    }

    @Override
    public boolean inactiveSkills(PlayerRef viewer) {
      return inactive;
    }

    @Override
    public boolean mutateOffered(PlayerRef viewer, String skillId) {
      return mutateOffered;
    }

    @Override
    public boolean unmutateOffered(PlayerRef viewer, String skillId) {
      return unmutateOffered;
    }

    @Override
    public List<String> mutationLeads(PlayerRef viewer) {
      return List.of("adapt:agility");
    }

    @Override
    public MutationSpreadView spread(PlayerRef viewer, String lead, String partner) {
      calls.add("spread " + lead + " " + partner);
      return lead.equals("adapt:agility") ? ScreenFixtures.spread(partner) : null;
    }

    @Override
    public Outcome learn(PlayerRef viewer, String adaptationId, int level) {
      calls.add("learn " + adaptationId + " " + level);
      onLearn.accept(level);
      fingerprint++;
      return outcome;
    }

    @Override
    public Outcome unlearn(PlayerRef viewer, String adaptationId, int level) {
      calls.add("unlearn " + adaptationId + " " + level);
      return outcome;
    }

    @Override
    public boolean setPreference(PlayerRef viewer, PreferenceOwner owner, String ownerId, String preferenceId, String value) {
      calls.add("set " + owner + " " + ownerId + " " + preferenceId + " " + value);
      return true;
    }

    @Override
    public boolean resetPreference(PlayerRef viewer, PreferenceOwner owner, String ownerId) {
      calls.add("reset " + owner + " " + ownerId);
      return true;
    }

    @Override
    public Mutation mutate(PlayerRef viewer, String lead, String partner) {
      calls.add("mutate " + lead + " " + partner);
      return new Mutation(false, "Not now", ScreenFixtures.spread(partner));
    }

    @Override
    public String unmutate(PlayerRef viewer, String skillId) {
      calls.add("unmutate " + skillId);
      return "Undone";
    }

    @Override
    public void open(PlayerRef viewer, Route route) {
      routes.add(route);
      switch (route) {
        case Route.Skills ignored -> screens.openSkills(viewer);
        case Route.Skill skill -> screens.openSkill(viewer, skill.skillId());
        case Route.Adaptation adaptation -> screens.openAdaptation(viewer, adaptation.adaptationId());
        case Route.Mutate mutate -> screens.openMutate(viewer, mutate.skillId());
      }
    }
  }

  /** A clock the test moves by hand; delayed tasks wait in a queue until {@link #run}. */
  static final class Clock implements PackClock {
    long now = 1_000L;
    final List<Runnable> later = new ArrayList<>();

    @Override
    public long now() {
      return now;
    }

    @Override
    public void later(PlayerRef viewer, int ticks, Runnable task) {
      later.add(task);
    }

    void run() {
      List<Runnable> due = new ArrayList<>(later);
      later.clear();
      due.forEach(Runnable::run);
    }
  }

  /** Prompts answered with a fixed text, or cancelled when it is null. */
  static final class Prompts implements PackPrompts {
    String answer;
    final List<String> asked = new ArrayList<>();

    @Override
    public boolean available(PlayerRef viewer) {
      return true;
    }

    @Override
    public void text(PlayerRef viewer, String title, String current, int maxLength, Consumer<String> answered,
                     Runnable cancelled) {
      asked.add(title);
      if (answer == null) {
        cancelled.run();
      } else {
        answered.accept(answer);
      }
    }

    @Override
    public void number(PlayerRef viewer, String title, double current, Double min, Double max, Double step,
                       DoubleConsumer answered, Runnable cancelled) {
      cancelled.run();
    }

    @Override
    public void choice(PlayerRef viewer, String title, List<String> options, int current, IntConsumer answered,
                       Runnable cancelled) {
      cancelled.run();
    }
  }
}
