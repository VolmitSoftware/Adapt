package art.arcane.adapt.content.item;

import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Skyhook's Mason's Template: copies a small build as block states, then stamps it elsewhere, turned and mirrored, or
 * repeats it along a chalk guide. Set to the replace brush it arms Wrecking Hand's replace from the hotbar.
 */
public final class MasonsTemplate extends MutationTool<MasonsTemplate.Data> {
  public static final Id ID = Id.adapt("architect/tool/masons_template");
  private static final int HOTBAR_SLOTS = 9;
  private final Skyhook owner;

  public MasonsTemplate(Skyhook owner) {
    super(ToolItems.builder(ID, Id.minecraft("paper"), Data.class, new Data()).build(), owner, 1);
    this.owner = owner;
  }

  /** What a template holds: its mode, a first corner while copying, and the copy with its turn and mirror. */
  public static final class Data {
    public boolean replace;
    public String cornerWorld;
    public int[] corner;
    public ArchitectPattern pattern;
    public int turns;
    public boolean mirror;

    public boolean hasPattern() {
      return pattern != null && pattern.isValid();
    }
  }

  /** The registered template tool, or null. */
  public static @Nullable MasonsTemplate find() {
    return Mutations.tool(ID).orElse(null) instanceof MasonsTemplate template ? template : null;
  }

  @Override
  public List<RecipeSpec> recipes() {
    ItemRef wand = ChalkWandItem.create(ChalkWandItem.Tool.STRAIGHTEDGE);
    Ingredient chalk = new Ingredient.Matching(Id.minecraft("stick"), wand,
        (ItemRef item) -> ChalkWandItem.read(item) != null);
    Ingredient template = new Ingredient.Matching(item().baseItem(), create(new Data()), this::is);
    return List.of(
        RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("paper")), chalk,
            new Ingredient.AnyOf(Set.of(Id.minecraft("copper_ingot"), Id.minecraft("iron_ingot")))), create(new Data())).withRequiredLevel(1),
        RecipeSpec.shapeless(ID.withPath(ID.path() + "/wipe"), List.of(template), create(new Data())).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
    Data data = read(use.item()).orElse(null);
    owner.use(use, data == null ? new Data() : data);
  }

  @Override
  public void applyLore(Data data, List<String> lines) {
    if (!data.hasPattern()) {
      lines.add(TextFormat.GRAY + Text.key("adapt.tool.architect.masons_template.usage_copy").resolve());
      return;
    }
    ArchitectPattern pattern = data.pattern;
    lines.add(TextFormat.WHITE + Text.key("adapt.tool.architect.masons_template.pattern").withMarkup("width", String.valueOf(pattern.width())).withMarkup("height", String.valueOf(pattern.height())).withMarkup("depth", String.valueOf(pattern.depth())).withMarkup("count", String.valueOf(pattern.count())).resolve());
    lines.add(TextFormat.AQUA + (data.replace ? Text.key("adapt.tool.architect.masons_template.mode_replace") : Text.key("adapt.tool.architect.masons_template.mode_stamp")).resolve());
    for (String usage : List.of("usage_stamp", "usage_turn", "usage_repeat", "usage_undo", "usage_mode", "usage_wipe")) {
      lines.add(TextFormat.DARK_GRAY + Text.key("adapt.tool.architect.masons_template." + usage).resolve());
    }
  }

  /** Whether a template set to the replace brush sits in the hotbar of a player who has Skyhook. */
  public boolean armsReplace(PlayerRef player) {
    if (owner.getActiveLevel(player) <= 0) {
      return false;
    }
    for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
      Data data = read(player.inventory().item(slot)).orElse(null);
      if (data != null && data.replace) {
        return true;
      }
    }
    return false;
  }
}
