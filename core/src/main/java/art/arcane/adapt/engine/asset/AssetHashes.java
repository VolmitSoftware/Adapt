package art.arcane.adapt.engine.asset;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The SHA-1 hashes the catalog and the asset sync name files by, as lower-case hex. */
public final class AssetHashes {
  public static final int BYTES = 20;

  private AssetHashes() {
  }

  /** The hash of {@code parts} read one after the other. */
  public static String sha1(byte[]... parts) {
    MessageDigest digest = digest();
    for (byte[] part : parts) {
      digest.update(part);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-1 is not available", missing);
    }
  }
}
