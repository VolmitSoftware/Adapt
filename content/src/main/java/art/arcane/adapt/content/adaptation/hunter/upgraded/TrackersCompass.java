package art.arcane.adapt.content.adaptation.hunter.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Apex Predator's compass: set a kind from a creature or a blood trail, or pick one from the kinds this compass has
 * killed, and it points to the nearest living one in loaded chunks. Players are never tracked; bosses and named mobs
 * only at the quarry level, and only those the holder has hit.
 */
public final class TrackersCompass extends MutationTool<TrackersCompass.Data> {
  public static final Id ID = Id.adapt("hunter/tool/trackers_compass");
  private static final Id PLAYER = Id.minecraft("player");
  private static final Id COMPASS = Id.minecraft("compass");
  private static final String HUD_PURPOSE = "hunter-tracker";
  private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
  private static final double VERTICAL_RANGE = 48D;
  private static final int RESCAN_PULSES = 6;
  private final ApexPredator apex;
  private final Map<UUID, Lock> locks = playerState();

  public TrackersCompass(ApexPredator apex) {
    super(ToolItems.builder(ID, COMPASS, Data.class, new Data()).build(), apex, 1);
    this.apex = apex;
  }

  public static final class Data {
    List<String> kinds = new ArrayList<>();
    int selected;
    List<String> killed = new ArrayList<>();
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("compass")),
        Ingredient.of(Id.minecraft("leather")), Ingredient.of(Id.minecraft("bone"))), create(new Data())).withRequiredLevel(1));
  }

  @Override
  public void applyLore(Data data, List<String> lines) {
    for (int i = 0; i < data.kinds.size(); i++) {
      String kind = kindName(Id.parse(data.kinds.get(i)));
      lines.add((i == data.selected ? TextFormat.GREEN + "> " : TextFormat.DARK_GRAY + "  ") + kind);
    }
  }

  @Override
  public void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef player = use.player();
    EntityRef clicked = use.entity();
    if (clicked != null) {
      set(player, use.level(), clicked, clicked.type());
    } else if (use.sneaking()) {
      openMenu(player);
    } else {
      EntityRef prey = apex.trailedPrey(player);
      if (prey != null) {
        set(player, use.level(), prey, prey.type());
      } else {
        cycle(player);
      }
    }
  }

  /** Adds a kind the holder killed to every Tracker's Compass they carry. */
  void recordKill(PlayerRef player, Id kind) {
    if (PLAYER.equals(kind)) {
      return;
    }
    List<ItemRef> contents = player.inventory().contents();
    for (int slot = 0; slot < contents.size(); slot++) {
      ItemRef item = contents.get(slot);
      Data data = item == null || !COMPASS.equals(item.type()) ? null : read(item).orElse(null);
      if (data == null) {
        continue;
      }
      String id = kind.toString();
      data.killed.remove(id);
      data.killed.addFirst(id);
      while (data.killed.size() > Math.max(1, apex.getConfig().maxKilledKinds)) {
        data.killed.removeLast();
      }
      write(item, data);
      player.inventory().setItem(slot, item);
    }
  }

  boolean held(PlayerRef player) {
    return compass(player.inventory().mainHand()) || compass(player.inventory().offHand());
  }

  private boolean compass(@Nullable ItemRef item) {
    return item != null && COMPASS.equals(item.type()) && is(item);
  }

  /** The kind a Tracker's Compass in either hand follows, main hand first, or null. */
  public static @Nullable Id trackedKind(PlayerRef player) {
    Id kind = trackedKind(player.inventory().mainHand());
    return kind != null ? kind : trackedKind(player.inventory().offHand());
  }

  /** The kind this stack follows when it is a Tracker's Compass, or null. */
  public static @Nullable Id trackedKind(@Nullable ItemRef item) {
    return Mutations.tool(item).orElse(null) instanceof TrackersCompass compass
        ? selected(compass.read(item).orElse(null)) : null;
  }

  private static @Nullable Id selected(@Nullable Data data) {
    if (data == null || data.kinds == null || data.kinds.isEmpty()) {
      return null;
    }
    try {
      return Id.parse(data.kinds.get(Math.floorMod(data.selected, data.kinds.size())));
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  /** Points the held compass at the nearest living one of its kind; nothing when no compass is held. */
  void track(PlayerRef player, int level, Set<UUID> quarries) {
    Hand hand = is(player.inventory().mainHand()) ? Hand.MAIN_HAND : is(player.inventory().offHand()) ? Hand.OFF_HAND : null;
    Id kind = hand == null ? null : selected(read(player.inventory().hand(hand)).orElse(null));
    if (kind == null) {
      return;
    }
    LivingRef nearest = locate(player, kind, apex.getCompassRange(level), level >= apex.getConfig().quarryLevel ? quarries : Set.of());
    String name = kindName(kind);
    if (nearest == null) {
      HudStatus.ambient(player, HUD_PURPOSE, TextFormat.GRAY + Text.key("adapt.tool.hunter.trackers_compass.none").withMarkup("kind", name).resolve());
      return;
    }
    Loc from = player.location();
    Loc to = nearest.location();
    double dx = to.x() - from.x();
    double dz = to.z() - from.z();
    double horizontal = Math.sqrt((dx * dx) + (dz * dz));
    long distance = Math.round(Math.sqrt(from.distanceSquared(to)));
    HudStatus.ambient(player, HUD_PURPOSE, TextFormat.RED + arrow(dx, dz, from.yaw()) + " " + TextFormat.WHITE
        + Text.key("adapt.tool.hunter.trackers_compass.reading").withMarkup("kind", name).withMarkup("distance", String.valueOf(distance)).resolve());
    if (horizontal > 1D) {
      DiscoverySixthSense.pointTowards(apex, player, dx / horizontal, dz / horizontal, horizontal);
    }
  }

  /**
   * The last target found for this kind while it stays trackable, otherwise a fresh search; a target or an empty result
   * is kept for {@link #RESCAN_PULSES} pulses before searching again.
   */
  private @Nullable LivingRef locate(PlayerRef player, Id kind, double range, Set<UUID> quarries) {
    Lock lock = locks.get(player.uuid());
    if (lock != null && lock.kind().equals(kind) && lock.pulsesLeft() > 0
        && (lock.target() == null || trackable(player, lock.target(), range, quarries))) {
      locks.put(player.uuid(), new Lock(kind, lock.target(), lock.pulsesLeft() - 1));
      return lock.target();
    }
    LivingRef found = nearest(player, kind, range, quarries);
    locks.put(player.uuid(), new Lock(kind, found, RESCAN_PULSES));
    return found;
  }

  /** The nearest living, trackable creature of {@code kind} within {@code range} in loaded chunks. */
  static @Nullable LivingRef nearest(PlayerRef player, Id kind, double range, Set<UUID> quarries) {
    WorldRef world = player.world();
    if (world == null || PLAYER.equals(kind) || range <= 0) {
      return null;
    }
    Loc from = player.location();
    LivingRef best = null;
    double bestSquared = Double.MAX_VALUE;
    for (EntityRef entity : world.entitiesIn(Box.around(from.position(), range, Math.min(range, VERTICAL_RANGE), range),
        (EntityRef candidate) -> kind.equals(candidate.type()))) {
      if (!(entity instanceof LivingRef living) || !trackable(player, living, range, quarries)) {
        continue;
      }
      double squared = from.distanceSquared(entity.location());
      if (squared < bestSquared) {
        best = living;
        bestSquared = squared;
      }
    }
    return best;
  }

  private static boolean trackable(PlayerRef player, LivingRef target, double range, Set<UUID> quarries) {
    if (target instanceof PlayerRef || !target.isValid() || target.isDead()
        || !target.world().uid().equals(player.world().uid()) || (special(target) && !quarries.contains(target.uuid()))) {
      return false;
    }
    Loc from = player.location();
    Loc at = target.location();
    return Math.abs(at.y() - from.y()) <= VERTICAL_RANGE && from.distanceSquared(at) <= range * range;
  }

  /** A boss or a mob with a custom name: tracked only at the quarry level once the holder has hit it. */
  static boolean special(EntityRef entity) {
    String name = entity.customName();
    return entity.has(EntityTrait.BOSS) || (name != null && !name.isBlank());
  }

  static String arrow(double dx, double dz, float yaw) {
    double bearing = Math.toDegrees(Math.atan2(-dx, dz));
    double relative = ((bearing - yaw) % 360D + 540D) % 360D - 180D;
    return ARROWS[Math.floorMod((int) Math.round(relative / 45D), ARROWS.length)];
  }

  static String kindName(Id kind) {
    String[] words = kind.path().split("_");
    StringBuilder name = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (!name.isEmpty()) {
        name.append(' ');
      }
      name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
    }
    return name.toString();
  }

  private void set(PlayerRef player, int level, EntityRef source, Id kind) {
    ItemRef item = player.inventory().mainHand();
    Data data = read(item).orElse(null);
    boolean allowed = data != null && !(source instanceof PlayerRef) && !PLAYER.equals(kind)
        && source instanceof LivingRef && (!special(source) || level >= apex.getConfig().quarryLevel);
    if (!allowed) {
      HudStatus.ambient(player, HUD_PURPOSE, TextFormat.RED + Text.key("adapt.tool.hunter.trackers_compass.untrackable").resolve());
      return;
    }
    remember(player, item, data, kind, level);
  }

  private void remember(PlayerRef player, ItemRef item, Data data, Id kind, int level) {
    int cost = Math.max(0, apex.getConfig().setHungerCost);
    if (player.foodLevel() < cost || !apex.payHungerCost(player, "set", cost, () -> {
      player.setFoodLevel(Math.max(0, player.foodLevel() - cost));
      return true;
    })) {
      HudStatus.ambient(player, HUD_PURPOSE, TextFormat.RED + Text.key("adapt.tool.hunter.trackers_compass.hungry").resolve());
      return;
    }
    String id = kind.toString();
    data.kinds.remove(id);
    data.kinds.addFirst(id);
    while (data.kinds.size() > apex.getKindsRemembered(level)) {
      data.kinds.removeLast();
    }
    data.selected = 0;
    write(item, data);
    player.inventory().setMainHand(item);
    apex.compassSet(player);
    HudStatus.ambient(player, HUD_PURPOSE, TextFormat.GREEN + Text.key("adapt.tool.hunter.trackers_compass.tracking").withMarkup("kind", kindName(kind)).resolve());
  }

  private void cycle(PlayerRef player) {
    ItemRef item = player.inventory().mainHand();
    Data data = read(item).orElse(null);
    if (data == null || data.kinds.isEmpty()) {
      HudStatus.ambient(player, HUD_PURPOSE, TextFormat.GRAY + Text.key("adapt.tool.hunter.trackers_compass.empty").resolve());
      return;
    }
    data.selected = Math.floorMod(data.selected + 1, data.kinds.size());
    write(item, data);
    player.inventory().setMainHand(item);
    HudStatus.ambient(player, HUD_PURPOSE, TextFormat.GREEN + Text.key("adapt.tool.hunter.trackers_compass.tracking").withMarkup("kind", kindName(Id.parse(data.kinds.get(data.selected)))).resolve());
  }

  private void openMenu(PlayerRef player) {
    Data data = read(player.inventory().mainHand()).orElse(null);
    if (data == null || data.killed.isEmpty()) {
      HudStatus.ambient(player, HUD_PURPOSE, TextFormat.GRAY + Text.key("adapt.tool.hunter.trackers_compass.empty").resolve());
      return;
    }
    int shown = Math.min(data.killed.size(), ChestMenu.COLUMNS * 2);
    ChestMenu.Builder menu = ChestMenu.builder(Text.key("adapt.tool.hunter.trackers_compass.menu").resolve(), shown > ChestMenu.COLUMNS ? 2 : 1);
    for (int slot = 0; slot < shown; slot++) {
      Id kind = Id.parse(data.killed.get(slot));
      boolean tracked = data.kinds.contains(kind.toString());
      menu.set(slot, new MenuButton(MenuIcon.of(Id.minecraft("compass"), kindName(kind), List.of()).withGlint(tracked),
          (MenuClick click) -> pick(click, kind)));
    }
    AdaptApi.platform().menus().open(player, menu.build());
  }

  private void pick(MenuClick click, Id kind) {
    PlayerRef player = click.viewer();
    ItemRef item = player.inventory().mainHand();
    Data data = read(item).orElse(null);
    int level = level(player);
    if (level > 0 && data != null && data.killed.contains(kind.toString())) {
      remember(player, item, data, kind, level);
    }
    click.session().close();
  }

  private record Lock(Id kind, @Nullable LivingRef target, int pulsesLeft) {
  }
}
