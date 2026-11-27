package art.arcane.adapt.engine.asset;

/** Told after every change of an {@link AssetCatalog}, on the thread that made it; the new revision is {@link AssetCatalog#revision()}. */
@FunctionalInterface
public interface AssetListener {
  void changed(AssetCatalog catalog);
}
