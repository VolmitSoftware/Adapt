package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismScarecrows;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;

/** Harvest Moon's Scarecrow: right-click a block to stand it there. It remembers how many harvests it has done. */
public final class Scarecrow extends MutationTool<Scarecrow.Data> {
  private final HarvestMoon moon;

  Scarecrow(HarvestMoon moon) {
    super(ToolItems.builder(Id.adapt("herbalism/tool/scarecrow"), HerbalismScarecrows.HAY, Data.class, new Data()).build(), moon, 1);
    this.moon = moon;
  }

  /** Harvests done by this scarecrow so far. */
  public static final class Data {
    int harvests;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shaped(Id.adapt("herbalism/tool/scarecrow"), List.of(" P ", "SHS", " T "), Map.of('P', Ingredient.of(Id.minecraft("carved_pumpkin")), 'S', Ingredient.of(Id.minecraft("stick")),
            'H', Ingredient.of(HerbalismScarecrows.HAY), 'T', Ingredient.of(Id.minecraft("leather_chestplate"))), create(new Data())).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
    BlockRef clicked = use.block();
    if (!use.rightClick() || clicked == null || use.face() == null) {
      return;
    }
    Data data = read(use.item()).orElse(null);
    moon.scarecrows().place(use.player(), use.level(), clicked.relative(use.face()), use.item().copyWithAmount(1),
        data == null ? 0 : data.harvests);
  }

  @Override
  public void applyLore(Data data, List<String> lines) {
    int limit = moon.getConfig().scarecrowWearLimit;
    lines.add(TextFormat.GRAY + Text.key("adapt.tool.herbalism.scarecrow.harvests").resolve() + " " + TextFormat.WHITE
        + Math.max(0, limit - data.harvests) + "/" + limit);
  }

  /** A scarecrow item that has done {@code harvests} harvests. */
  ItemRef worn(int harvests) {
    Data data = new Data();
    data.harvests = Math.max(0, harvests);
    return create(data);
  }
}
