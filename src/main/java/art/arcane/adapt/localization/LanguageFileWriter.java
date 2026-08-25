package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LanguageFileWriter {
  private static final String TEMP_SUFFIX = ".toml.tmp";

  private LanguageFileWriter() {
  }

  static boolean write(Path target, String label, String content) {
    try {
      writeRequired(target, content);
      return true;
    } catch (Throwable e) {
      Adapt.warn("Failed to write " + label + " " + target + ": " + e.getMessage());
      return false;
    }
  }

  static void writeRequired(Path target, String content) throws Exception {
    writeRequired(target, content, false);
  }

  static void writeAtomicRequired(Path target, String content) throws Exception {
    writeRequired(target, content, true);
  }

  private static void writeRequired(Path target, String content, boolean requireAtomicMove) throws Exception {
    Path directory = target.getParent();
    Path temp = null;
    try {
      Files.createDirectories(directory);
      temp = Files.createTempFile(directory, tempPrefix(target), TEMP_SUFFIX);
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      move(temp, target, requireAtomicMove);
    } catch (Exception failure) {
      discard(temp);
      throw failure;
    }
  }

  private static String tempPrefix(Path target) {
    String name = target.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return (dot > 0 ? name.substring(0, dot) : name) + "-";
  }

  private static void move(Path temp, Path target, boolean requireAtomicMove) throws Exception {
    try {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      if (requireAtomicMove) {
        throw e;
      }
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void discard(Path temp) {
    if (temp == null) {
      return;
    }
    try {
      Files.deleteIfExists(temp);
    } catch (Throwable ignored) {
    }
  }
}
