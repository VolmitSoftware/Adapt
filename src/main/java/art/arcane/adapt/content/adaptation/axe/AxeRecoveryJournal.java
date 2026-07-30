package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.Adapt;
import org.bukkit.NamespacedKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

final class AxeRecoveryJournal {
  private static final int MAGIC = 0x41584531;
  private static final int VERSION = 1;
  private static final int CHECKSUM_BYTES = 32;
  private static final int HEADER_BYTES = Integer.BYTES * 3 + CHECKSUM_BYTES;
  private static final int MAX_ENTRY_BYTES = 1_048_576;
  private static final int MAX_ENTRIES_PER_OWNER = 512;
  private static final String ENTRY_SUFFIX = ".axe";
  private static final String KEY_PREFIX = "throwing_axe_recovery_";

  private final Path root;

  AxeRecoveryJournal(Path root) {
    this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
  }

  static AxeRecoveryJournal createDefault() {
    Path marker = Adapt.instance.getDataFile(
        "data",
        "recovery",
        "throwing-axes",
        ".root"
    ).toPath();
    return new AxeRecoveryJournal(marker.getParent());
  }

  synchronized void persist(
      UUID ownerId,
      NamespacedKey recoveryKey,
      byte[] encoded
  ) throws IOException {
    byte[] payload = Objects.requireNonNull(encoded).clone();
    if (payload.length == 0 || payload.length > MAX_ENTRY_BYTES) {
      throw new IOException("Invalid thrown-axe recovery payload length: " + payload.length);
    }
    Path ownerDirectory = ownerDirectory(ownerId);
    Files.createDirectories(ownerDirectory);
    Path target = entryPath(ownerId, recoveryKey.getKey());
    if (Files.exists(target)) {
      byte[] existing = read(ownerId, recoveryKey.getKey());
      if (Arrays.equals(existing, payload)) {
        return;
      }
      throw new IOException("Conflicting thrown-axe recovery entry: " + recoveryKey.getKey());
    }
    Path temporary = Files.createTempFile(
        ownerDirectory,
        recoveryKey.getKey() + ".",
        ".tmp"
    );
    try {
      Files.write(
          temporary,
          encode(payload),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
      );
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      Files.move(
          temporary,
          target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING
      );
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  synchronized List<String> keys(UUID ownerId) throws IOException {
    Path ownerDirectory = ownerDirectory(ownerId);
    if (!Files.isDirectory(ownerDirectory)) {
      return List.of();
    }

    List<String> keys = new ArrayList<>();
    try (Stream<Path> entries = Files.list(ownerDirectory)) {
      entries
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(ENTRY_SUFFIX))
          .map(name -> name.substring(0, name.length() - ENTRY_SUFFIX.length()))
          .filter(AxeRecoveryJournal::isSafeKey)
          .sorted()
          .limit(MAX_ENTRIES_PER_OWNER)
          .forEach(keys::add);
    }
    return keys;
  }

  synchronized byte[] read(UUID ownerId, String recoveryKey) throws IOException {
    Path entry = entryPath(ownerId, recoveryKey);
    long size = Files.size(entry);
    if (size < HEADER_BYTES || size > HEADER_BYTES + MAX_ENTRY_BYTES) {
      throw new IOException("Invalid thrown-axe recovery entry size: " + size);
    }
    return decode(Files.readAllBytes(entry));
  }

  synchronized void delete(UUID ownerId, String recoveryKey) throws IOException {
    Files.deleteIfExists(entryPath(ownerId, recoveryKey));
  }

  private Path ownerDirectory(UUID ownerId) {
    return root.resolve(Objects.requireNonNull(ownerId).toString());
  }

  private Path entryPath(UUID ownerId, String recoveryKey) {
    if (!isSafeKey(recoveryKey)) {
      throw new IllegalArgumentException("Invalid thrown-axe recovery key");
    }
    return ownerDirectory(ownerId).resolve(recoveryKey + ENTRY_SUFFIX);
  }

  private static boolean isSafeKey(String recoveryKey) {
    if (recoveryKey == null || !recoveryKey.startsWith(KEY_PREFIX)
        || recoveryKey.length() > 128) {
      return false;
    }
    for (int i = 0; i < recoveryKey.length(); i++) {
      char character = recoveryKey.charAt(i);
      if ((character < 'a' || character > 'z')
          && (character < '0' || character > '9')
          && character != '_'
          && character != '-'
          && character != '.') {
        return false;
      }
    }
    return true;
  }

  private static byte[] encode(byte[] payload) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + payload.length);
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeInt(VERSION);
      output.writeInt(payload.length);
      output.write(checksum(payload));
      output.write(payload);
    }
    return bytes.toByteArray();
  }

  private static byte[] decode(byte[] encoded) throws IOException {
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
      int magic = input.readInt();
      int version = input.readInt();
      int payloadLength = input.readInt();
      byte[] expectedChecksum = input.readNBytes(CHECKSUM_BYTES);
      if (magic != MAGIC || version != VERSION
          || payloadLength <= 0 || payloadLength > MAX_ENTRY_BYTES
          || encoded.length != HEADER_BYTES + payloadLength
          || expectedChecksum.length != CHECKSUM_BYTES) {
        throw new IOException("Invalid thrown-axe recovery entry header");
      }
      byte[] payload = input.readNBytes(payloadLength);
      if (payload.length != payloadLength
          || !MessageDigest.isEqual(expectedChecksum, checksum(payload))) {
        throw new IOException("Invalid thrown-axe recovery entry checksum");
      }
      return payload;
    }
  }

  private static byte[] checksum(byte[] payload) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(payload);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }
}
