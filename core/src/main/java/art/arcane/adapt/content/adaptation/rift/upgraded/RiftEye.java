package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** An eye bound to the anchor network: right-click anywhere to pick an anchor and travel there. */
public class RiftEye extends MutationTool<MutationTool.NoData> {
  private final PocketRealm realm;

  public RiftEye(PocketRealm realm) {
    super("tool-rift-eye", realm, Id.minecraft("ender_eye"), 1, NoData.class,
        RiftMessages.EYE_NAME, RiftMessages.EYE_USAGE);
    this.realm = realm;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shapeless(Id.adapt("tool-rift-eye"),
        List.of(Ingredient.of(Id.minecraft("ender_eye")), Ingredient.of(Id.minecraft("crying_obsidian")),
            Ingredient.of(Id.minecraft("amethyst_shard"))),
        create(new NoData())));
  }

  @Override
  protected void use(ToolUse use) {
    if (use.rightClick()) {
      realm.openFromEye(use.player(), use.block(), use.level());
    }
  }
}
