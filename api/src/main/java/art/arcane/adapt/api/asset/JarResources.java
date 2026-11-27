package art.arcane.adapt.api.asset;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lists and reads the resource files an addon ships in its own jar. Shared by the bundle factories. */
@ApiStatus.Internal
public final class JarResources {
  private JarResources() {
  }

  /** Strips slashes from both ends and rejects a root that could leave the jar. */
  public static String normalizeRoot(String root) {
    if (root == null) {
      throw new NullPointerException("root");
    }
    String normalized = root;
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isEmpty() || normalized.contains("..") || normalized.contains("\\")) {
      throw new IllegalArgumentException("Invalid resource root: '" + root + "'");
    }
    return normalized;
  }

  /**
   * Every file below {@code root} in the jar or folder {@code owner} was loaded from, as paths relative to
   * {@code root} with {@code /} separators, sorted. Empty when the folder does not exist.
   *
   * <p>A jar is read from its entry table, so a jar built without directory entries lists the same as one built
   * with them. For classes loaded from a folder, {@code root} is looked for next to the classes and then wherever
   * the class loader finds it, which covers build layouts that keep resources in a separate folder.
   */
  public static List<String> list(Class<?> owner, String root) {
    String normalizedRoot = normalizeRoot(root);
    URL classFile = owner.getResource("/" + owner.getName().replace('.', '/') + ".class");
    if (classFile != null && "jar".equals(classFile.getProtocol())) {
      return listJar(jarOf(owner, classFile), normalizedRoot);
    }
    if (classFile != null && "file".equals(classFile.getProtocol())) {
      Path classes = classesFolder(owner, classFile);
      Path beside = classes.resolve(normalizedRoot);
      if (Files.isDirectory(beside)) {
        return walk(beside);
      }
    }
    return listFromLoader(owner, normalizedRoot);
  }

  public static byte[] read(Class<?> owner, String path) {
    try (InputStream stream = owner.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalArgumentException("Resource " + path + " is not in the jar of " + owner.getName());
      }
      return stream.readAllBytes();
    } catch (IOException failure) {
      throw new UncheckedIOException("Cannot read resource " + path + " of " + owner.getName(), failure);
    }
  }

  private static List<String> listJar(Path jar, String root) {
    String prefix = root + "/";
    List<String> files = new ArrayList<>();
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!entry.isDirectory() && name.startsWith(prefix) && name.length() > prefix.length()) {
          files.add(name.substring(prefix.length()));
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("Cannot list " + jar, failure);
    }
    Collections.sort(files);
    return List.copyOf(files);
  }

  /** The jar file of a {@code jar:file:/...!/entry} URL. */
  private static Path jarOf(Class<?> owner, URL classFile) {
    String spec = classFile.toString();
    int separator = spec.indexOf("!/");
    if (separator < 0) {
      throw new IllegalStateException("Cannot locate the jar of " + owner.getName() + " in " + spec);
    }
    return Paths.get(toUri(owner, spec.substring("jar:".length(), separator)));
  }

  /** The folder the package tree of {@code owner} starts in. */
  private static Path classesFolder(Class<?> owner, URL classFile) {
    Path path = Paths.get(toUri(owner, classFile.toString()));
    int depth = owner.getName().split("\\.").length;
    for (int level = 0; level < depth; level++) {
      path = path.getParent();
    }
    return path;
  }

  private static List<String> listFromLoader(Class<?> owner, String root) {
    URL url = owner.getClassLoader().getResource(root);
    if (url == null) {
      return List.of();
    }
    URI uri = toUri(owner, url.toString());
    try {
      return walk(Paths.get(uri));
    } catch (FileSystemNotFoundException closed) {
      // A URL of a file system that is not open yet, such as a jar inside a mod loader's union file system.
      try (FileSystem opened = FileSystems.newFileSystem(uri, Map.of())) {
        return walk(opened.provider().getPath(uri));
      } catch (FileSystemAlreadyExistsException raced) {
        return walk(Paths.get(uri));
      } catch (IOException failure) {
        throw new UncheckedIOException("Cannot open resources of " + owner.getName() + " at " + url, failure);
      }
    }
  }

  private static URI toUri(Class<?> owner, String spec) {
    try {
      return new URI(spec);
    } catch (URISyntaxException invalid) {
      throw new IllegalStateException("Cannot read resources of " + owner.getName() + " at " + spec, invalid);
    }
  }

  private static List<String> walk(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    List<String> files = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.filter(Files::isRegularFile).forEach((Path file) -> {
        String relative = directory.relativize(file).toString();
        files.add(relative.replace(file.getFileSystem().getSeparator(), "/"));
      });
    } catch (IOException failure) {
      throw new UncheckedIOException("Cannot list " + directory, failure);
    }
    Collections.sort(files);
    return List.copyOf(files);
  }
}
