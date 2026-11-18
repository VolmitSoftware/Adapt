package art.arcane.adapt.content.integration.hiddenore;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;

/** Hidden veins from the HiddenOre plugin (Bukkit only). Empty answers when the plugin is absent. */
public interface HiddenOrePort {
  HiddenOrePort NONE = new HiddenOrePort() {
  };

  /** A hidden vein block and the ore it reveals as. */
  record VeinTarget(Loc location, Id display) {
  }

  default List<VeinTarget> veins(Loc origin, int radius) {
    return List.of();
  }

  /** Blocks of the hidden vein {@code block} belongs to, excluding {@code block}. */
  default List<BlockRef> veinSiblings(BlockRef block) {
    return List.of();
  }
}
