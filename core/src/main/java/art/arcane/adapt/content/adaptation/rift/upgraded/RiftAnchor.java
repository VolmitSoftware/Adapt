package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

/** A Rift Anchor waiting to be placed: right-click a block to set it beside that block. */
public class RiftAnchor extends MutationTool<MutationTool.NoData> {
  private final PocketRealm realm;

  public RiftAnchor(PocketRealm realm) {
    super("tool-rift-anchor", realm, Id.minecraft("lodestone"), 1, NoData.class,
        RiftMessages.ANCHOR_NAME, RiftMessages.ANCHOR_USAGE);
    this.realm = realm;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shaped(Id.adapt("tool-rift-anchor"), List.of(" C ", "PLP", " C "),
        Map.of('C', Ingredient.of(Id.minecraft("crying_obsidian")), 'P', Ingredient.of(Id.minecraft("ender_pearl")),
            'L', Ingredient.of(Id.minecraft("lodestone"))),
        create(new NoData())));
  }

  @Override
  protected void use(ToolUse use) {
    if (use.action() == InteractAction.RIGHT_CLICK_BLOCK
        && use.block() != null && use.face() != null) {
      realm.placeAnchor(use.player(), use.block().relative(use.face()), use.item(), use.level());
    }
  }
}
