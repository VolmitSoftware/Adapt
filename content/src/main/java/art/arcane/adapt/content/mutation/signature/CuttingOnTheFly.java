package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.item.MaterialValues;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Architect leading Crafting, replacing Convergence: a chalk fill or template build that runs short of a shape cuts more
 * from the base blocks you carry, by the server's stonecutter and crafting recipes at their exact yields. Cutting runs
 * only while that build runs, and stops when the inventory has no room for the pieces.
 */
public final class CuttingOnTheFly extends Signature<CuttingOnTheFly.Config> {
  private final Map<UUID, Preview> previews = new ConcurrentHashMap<>();
  private final Map<UUID, List<ItemRef>> pending = new ConcurrentHashMap<>();
  private final Map<UUID, Cutting> cutting = new ConcurrentHashMap<>();
  private final Map<Id, Cuts> cuts = new ConcurrentHashMap<>();

  public CuttingOnTheFly(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stonecutter"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, pieces(level), 1);
    if (level >= getConfig().containerLevel) {
      statLore(v, TextFormat.GREEN, "+ ", "", 2);
    } else {
      statLore(v, TextFormat.GRAY, "- ", "", 2);
    }
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.LOW, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.protectionProbe() || e.hand() != Hand.MAIN_HAND
        || (e.action() != InteractAction.RIGHT_CLICK_AIR && e.action() != InteractAction.RIGHT_CLICK_BLOCK)) {
      return;
    }
    PlayerRef player = e.player();
    ItemRef held = player.inventory().mainHand();
    ChalkWandItem.WandData wand = ChalkWandItem.read(held);
    if (wand != null) {
      ItemRef off = player.inventory().offHand();
      if (player.isSneaking() && !wand.plan().isEmpty() && ArchitectBuilds.placedState(off) != null) {
        start(player, List.of(off.copyWithAmount(1)), player.location());
      }
      return;
    }
    MasonsTemplate template = MasonsTemplate.find();
    MasonsTemplate.Data data = template == null ? null : template.read(held).orElse(null);
    BlockRef block = e.clickedBlock();
    if (data == null || !data.hasPattern() || block == null || e.clickedFace() == null) {
      return;
    }
    BlockRef anchor = block.relative(e.clickedFace());
    if (!player.isSneaking()) {
      Preview shown = previews.get(player.uuid());
      long now = System.currentTimeMillis();
      if (shown == null || !shown.matches(anchor, data.pattern.fingerprint(), now)) {
        previews.put(player.uuid(), new Preview(anchor.world().uid(), anchor.x(), anchor.y(), anchor.z(),
            data.pattern.fingerprint(), now + (long) (getConfig().previewSeconds * 1000D)));
        return;
      }
      previews.remove(player.uuid());
    }
    start(player, units(data.pattern, anchor), anchor.location());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    previews.remove(id);
    pending.remove(id);
    Cutting running = cutting.remove(id);
    if (running != null) {
      running.stop();
    }
  }

  private void start(PlayerRef player, List<ItemRef> units, Loc at) {
    if (units.isEmpty()) {
      return;
    }
    pending.put(player.uuid(), units);
    try {
      fire(player, null, at, false);
    } finally {
      pending.remove(player.uuid());
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    List<ItemRef> units = pending.remove(player.uuid());
    if (units == null) {
      return Outcome.MISSED;
    }
    List<ItemRef> cuttable = new ArrayList<>(units.size());
    for (ItemRef unit : units) {
      if (cuttable.size() < Math.max(1, getConfig().maxShapes) && plain(unit) && !recipes(unit.type()).isEmpty()) {
        cuttable.add(unit);
      }
    }
    if (cuttable.isEmpty()) {
      return Outcome.MISSED;
    }
    Cutting previous = cutting.remove(player.uuid());
    if (previous != null) {
      previous.stop();
    }
    Cutting job = new Cutting(player, cuttable, pieces(use.level()), use.level() >= getConfig().containerLevel);
    job.task = AdaptApi.scheduler().repeatEntity(player, job::tick, 0, 1, job::stop);
    if (job.task == null) {
      return Outcome.MISSED;
    }
    cutting.put(player.uuid(), job);
    return Outcome.INSTANT;
  }

  /**
   * The crafting and stonecutter recipes that make {@code shape}, best yield first; kept per shape until the server's
   * recipe index hands out a new list, as it does after a reload.
   */
  List<MaterialValues.Recipe> recipes(Id shape) {
    List<MaterialValues.Recipe> source = MaterialValues.recipes(shape);
    Cuts cached = cuts.get(shape);
    if (cached != null && cached.source() == source) {
      return cached.usable();
    }
    List<MaterialValues.Recipe> usable = new ArrayList<>();
    for (MaterialValues.Recipe recipe : source) {
      if (cuttable(recipe, shape)) {
        usable.add(recipe);
      }
    }
    usable.sort(Comparator.comparingDouble(CuttingOnTheFly::yield).reversed());
    Cuts fresh = new Cuts(source, List.copyOf(usable));
    cuts.put(shape, fresh);
    return fresh.usable();
  }

  /** A crafting table or stonecutter recipe for {@code shape} with real inputs, none of them the shape itself. */
  static boolean cuttable(MaterialValues.Recipe recipe, Id shape) {
    MaterialValues.Count output = recipe.output();
    List<MaterialValues.Count> inputs = recipe.inputs();
    if ((recipe.kind() != MaterialValues.Kind.CRAFTING && recipe.kind() != MaterialValues.Kind.STONECUTTING)
        || output.amount() <= 0 || !shape.equals(output.material()) || inputs.isEmpty()) {
      return false;
    }
    for (MaterialValues.Count input : inputs) {
      if (input.amount() <= 0 || shape.equals(input.material())) {
        return false;
      }
    }
    return true;
  }

  private static double yield(MaterialValues.Recipe recipe) {
    int spent = 0;
    for (MaterialValues.Count input : recipe.inputs()) {
      spent += input.amount();
    }
    return recipe.output().amount() / (double) Math.max(1, spent);
  }

  int pieces(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().piecesAtFirstLevel,
        getConfig().piecesAtTopLevel));
  }

  private static List<ItemRef> units(ArchitectPattern pattern, BlockRef anchor) {
    Map<Id, ItemRef> units = new LinkedHashMap<>();
    for (ArchitectPattern.Placement placement : pattern.placements(anchor, 0, false)) {
      Id item = placement.state().placementItem();
      if (item != null && !units.containsKey(item)) {
        units.put(item, items().create(item, 1));
      }
    }
    return List.copyOf(units.values());
  }

  private static boolean plain(ItemRef unit) {
    return unit.isSimilar(items().create(unit.type(), 1));
  }

  /** How many of each plain item (no name or data) the inventory holds, read in one pass. */
  static Map<Id, Integer> tally(PlayerInventoryRef inventory) {
    Map<Id, Integer> counts = new HashMap<>();
    Map<Id, ItemRef> plain = new HashMap<>();
    for (ItemRef item : inventory.contents()) {
      if (item == null || item.isEmpty()) {
        continue;
      }
      Id type = item.type();
      if (item.isSimilar(plain.computeIfAbsent(type, (Id id) -> items().create(id, 1)))) {
        counts.merge(type, item.amount(), Integer::sum);
      }
    }
    return counts;
  }

  private static ItemOps items() {
    return AdaptApi.platform().host().items();
  }

  private record Cuts(List<MaterialValues.Recipe> source, List<MaterialValues.Recipe> usable) {
  }

  private record Preview(UUID world, int x, int y, int z, int pattern, long until) {
    boolean matches(BlockRef spot, int fingerprint, long now) {
      return now <= until && world.equals(spot.world().uid()) && x == spot.x() && y == spot.y() && z == spot.z()
          && pattern == fingerprint;
    }
  }

  /** Keeps a few of each shape in stock while the player's build runs, cutting one recipe at a time. */
  private final class Cutting {
    private final PlayerRef player;
    private final List<ItemRef> units;
    private final boolean fromContainers;
    private int left;
    private int idle;
    private @Nullable TaskHandle task;

    private Cutting(PlayerRef player, List<ItemRef> units, int left, boolean fromContainers) {
      this.player = player;
      this.units = units;
      this.left = left;
      this.fromContainers = fromContainers;
    }

    private void tick() {
      if (!player.isOnline() || left <= 0) {
        stop();
        return;
      }
      if (!BuildBatches.isRunning(player)) {
        if (++idle > Math.max(1, getConfig().idleTicks)) {
          stop();
        }
        return;
      }
      idle = 0;
      stock();
    }

    /** Cuts each shape up to the stock a build may use in one tick, so the build never runs dry mid-tick. */
    private void stock() {
      Map<Id, Integer> carried = tally(player.inventory());
      int wanted = Math.max(1, getConfig().stockPerShape);
      for (ItemRef unit : units) {
        while (carried.getOrDefault(unit.type(), 0) < wanted) {
          if (!cut(unit, carried)) {
            break;
          }
        }
      }
    }

    /** Runs the best recipe for {@code unit} the player can pay once; false when none can be paid or there is no room. */
    private boolean cut(ItemRef unit, Map<Id, Integer> carried) {
      if (left <= 0) {
        return false;
      }
      for (MaterialValues.Recipe recipe : recipes(unit.type())) {
        if (pay(recipe, carried)) {
          int made = recipe.output().amount();
          left -= made;
          carried.merge(unit.type(), made, Integer::sum);
          if (!keep(items().create(unit.type(), made))) {
            left = 0;
            return false;
          }
          return true;
        }
      }
      return false;
    }

    private boolean pay(MaterialValues.Recipe recipe, Map<Id, Integer> carried) {
      for (MaterialValues.Count input : recipe.inputs()) {
        if (carried.getOrDefault(input.material(), 0) < input.amount()
            && !(fromContainers && pull(input.material(), input.amount(), carried))) {
          return false;
        }
      }
      PlayerInventoryRef inventory = player.inventory();
      List<ItemRef> taken = new ArrayList<>(recipe.inputs().size());
      for (MaterialValues.Count input : recipe.inputs()) {
        int removed = inventory.removeSimilar(items().create(input.material(), 1), input.amount());
        carried.merge(input.material(), -removed, Integer::sum);
        if (removed > 0) {
          taken.add(items().create(input.material(), removed));
        }
        if (removed < input.amount()) {
          for (ItemRef back : taken) {
            carried.merge(back.type(), back.amount(), Integer::sum);
            keep(back);
          }
          return false;
        }
      }
      return true;
    }

    /** Takes one stack of {@code base} out of a carried container; true when the player then holds {@code needed}. */
    private boolean pull(Id base, int needed, Map<Id, Integer> carried) {
      ArchitectSupplyLine supply = ArchitectBuilds.find(ArchitectSupplyLine.ID, ArchitectSupplyLine.class);
      ItemRef pulled = supply == null ? null : supply.pullFromContainers(player, items().create(base, 1));
      if (pulled == null || pulled.isEmpty()) {
        return false;
      }
      carried.merge(base, pulled.amount(), Integer::sum);
      if (!keep(pulled)) {
        left = 0;
        return false;
      }
      return carried.getOrDefault(base, 0) >= needed;
    }

    /** Puts {@code item} in the inventory; what does not fit drops at the player's feet, and false says so. */
    private boolean keep(ItemRef item) {
      List<ItemRef> overflow = player.inventory().addItem(item);
      for (ItemRef spilled : overflow) {
        player.world().dropItemNaturally(player.location().position(), spilled);
      }
      return overflow.isEmpty();
    }

    private void stop() {
      if (task != null) {
        task.cancel();
      }
      cutting.remove(player.uuid(), this);
    }
  }

  @ConfigDescription("Chalk fills and template builds cut the shapes they run short of from the base blocks you carry.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Pieces one build may cut at level 1.", impact = "Higher values let longer builds keep cutting.")
    int piecesAtFirstLevel = 64;
    @ConfigDoc(value = "Pieces one build may cut at the top level.", impact = "Higher values let longer builds keep cutting.")
    int piecesAtTopLevel = 512;
    @ConfigDoc(value = "Level from which base blocks are also taken from carried bundles, backpacks and shulker boxes.", impact = "Lower values reach carried containers sooner.")
    int containerLevel = 3;
    @ConfigDoc(value = "Pieces of each shape kept in the inventory while a build runs; keep it at or above the fastest build pace.", impact = "Higher values leave more spare pieces when a build ends.")
    int stockPerShape = 16;
    @ConfigDoc(value = "Most different shapes one build cuts.", impact = "Higher values cover templates with more kinds of blocks.")
    int maxShapes = 16;
    @ConfigDoc(value = "Seconds a template preview counts as waiting for its second right-click.", impact = "Match it to the Mason's Template preview time.")
    double previewSeconds = 30;
    @ConfigDoc(value = "Ticks without a running build before cutting stops.", impact = "Higher values wait longer for a build to start.")
    int idleTicks = 3;
    @ConfigDoc(value = "Cooldown after a build starts cutting, in milliseconds.", impact = "Higher values space out builds that cut.")
    long cooldownMillis = 0;
  }
}
