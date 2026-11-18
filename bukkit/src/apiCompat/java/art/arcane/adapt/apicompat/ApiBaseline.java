package art.arcane.adapt.apicompat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The pre-port public API as plain text: one tab-separated line per type or member. {@code class} lines are checked
 * types, {@code type} lines are Adapt types the checked members only mention (written as empty stubs for the sample
 * compile), {@code method}/{@code field} lines are the members a third-party plugin may link against.
 */
final class ApiBaseline {
  private static final String NONE = "-";
  private static final int[] FLAGS = {Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED, Opcodes.ACC_STATIC, Opcodes.ACC_FINAL,
      Opcodes.ACC_INTERFACE, Opcodes.ACC_ABSTRACT, Opcodes.ACC_SYNTHETIC, Opcodes.ACC_ANNOTATION, Opcodes.ACC_ENUM,
      Opcodes.ACC_BRIDGE, Opcodes.ACC_VARARGS};
  private static final String[] WORDS = {"public", "protected", "static", "final", "interface", "abstract", "synthetic",
      "annotation", "enum", "bridge", "varargs"};

  private final List<TypeEntry> types;
  private final List<MemberEntry> members;

  ApiBaseline(List<TypeEntry> types, List<MemberEntry> members) {
    this.types = List.copyOf(types);
    this.members = List.copyOf(members);
  }

  List<TypeEntry> types() {
    return types;
  }

  List<MemberEntry> members() {
    return members;
  }

  static ApiBaseline read(Path file) throws IOException {
    List<TypeEntry> types = new ArrayList<>();
    List<MemberEntry> members = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] columns = line.split("\t", -1);
      switch (columns[0]) {
        case "class", "type" -> types.add(new TypeEntry(columns[0].equals("class"), columns[1], access(columns[2]),
            columns[3].equals(NONE) ? -1 : access(columns[3]), optional(columns[4]),
            columns[5].equals(NONE) ? List.of() : Arrays.asList(columns[5].split(",")), optional(columns[6])));
        case "method", "field" -> members.add(new MemberEntry(columns[0].equals("method"), columns[1],
            access(columns[2]), columns[3], columns[4], optional(columns[5])));
        default -> throw new IOException("Unknown baseline line: " + line);
      }
    }
    return new ApiBaseline(types, members);
  }

  void write(Path file, String header) throws IOException {
    List<String> lines = new ArrayList<>();
    for (String headerLine : header.split("\n")) {
      lines.add("# " + headerLine);
    }
    for (TypeEntry type : types) {
      lines.add(String.join("\t", type.checked() ? "class" : "type", type.name(), words(type.access()),
          type.innerAccess() < 0 ? NONE : words(type.innerAccess()), orNone(type.superName()),
          type.interfaces().isEmpty() ? NONE : String.join(",", type.interfaces()), orNone(type.signature())));
    }
    for (MemberEntry member : members) {
      lines.add(String.join("\t", member.method() ? "method" : "field", member.owner(), words(member.access()),
          member.name(), member.descriptor(), orNone(member.signature())));
    }
    Files.createDirectories(file.toAbsolutePath().getParent());
    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  static Map<String, ClassNode> classes(Path jar, int parsingOptions) throws IOException {
    Map<String, ClassNode> classes = new LinkedHashMap<>();
    try (JarFile file = new JarFile(jar.toFile())) {
      Enumeration<JarEntry> entries = file.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) {
          continue;
        }
        try (InputStream input = file.getInputStream(entry)) {
          ClassNode node = new ClassNode();
          new ClassReader(input).accept(node, parsingOptions);
          classes.put(node.name, node);
        }
      }
    }
    return classes;
  }

  static String words(int access) {
    List<String> words = new ArrayList<>();
    for (int index = 0; index < FLAGS.length; index++) {
      if ((access & FLAGS[index]) != 0) {
        words.add(WORDS[index]);
      }
    }
    return words.isEmpty() ? "package" : String.join(" ", words);
  }

  static int access(String words) {
    int access = 0;
    for (String word : words.split(" ")) {
      for (int index = 0; index < WORDS.length; index++) {
        if (WORDS[index].equals(word)) {
          access |= FLAGS[index];
        }
      }
    }
    return access;
  }

  static int relevant(int access) {
    return access(words(access));
  }

  private static String optional(String value) {
    return value.equals(NONE) ? null : value;
  }

  private static String orNone(String value) {
    return value == null ? NONE : value;
  }

  record TypeEntry(boolean checked, String name, int access, int innerAccess, String superName,
                   List<String> interfaces, String signature) {
  }

  record MemberEntry(boolean method, String owner, int access, String name, String descriptor, String signature) {
    String display() {
      return ((access & Opcodes.ACC_STATIC) != 0 ? "static " : "") + name + (method ? "" : ":") + descriptor;
    }
  }
}
