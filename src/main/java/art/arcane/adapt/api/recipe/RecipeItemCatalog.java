package art.arcane.adapt.api.recipe;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

public interface RecipeItemCatalog {
  Material itemMaterial(String name);

  Tag<Material> itemTag(NamespacedKey key);
}
