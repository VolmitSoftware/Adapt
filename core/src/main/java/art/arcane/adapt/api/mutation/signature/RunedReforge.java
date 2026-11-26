package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Enchanting and Crafting, either leading, replacing Convergence: a Runed Book in the main hand presses the Masterwork
 * bonus of the gear in the off hand into itself, and a full book sets its bonus on gear that has none. Book and gear
 * change in the same tick, so a bonus is never in two places.
 */
public final class RunedReforge extends Signature<RunedReforge.Config> {
  private final RunedBook book;
  private final Map<UUID, ToolUse> using = new ConcurrentHashMap<>();

  public RunedReforge(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chipped_anvil"));
    book = registerTool(new RunedBook(this, "tool-signature-" + row.leadId() + "-" + row.partnerId() + "-runed-book"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.pc(kept(level), 0), 1);
    statLore(v, getConfig().edgeLevel, 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
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
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    ToolUse tool = using.get(player.uuid());
    CraftingMasterwork masterwork = masterwork();
    if (tool == null || masterwork == null) {
      return Outcome.MISSED;
    }
    PlayerInventoryRef inventory = player.inventory();
    ItemRef held = inventory.mainHand();
    ItemRef gear = inventory.offHand();
    RunedBook.Data data = book.read(held);
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
    inventory.setMainHand(nextBook == null ? AdaptCore.platform().host().items().empty() : nextBook);
    fx(player.location().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.ENCHANTMENT_TABLE, 16, 0.4D)
        .burst(Particles.CRIT_MAGIC, 6, 0.3D);
    return Outcome.INSTANT;
  }

  private @Nullable CraftingMasterwork masterwork() {
    Skill<?> crafting = getServer().getSkillRegistry().getSkill("crafting");
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

    private RunedBook(RunedReforge owner, String id) {
      super(id, owner, Id.minecraft("enchanted_book"), 1, Data.class, CraftingMessages.RUNED_BOOK_NAME);
      this.owner = owner;
    }

    /** The bonus a book holds; {@code share} is null while it is blank. */
    public static final class Data {
      Double share;
      boolean edge;
    }

    @Override
    public List<RecipeSpec> recipes() {
      return List.of(new RecipeSpec.Shapeless(Id.adapt(id()), List.of(Ingredient.of(Id.minecraft("book")),
          Ingredient.of(Id.minecraft("lapis_lazuli")), Ingredient.of(Id.minecraft("amethyst_shard"))),
          create(new Data()), 1));
    }

    @Override
    protected void use(ToolUse use) {
      owner.used(use);
    }

    @Override
    protected void applyLore(Data data, List<String> lines) {
      if (data.share == null) {
        return;
      }
      lines.add(C.AQUA + AdaptLanguage.text(CraftingMessages.MASTERWORK_BONUS,
          trusted("bonus", C.GRAY + Form.pc(data.share, 0))));
      if (data.edge) {
        lines.add(C.LIGHT_PURPLE + AdaptLanguage.text(CraftingMessages.MASTERWORK_ATTRIBUTE_TAG));
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
