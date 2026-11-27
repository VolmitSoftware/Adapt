package art.arcane.adapt.client.protocol;

import java.util.Objects;

/** One file of a {@link ServerFrame.AssetManifest}: its resource id and its SHA-1 hash in lower-case hex. */
public record AssetRef(String resource, String hash) {
  public AssetRef {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(hash, "hash");
  }
}
