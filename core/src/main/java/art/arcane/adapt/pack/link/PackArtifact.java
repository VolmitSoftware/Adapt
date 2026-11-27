package art.arcane.adapt.pack.link;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * One built resource pack zip. Immutable: the bytes are copied in and never handed out, so the SHA-1 and the pack id
 * derived from it always describe what is served. {@code minFormat} and {@code maxFormat} are the resource pack format
 * range the pack declares.
 */
public final class PackArtifact {
  private final byte[] zip;
  private final byte[] sha1;
  private final String sha1Hex;
  private final UUID id;
  private final int minFormat;
  private final int maxFormat;

  private PackArtifact(byte[] zip, int minFormat, int maxFormat) {
    if (minFormat > maxFormat) {
      throw new IllegalArgumentException("minFormat " + minFormat + " is above maxFormat " + maxFormat);
    }
    this.zip = zip;
    this.sha1 = digest(zip);
    this.sha1Hex = HexFormat.of().formatHex(sha1);
    this.id = UUID.nameUUIDFromBytes(("adapt:pack:" + sha1Hex).getBytes(StandardCharsets.UTF_8));
    this.minFormat = minFormat;
    this.maxFormat = maxFormat;
  }

  public static PackArtifact of(byte[] zip, int minFormat, int maxFormat) {
    return new PackArtifact(Objects.requireNonNull(zip, "zip").clone(), minFormat, maxFormat);
  }

  public static PackArtifact read(Path zip, int minFormat, int maxFormat) throws IOException {
    return new PackArtifact(Files.readAllBytes(zip), minFormat, maxFormat);
  }

  /** The id clients hold the pack under; the same hash always gives the same id. */
  public UUID id() {
    return id;
  }

  public String sha1Hex() {
    return sha1Hex;
  }

  public byte[] sha1() {
    return sha1.clone();
  }

  public int size() {
    return zip.length;
  }

  public int minFormat() {
    return minFormat;
  }

  public int maxFormat() {
    return maxFormat;
  }

  public InputStream open() {
    return new ByteArrayInputStream(zip);
  }

  public void writeTo(OutputStream out) throws IOException {
    out.write(zip);
  }

  public void writeTo(Path file) throws IOException {
    Files.write(file, zip);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PackArtifact artifact && artifact.sha1Hex.equals(sha1Hex)
        && artifact.minFormat == minFormat && artifact.maxFormat == maxFormat;
  }

  @Override
  public int hashCode() {
    return sha1Hex.hashCode();
  }

  @Override
  public String toString() {
    return "PackArtifact[" + sha1Hex + ", " + zip.length + " bytes, formats " + minFormat + "-" + maxFormat + "]";
  }

  private static byte[] digest(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-1").digest(bytes);
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-1 is unavailable", missing);
    }
  }
}
