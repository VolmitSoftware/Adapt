package art.arcane.adapt.engine.asset;

/** A listener's place in an {@link AssetCatalog}; cancelling stops the calls. Cancelling twice does nothing. */
public interface AssetSubscription {
  void cancel();
}
