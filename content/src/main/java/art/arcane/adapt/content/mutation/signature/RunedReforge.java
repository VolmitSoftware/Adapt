package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Enchanting and Crafting, either leading, replacing Convergence: a Runed Book in the main hand presses the Masterwork
 * bonus of the gear in the off hand into itself, and a full book sets its bonus on gear that has none. Book and gear
 * change in the same tick, so a bonus is never in two places.
 */
public final class RunedReforge extends Signature<RunedReforge.Config> {
  private final RunedBook book;
  private final Map<UUID, ToolUse> using = new ConcurrentHashMap<>();

  public RunedReforge(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chipped_anvil"));
    book = registerTool(new RunedBook(this, getSlot().pair().child("tool/runed_book")));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.pc(kept(level), 0), 1);
    statLore(v, getConfig().edgeLevel, 2);
  }

  @Override
  public long cooldownMillis(int level) {
    return 0L;
  }

  /** The Runed Book this signature hands out. */
  public RunedBook book() {
    return book;
  }

  private void used(ToolUse use) {
    PlayerRef player = use.player();
    if (!use.rightClick() || player.openMenuInventory() != null || using.putIfAbsent(player.uuid(), use) != null) {
      return;
    }
    try {
      fire(player, null, null, false);
    } finally {
      using.remove(player.uuid());
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    ToolUse tool = using.get(player.uuid());
    CraftingMasterwork masterwork = masterwork();
    if (tool == null || masterwork == null) {
      return Outcome.MISSED;
    }
    PlayerInventoryRef inventory = player.inventory();
    ItemRef held = inventory.mainHand();
    ItemRef gear = inventory.offHand();
    RunedBook.Data data = book.read(held).orElse(null);
    if (!book.is(held) || data == null || held.amount() != 1 || gear == null || gear.isEmpty() || gear.amount() != 1) {
      return Outcome.MISSED;
    }
    ItemRef nextGear;
    ItemRef nextBook;
    if (data.share == null) {
      CraftingMasterwork.Bonus bonus = CraftingMasterwork.bonus(gear);
      nextGear = bonus == null ? null : CraftingMasterwork.strip(gear);
      if (nextGear == null) {
        return Outcome.MISSED;
      }
      RunedBook.Data pressed = new RunedBook.Data();
      pressed.share = bonus.durabilityShare() * kept(use.level());
      pressed.edge = bonus.edge() && use.level() >= getConfig().edgeLevel;
      nextBook = book.create(pressed);
    } else {
      nextGear = masterwork.apply(gear, new CraftingMasterwork.Bonus(data.share, data.edge));
      if (nextGear == null) {
        return Outcome.MISSED;
      }
      nextBook = null;
    }
    inventory.setOffHand(nextGear);
    inventory.setMainHand(nextBook == null ? AdaptApi.platform().host().items().empty() : nextBook);
    fx(player.location().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.ENCHANT, 16, 0.4D)
        .burst(Particles.CRIT, 6, 0.3D);
    return Outcome.INSTANT;
  }

  private @Nullable CraftingMasterwork masterwork() {
    Skill<?> crafting = AdaptApi.content().skill(Id.adapt("crafting")).orElse(null);
    if (crafting == null) {
      return null;
    }
    for (Adaptation<?> adaptation : crafting.getAdaptations()) {
      if (adaptation instanceof CraftingMasterwork masterwork) {
        return masterwork;
      }
    }
    return null;
  }

  double kept(int level) {
    return Math.min(1D, Math.max(0D, SignatureKit.scaled(level, getMaxLevel(), getConfig().keptAtFirstLevel,
        getConfig().keptAtTopLevel)));
  }

  /** A Runed Book: blank, or holding one pressed Masterwork bonus. */
  public static final class RunedBook extends MutationTool<RunedBook.Data> {
    private final RunedReforge owner;

    private RunedBook(RunedReforge owner, Id id) {
      super(ToolItems.builder(id, Id.minecraft("enchanted_book"), Data.class, new Data()).build(), owner, 1);
      this.owner = owner;
    }

    /** The bonus a book holds; {@code share} is null while it is blank. */
    public static final class Data {
      Double share;
      boolean edge;
    }

    @Override
    public List<RecipeSpec> recipes() {
      return List.of(RecipeSpec.shapeless(item().id(), List.of(Ingredient.of(Id.minecraft("book")),
          Ingredient.of(Id.minecraft("lapis_lazuli")), Ingredient.of(Id.minecraft("amethyst_shard"))), create(new Data())).withRequiredLevel(1));
    }

    @Override
    public void use(ToolUse use) {
      owner.used(use);
    }

    @Override
    public void applyLore(Data data, List<String> lines) {
      if (data.share == null) {
        return;
      }
      lines.add(TextFormat.AQUA + Text.key("adapt.crafting.masterwork.bonus").withMarkup("bonus", TextFormat.GRAY + Format.pc(data.share, 0)).resolve());
      if (data.edge) {
        lines.add(TextFormat.LIGHT_PURPLE + Text.key("adapt.crafting.masterwork.attribute_tag").resolve());
      }
    }
  }

  @ConfigDescription("A Runed Book moves a Masterwork bonus from one piece of gear to another.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Share of the bonus durability a book keeps at level 1.", impact = "Higher values lose less of the bonus.")
    double keptAtFirstLevel = 0.7D;
    @ConfigDoc(value = "Share of the bonus durability a book keeps at the top level.", impact = "Higher values lose less of the bonus.")
    double keptAtTopLevel = 1.0D;
    @ConfigDoc(value = "Level from which the Masterwork Edge moves with the bonus.", impact = "Lower values move the Edge sooner.")
    int edgeLevel = 2;
  }
}
