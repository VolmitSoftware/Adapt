package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildPayment;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Crafting leading Architect, replacing Convergence: a Mason's Template and a chest craft into a Prefab Crate that
 * holds the template and every block its copy needs, taken from your inventory when the crate is taken from the table
 * (the result slot only shows an empty crate). Right-click a spot with the crate to see the build, left-click to turn
 * it, right-click the same spot again to unfold it there, paid only from the crate; what could not be placed comes
 * back with the template.
 */
public final class PrefabCrate extends Signature<PrefabCrate.Config> {
  public static final Id TOOL_ID = Id.adapt("crafting/tool/prefab_crate");
  private static final Id CHEST = Id.minecraft("chest");
  private static final Rgb GHOST = new Rgb(255, 210, 140);
  private static final int STORAGE_SLOTS = 36;
  private static final int HOTBAR_SLOTS = 9;
  private final Crate crate;
  private final Map<UUID, Preview> previews = new ConcurrentHashMap<>();
  private final Map<UUID, BlockRef> unfolding = new ConcurrentHashMap<>();

  public PrefabCrate(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(CHEST);
    crate = registerTool(new Crate(this));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, blocks(level), 1);
    statLore(v, pace(level), 2);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  public Crate crate() {
    return crate;
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PrepareItemCraftEvent e) {
    if (!crate.isRecipe(e.recipeKey())) {
      return;
    }
    Packing packing = packing(e.player(), e.matrix());
    if (packing == null) {
      return;
    }
    if (!packing.fits() || !packing.missing().isEmpty()) {
      e.setResult(items().empty());
      if (packing.fits()) {
        HudStatus.show(e.player(), TextFormat.RED + Text.key("adapt.tool.architect.masons_template.short").withMarkup("items", shortList(packing.missing())).resolve());
      }
      return;
    }
    Crate.Data shown = new Crate.Data();
    shown.template = packing.data().template;
    e.setResult(crate.create(shown));
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(CraftItemEvent e) {
    if (!crate.isRecipe(e.recipeKey()) || e.isCancelled()) {
      return;
    }
    PlayerRef player = e.player();
    Packing packing = packing(player, e.matrix());
    if (packing == null) {
      return;
    }
    if (!packing.fits() || !packing.missing().isEmpty() || !crafts(e)) {
      e.setCancelled(true);
      return;
    }
    List<ItemRef> taken = take(player.inventory(), packing.data().contents);
    if (taken == null) {
      e.setCancelled(true);
      return;
    }
    if (!lands(e)) {
      for (ItemRef back : taken) {
        player.inventory().addItem(back);
      }
      e.setCancelled(true);
      return;
    }
    e.setCurrentItem(crate.create(packing.data()));
  }

  /** Whether the click takes the result out of the grid: an empty cursor, a drop, a shift-click or a number key. */
  private static boolean crafts(CraftItemEvent e) {
    InventoryAction action = e.action();
    return switch (action) {
      case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> e.cursor() == null || e.cursor().isEmpty();
      case DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP -> true;
      default -> false;
    };
  }

  /** Whether the crate has somewhere to go once the blocks are taken: a free storage slot or the pressed hotbar slot. */
  private static boolean lands(CraftItemEvent e) {
    PlayerInventoryRef inventory = e.player().inventory();
    if (e.action() == InventoryAction.HOTBAR_SWAP) {
      int slot = e.hotbarButton();
      return slot >= 0 && slot < HOTBAR_SLOTS && empty(inventory.item(slot));
    }
    if (e.action() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      return true;
    }
    for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
      if (empty(inventory.item(slot))) {
        return true;
      }
    }
    return false;
  }

  private static boolean empty(@Nullable ItemRef item) {
    return item == null || item.isEmpty();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    previews.remove(e.player().uuid());
    unfolding.remove(e.player().uuid());
  }

