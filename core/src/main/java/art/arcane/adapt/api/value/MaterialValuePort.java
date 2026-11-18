package art.arcane.adapt.api.value;

import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Recipe and block facts {@link MaterialValue} reads from the server. */
public interface MaterialValuePort {
  MaterialValuePort NONE = new MaterialValuePort() {
    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
      return List.of();
    }

    @Override
    public boolean breaksInstantly(Id material) {
      return false;
    }
  };

  /** Every server recipe producing this material, excluding Adapt's own recipes. */
  List<MaterialRecipe> recipesFor(Id material);

  /** True for blocks with zero hardness. */
  boolean breaksInstantly(Id material);
}
