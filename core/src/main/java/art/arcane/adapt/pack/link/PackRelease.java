package art.arcane.adapt.pack.link;

import java.util.Objects;

/**
 * The released Adapt pack: where players download it ({@code pack.releaseUrl}) and its SHA-1. Players are sent there
 * while the server's own pack is byte for byte that one and no player has failed to download it before anyone loaded
 * it from there; the first such failure moves delivery to the server's own host for good. Thread-safe.
 */
public final class PackRelease {
  private final String url;
  private final String sha1Hex;
  private boolean failed;
  private boolean loaded;

  public PackRelease(String url, String sha1Hex) {
    this.url = Objects.requireNonNull(url, "url");
    this.sha1Hex = Objects.requireNonNull(sha1Hex, "sha1Hex");
  }

  public String url() {
    return url;
  }

  public String sha1Hex() {
    return sha1Hex;
  }

  /** True when players should download {@code artifact} from the release. */
  public synchronized boolean serves(PackArtifact artifact) {
    return !failed && artifact != null && sha1Hex.equals(artifact.sha1Hex());
  }

  /**
   * Takes a client report on {@code artifact}. Returns true when it ends release delivery: a failed download while
   * players get the release and none has loaded it from there yet. A load proves the release reachable, so later
   * failures are the player's own.
   */
  public synchronized boolean report(PackArtifact artifact, PackState state) {
    if (!serves(artifact)) {
      return false;
    }
    if (state == PackState.LOADED) {
      loaded = true;
      return false;
    }
    if (state == PackState.DOWNLOAD_FAILED && !loaded) {
      failed = true;
      return true;
    }
    return false;
  }
}
