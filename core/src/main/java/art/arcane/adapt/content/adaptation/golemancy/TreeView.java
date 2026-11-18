package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.value.Id;

interface TreeView {
  Id type(int x, int y, int z);

  boolean owned(int x, int y, int z);

  boolean placed(int x, int y, int z);

  boolean decaying(int x, int y, int z);

  default boolean complete() {
    return true;
  }
}
