package art.arcane.adapt.api.item;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/** The server's recipe index as Adapt reads it for material values: which recipes make a material, from what. */
@ApiStatus.Experimental
public final class MaterialValues {
  private MaterialValues() {
  }

  /**
   * The recipes whose output is {@code material}; empty when none or while the index is not built. The same list
   * instance comes back until the index changes (after a reload), so callers may cache by identity. Recipes with a
   * missing input or output are left out.
   */
  public static List<Recipe> recipes(Id material) {
    return Engine.bridge().items().materialRecipes(Objects.requireNonNull(material, "material"));
  }

  /**
   * Where a recipe is made.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Kind {
    /** A crafting table recipe. */
    CRAFTING,
    /** A stonecutter recipe. */
    STONECUTTING,
    /** Any other recipe: smelting, smithing and the rest. */
    OTHER
  }

  /** An amount of one material. */
  public record Count(Id material, int amount) {
    /** Checks the material is set. */
    public Count {
      Objects.requireNonNull(material, "material");
    }
  }

  /** One recipe: what it takes and what it makes. */
  public record Recipe(Kind kind, List<Count> inputs, Count output) {
    /** Copies the inputs. */
    public Recipe {
      Objects.requireNonNull(kind, "kind");
      inputs = List.copyOf(inputs);
      Objects.requireNonNull(output, "output");
    }
  }
}
