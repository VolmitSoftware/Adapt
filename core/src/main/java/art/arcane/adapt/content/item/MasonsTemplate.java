package art.arcane.adapt.content.item;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Skyhook's Mason's Template: copies a small build as block states, then stamps it elsewhere, turned and mirrored, or
 * repeats it along a chalk guide. Set to the replace brush it arms Wrecking Hand's replace from the hotbar.
 */
public final class MasonsTemplate extends MutationTool<MasonsTemplate.Data> {
  public static final String ID = "tool-architect-masons-template";
  private static final int HOTBAR_SLOTS = 9;
  private final Skyhook owner;

  public MasonsTemplate(Skyhook owner) {
    super("tool-architect-masons-template", owner, Id.minecraft("paper"), 1, Data.class, ArchitectMessages.TEMPLATE_NAME);
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
    return MutationTools.find(ID) instanceof MasonsTemplate template ? template : null;
  }

  @Override
  public List<RecipeSpec> recipes() {
    ItemRef wand = ChalkWandItem.create(ChalkWandItem.Tool.STRAIGHTEDGE);
    Ingredient chalk = new Ingredient.Matching(Id.minecraft("stick"), wand,
        (ItemRef item) -> ChalkWandItem.read(item) != null);
    Ingredient template = new Ingredient.Matching(material(), create(new Data()), this::is);
    return List.of(
        new RecipeSpec.Shapeless(Id.adapt(ID), List.of(Ingredient.of(Id.minecraft("paper")), chalk,
            new Ingredient.AnyOf(Set.of(Id.minecraft("copper_ingot"), Id.minecraft("iron_ingot")))), create(new Data()), 1),
        new RecipeSpec.Shapeless(Id.adapt(ID + "-wipe"), List.of(template), create(new Data()), 1));
  }

  @Override
  protected void use(ToolUse use) {
    Data data = read(use.item());
    owner.use(use, data == null ? new Data() : data);
  }

  @Override
  protected void applyLore(Data data, List<String> lines) {
    if (!data.hasPattern()) {
      lines.add(C.GRAY + AdaptLanguage.text(ArchitectMessages.TEMPLATE_USAGE_COPY));
      return;
    }
    ArchitectPattern pattern = data.pattern;
    lines.add(C.WHITE + AdaptLanguage.text(ArchitectMessages.TEMPLATE_PATTERN,
        trusted("width", String.valueOf(pattern.width())), trusted("height", String.valueOf(pattern.height())),
        trusted("depth", String.valueOf(pattern.depth())), trusted("count", String.valueOf(pattern.count()))));
    lines.add(C.AQUA + AdaptLanguage.text(data.replace ? ArchitectMessages.TEMPLATE_MODE_REPLACE : ArchitectMessages.TEMPLATE_MODE_STAMP));
    for (TextKey usage : List.of(ArchitectMessages.TEMPLATE_USAGE_STAMP, ArchitectMessages.TEMPLATE_USAGE_TURN,
        ArchitectMessages.TEMPLATE_USAGE_REPEAT, ArchitectMessages.TEMPLATE_USAGE_UNDO, ArchitectMessages.TEMPLATE_USAGE_MODE,
        ArchitectMessages.TEMPLATE_USAGE_WIPE)) {
      lines.add(C.DARK_GRAY + AdaptLanguage.text(usage));
    }
  }

  /** Whether a template set to the replace brush sits in the hotbar of a player who has Skyhook. */
  public boolean armsReplace(PlayerRef player) {
    if (owner.getActiveLevel(player) <= 0) {
      return false;
    }
    for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
      Data data = read(player.inventory().item(slot));
      if (data != null && data.replace) {
        return true;
      }
    }
    return false;
  }
}
