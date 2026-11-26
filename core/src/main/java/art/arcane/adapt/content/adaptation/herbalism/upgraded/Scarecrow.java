package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismScarecrows;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;

import java.util.List;
import java.util.Map;

/** Harvest Moon's Scarecrow: right-click a block to stand it there. It remembers how many harvests it has done. */
public final class Scarecrow extends MutationTool<Scarecrow.Data> {
  private final HarvestMoon moon;

  Scarecrow(HarvestMoon moon) {
    super("tool-herbalism-scarecrow", moon, HerbalismScarecrows.HAY, 1, Data.class, HerbalismMessages.SCARECROW_NAME);
    this.moon = moon;
  }

  /** Harvests done by this scarecrow so far. */
  public static final class Data {
    int harvests;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shaped(Id.adapt("tool-herbalism-scarecrow"), List.of(" P ", "SHS", " T "),
        Map.of('P', Ingredient.of(Id.minecraft("carved_pumpkin")), 'S', Ingredient.of(Id.minecraft("stick")),
            'H', Ingredient.of(HerbalismScarecrows.HAY), 'T', Ingredient.of(Id.minecraft("leather_chestplate"))),
        create(new Data()), 1));
  }

  @Override
  protected void use(ToolUse use) {
    BlockRef clicked = use.block();
    if (!use.rightClick() || clicked == null || use.face() == null) {
      return;
    }
    Data data = read(use.item());
    moon.scarecrows().place(use.player(), use.level(), clicked.relative(use.face()), use.item().copyWithAmount(1),
        data == null ? 0 : data.harvests);
  }

  @Override
  protected void applyLore(Data data, List<String> lines) {
    int limit = moon.getConfig().scarecrowWearLimit;
    lines.add(C.GRAY + AdaptLanguage.text(HerbalismMessages.SCARECROW_HARVESTS) + " " + C.WHITE
        + Math.max(0, limit - data.harvests) + "/" + limit);
  }

  /** A scarecrow item that has done {@code harvests} harvests. */
  ItemRef worn(int harvests) {
    Data data = new Data();
    data.harvests = Math.max(0, harvests);
    return create(data);
  }
}