  /** A main-hand use of the crate by a holder. */
  void use(ToolUse use, Crate.Data data) {
    PlayerRef player = use.player();
    if (data.template == null || !data.template.hasPattern()) {
      return;
    }
    if (use.leftClick()) {
      data.template.turns = Math.floorMod(data.template.turns + 1, 4);
      crate.write(use.item(), data);
      player.inventory().setMainHand(use.item());
      HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.architect.masons_template.turned").withMarkup("degrees", String.valueOf(data.template.turns * 90)).resolve());
      Preview shown = previews.get(player.uuid());
      BlockRef anchor = shown == null || !shown.isLive() ? null : shown.anchor(player.world());
      if (anchor != null) {
        preview(player, data, anchor);
      }
      return;
    }
    BlockRef block = use.block();
    if (!use.rightClick() || block == null || use.face() == null) {
      return;
    }
    BlockRef anchor = block.relative(use.face());
    Preview shown = previews.get(player.uuid());
    if (shown == null || !shown.isLive() || !shown.matches(anchor, data.template.turns)) {
      preview(player, data, anchor);
      return;
    }
    previews.remove(player.uuid());
    displays().clearViewer(getId().toString(), player.uuid());
    unfolding.put(player.uuid(), anchor);
    try {
      fire(player, null, anchor.location(), false);
    } finally {
      unfolding.remove(player.uuid());
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    BlockRef anchor = unfolding.remove(player.uuid());
    ItemRef held = player.inventory().mainHand();
    Crate.Data data = crate.read(held).orElse(null);
    if (anchor == null || data == null || data.template == null || !data.template.hasPattern()) {
      return Outcome.MISSED;
    }
    if (BuildBatches.isRunning(player)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.architect.master_builder.busy").resolve());
      return Outcome.MISSED;
    }
    List<ArchitectPattern.Placement> placements = data.template.pattern.placements(anchor, data.template.turns,
        data.template.mirror);
    CratePayment payment = new CratePayment(player, data.contents);
    BuildBatch.Builder batch = BuildBatch.of(this, player)
        .limit(blocks(use.level()))
        .pace(pace(use.level()))
        .payFrom(payment)
        .onDone((BuildResult result) -> {
          payment.returnRest(data.template);
          HudStatus.show(player, ArchitectBuilds.report(result));
        });
    for (ArchitectPattern.Placement placement : placements) {
      Id unit = placement.state().placementItem();
      if (unit != null && data.contents.getOrDefault(unit.toString(), 0) > 0 && open(placement.block())) {
        batch.place(placement.block(), placement.state());
      }
    }
    BuildBatch built = batch.build();
    if (built.steps().isEmpty()) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.master_builder.nothing").resolve());
      return Outcome.MISSED;
    }
    player.inventory().setMainHand(held.copyWithAmount(held.amount() - 1));
    if (!BuildBatches.start(built)) {
      player.inventory().setMainHand(held);
      return Outcome.MISSED;
    }
    return Outcome.INSTANT;
  }

  private void preview(PlayerRef player, Crate.Data data, BlockRef anchor) {
    List<ArchitectPattern.Placement> placements = data.template.pattern.placements(anchor, data.template.turns,
        data.template.mirror);
    previews.put(player.uuid(), new Preview(anchor.world().uid(), anchor.x(), anchor.y(), anchor.z(),
        data.template.turns, System.currentTimeMillis() + (long) (getConfig().previewSeconds * 1000D)));
    ViewerDisplayPort displays = displays();
    displays.clearViewer(getId().toString(), player.uuid());
    int ticks = (int) Math.max(20L, Math.round(getConfig().previewSeconds * 20D));
    int shown = 0;
    for (ArchitectPattern.Placement placement : placements) {
      if (shown >= Skyhook.MAX_GHOSTS) {
        break;
      }
      if (open(placement.block())) {
        displays.showBlock(getId().toString(), "ghost:" + shown, player, placement.block().location(), placement.state(), GHOST,
            ticks);
        shown++;
      }
    }
    HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.architect.masons_template.ready").withMarkup("count", String.valueOf(placements.size())).resolve());
  }

