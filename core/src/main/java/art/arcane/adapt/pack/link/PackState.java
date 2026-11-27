package art.arcane.adapt.pack.link;

/** Where one player is with one offered pack. Every value but {@link #OFFERED} is reported by the client. */
public enum PackState {
  OFFERED,
  ACCEPTED,
  DOWNLOADED,
  LOADED,
  DECLINED,
  /** The download failed: the address could not be reached, answered no file, or the file's SHA-1 did not match. */
  DOWNLOAD_FAILED,
  FAILED,
  DISCARDED;

  /** True once the client reported its final answer for this offer. */
  public boolean settled() {
    return this == LOADED || this == DECLINED || this == DOWNLOAD_FAILED || this == FAILED || this == DISCARDED;
  }
}
