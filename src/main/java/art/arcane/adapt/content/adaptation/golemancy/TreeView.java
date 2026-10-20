package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

interface TreeView {
  Material type(int x, int y, int z);

  boolean owned(int x, int y, int z);

  boolean placed(int x, int y, int z);

  boolean decaying(int x, int y, int z);
}