  /** What the template in {@code matrix} needs packed, or null when the grid holds no template with a copy. */
  private @Nullable Packing packing(PlayerRef player, List<ItemRef> matrix) {
    MasonsTemplate template = MasonsTemplate.find();
    if (template == null) {
      return null;
    }
    MasonsTemplate.Data copy = null;
    for (ItemRef item : matrix) {
      MasonsTemplate.Data read = template.read(item).orElse(null);
      if (read != null && read.hasPattern()) {
        copy = read;
      }
    }
    if (copy == null) {
      return null;
    }
    int level = getActiveLevel(player);
    boolean fits = level > 0 && copy.pattern.count() <= blocks(level);
    WorldRef world = player.world();
    BlockRef origin = world.blockAt(0, world.minHeight(), 0);
    Map<String, Integer> needed = new LinkedHashMap<>();
    for (ArchitectPattern.Placement placement : copy.pattern.placements(origin, 0, false)) {
      Id unit = placement.state().placementItem();
      if (unit != null) {
        needed.merge(unit.toString(), 1, Integer::sum);
      }
    }
    Map<Id, Integer> carriedByType = CuttingOnTheFly.tally(player.inventory());
    Map<String, Integer> missing = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> need : needed.entrySet()) {
      int carried = carriedByType.getOrDefault(Id.parse(need.getKey()), 0);
      if (carried < need.getValue()) {
        missing.put(need.getKey(), need.getValue() - carried);
      }
    }
    copy.replace = false;
    copy.corner = null;
    copy.cornerWorld = null;
    Crate.Data data = new Crate.Data();
    data.template = copy;
    data.contents = needed;
    return new Packing(data, missing, fits);
  }

  /** Takes every packed block from the inventory and returns what it took, or takes nothing and returns null. */
  private static @Nullable List<ItemRef> take(PlayerInventoryRef inventory, Map<String, Integer> contents) {
    List<ItemRef> taken = new ArrayList<>(contents.size());
    for (Map.Entry<String, Integer> entry : contents.entrySet()) {
      ItemRef unit = unit(entry.getKey());
      int removed = inventory.removeSimilar(unit, entry.getValue());
      if (removed > 0) {
        taken.add(unit.copyWithAmount(removed));
      }
      if (removed < entry.getValue()) {
        for (ItemRef back : taken) {
          inventory.addItem(back);
        }
        return null;
      }
    }
    return taken;
  }

  private static String shortList(Map<String, Integer> missing) {
    List<String> parts = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : missing.entrySet()) {
      if (parts.size() == Skyhook.MAX_SHORT_LISTED) {
        parts.add("...");
        break;
      }
      parts.add(entry.getValue() + " " + Id.parse(entry.getKey()).path().replace('_', ' '));
    }
    return String.join(", ", parts);
  }

  private static boolean open(BlockRef block) {
    WorldRef world = block.world();
    return block.y() >= world.minHeight() && block.y() < world.maxHeight()
        && world.isChunkLoaded(block.x() >> 4, block.z() >> 4) && (block.isAir() || block.isReplaceable());
  }

  int blocks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().blocksAtFirstLevel,
        getConfig().blocksAtTopLevel));
  }

  int pace(int level) {
    return Math.max(1, (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().blocksPerTickAtFirstLevel,
        getConfig().blocksPerTickAtTopLevel)));
  }

  private static ItemRef unit(String id) {
    return items().create(Id.parse(id), 1);
  }

  private static ItemOps items() {
    return AdaptApi.platform().host().items();
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }

  private record Packing(Crate.Data data, Map<String, Integer> missing, boolean fits) {
  }

  private record Preview(UUID world, int x, int y, int z, int turns, long until) {
    boolean isLive() {
      return System.currentTimeMillis() <= until;
    }

    @Nullable BlockRef anchor(WorldRef current) {
      return current.uid().equals(world) ? current.blockAt(x, y, z) : null;
    }

    boolean matches(BlockRef spot, int currentTurns) {
      return world.equals(spot.world().uid()) && x == spot.x() && y == spot.y() && z == spot.z() && turns == currentTurns;
    }
  }

  /** Pays only from what the crate holds; what is left goes back to the player with the template. */
  private static final class CratePayment implements BuildPayment {
    private final PlayerRef player;
    private final Map<String, Integer> contents;

    private CratePayment(PlayerRef player, Map<String, Integer> contents) {
      this.player = player;
      this.contents = new LinkedHashMap<>(contents);
    }

    @Override
    public synchronized @Nullable ItemRef take(ItemRef unit) {
      String key = unit.type().toString();
      int left = contents.getOrDefault(key, 0);
      if (left <= 0) {
        return null;
      }
      contents.put(key, left - 1);
      return items().create(unit.type(), 1);
    }

    @Override
    public synchronized void giveBack(ItemRef taken) {
      contents.merge(taken.type().toString(), taken.amount(), Integer::sum);
    }

    private synchronized void returnRest(MasonsTemplate.Data template) {
      List<ItemRef> rest = new ArrayList<>();
      for (Map.Entry<String, Integer> entry : contents.entrySet()) {
        Id type = Id.parse(entry.getKey());
        int left = entry.getValue();
        int stack = Math.max(1, items().maxStackSize(type));
        while (left > 0) {
          int amount = Math.min(stack, left);
          rest.add(items().create(type, amount));
          left -= amount;
        }
      }
      contents.clear();
      MasonsTemplate tool = MasonsTemplate.find();
      if (tool != null) {
        rest.add(tool.create(template));
      }
      BuildPayment.give(player, rest);
    }
  }

  /** The crate item: the template it packs and the blocks it holds for that build. */
  public static final class Crate extends MutationTool<Crate.Data> {
    private final PrefabCrate owner;

    private Crate(PrefabCrate owner) {
      super(ToolItems.builder(TOOL_ID, CHEST, Data.class, new Data()).build(), owner, 1);
      this.owner = owner;
    }

    /** The packed template and its blocks, by item id. */
    public static final class Data {
      public MasonsTemplate.Data template;
      public Map<String, Integer> contents = new LinkedHashMap<>();
    }

    @Override
    public List<RecipeSpec> recipes() {
      ItemRef paper = items().create(Id.minecraft("paper"), 1);
      Ingredient template = new Ingredient.Matching(Id.minecraft("paper"), paper, (ItemRef item) -> {
        MasonsTemplate tool = MasonsTemplate.find();
        MasonsTemplate.Data data = tool == null ? null : tool.read(item).orElse(null);
        return data != null && data.hasPattern();
      });
      return List.of(RecipeSpec.shapeless(TOOL_ID, List.of(template, Ingredient.of(CHEST)), create(new Data())));
    }

    boolean isRecipe(@Nullable Id key) {
      return key != null && key.equals(TOOL_ID);
    }

    @Override
    public void use(ToolUse use) {
      Data data = read(use.item()).orElse(null);
      if (data != null) {
        owner.use(use, data);
      }
    }

    @Override
    public void applyLore(Data data, List<String> lines) {
      if (data.template == null || !data.template.hasPattern()) {
        return;
      }
      ArchitectPattern pattern = data.template.pattern;
      lines.add(TextFormat.WHITE + Text.key("adapt.tool.architect.masons_template.pattern").withMarkup("width", String.valueOf(pattern.width())).withMarkup("height", String.valueOf(pattern.height())).withMarkup("depth", String.valueOf(pattern.depth())).withMarkup("count", String.valueOf(pattern.count())).resolve());
    }
  }

  @ConfigDescription("A Mason's Template packs with its blocks into a crate that unfolds the build where you place it.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Most blocks one crate holds at level 1.", impact = "Higher values pack larger templates.")
    int blocksAtFirstLevel = 216;
    @ConfigDoc(value = "Most blocks one crate holds at the top level.", impact = "Higher values pack larger templates.")
    int blocksAtTopLevel = 2744;
    @ConfigDoc(value = "Blocks a crate lays per tick at level 1, before the server-wide cap.", impact = "Higher values unfold faster.")
    int blocksPerTickAtFirstLevel = 4;
    @ConfigDoc(value = "Blocks a crate lays per tick at the top level, before the server-wide cap.", impact = "Higher values unfold faster.")
    int blocksPerTickAtTopLevel = 16;
    @ConfigDoc(value = "Seconds a crate preview stays up waiting for the second right-click.", impact = "Higher values give more time to confirm.")
    double previewSeconds = 30;
    @ConfigDoc(value = "Cooldown after a crate unfolds, in milliseconds.", impact = "Higher values space out unfolding.")
    long cooldownMillis = 0;
  }
}
