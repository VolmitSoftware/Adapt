package art.arcane.adapt.apicompat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Resolves every baseline member against a built jar the way the JVM links a call site: declared on the class or
 * inherited from a superclass or superinterface, with the same descriptor and static-ness, public or protected.
 * Writes a TSV report and exits non-zero when anything is missing.
 */
public final class ApiCompatibilityCheck {
  static final String SAME = "same";
  static final String REMOVED = "removed";
  static final String CHANGED = "signature changed";
  static final String CLASS_MISSING = "class missing";

  private ApiCompatibilityCheck() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException("usage: ApiCompatibilityCheck <baseline> <plugin runtime jar> <report>");
    }
    ApiBaseline baseline = ApiBaseline.read(Path.of(args[0]));
    Map<String, ClassNode> classes = ApiBaseline.classes(Path.of(args[1]), ClassReader.SKIP_CODE);
    List<String> report = check(baseline, classes);
    Path output = Path.of(args[2]);
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.write(output, report, StandardCharsets.UTF_8);

    Map<String, Integer> totals = new TreeMap<>();
    List<String> failures = new ArrayList<>();
    for (String line : report.subList(1, report.size())) {
      String status = line.substring(line.lastIndexOf('\t') + 1);
      totals.merge(status, 1, Integer::sum);
      if (!status.equals(SAME)) {
        failures.add(line);
      }
    }
    System.out.println("Public API members checked: " + (report.size() - 1) + " " + totals + " -> " + output);
    if (!failures.isEmpty()) {
      for (String failure : failures) {
        System.err.println("  " + failure);
      }
      System.err.println(failures.size() + " pre-port API members do not link against " + args[1]);
      System.exit(1);
    }
  }

  static List<String> check(ApiBaseline baseline, Map<String, ClassNode> classes) {
    Set<String> checked = new HashSet<>();
    List<String> report = new ArrayList<>();
    report.add("class\tmember\tstatus");
    for (ApiBaseline.TypeEntry type : baseline.types()) {
      if (type.checked()) {
        checked.add(type.name());
        ClassNode node = classes.get(type.name());
        boolean present = node != null && (node.access & Opcodes.ACC_PUBLIC) != 0
            && (node.access & Opcodes.ACC_INTERFACE) == (type.access() & Opcodes.ACC_INTERFACE);
        report.add(type.name() + "\t<class>\t" + (present ? SAME : CLASS_MISSING));
      }
    }
    for (ApiBaseline.MemberEntry member : baseline.members()) {
      if (!checked.contains(member.owner())) {
        continue;
      }
      report.add(member.owner() + "\t" + member.display() + "\t" + status(member, classes));
    }
    return report;
  }

  static String status(ApiBaseline.MemberEntry member, Map<String, ClassNode> classes) {
    ClassNode owner = classes.get(member.owner());
    if (owner == null) {
      return CLASS_MISSING;
    }
    boolean wantStatic = (member.access() & Opcodes.ACC_STATIC) != 0;
    boolean sameName = false;
    Deque<String> pending = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    pending.add(owner.name);
    while (!pending.isEmpty()) {
      ClassNode node = classes.get(pending.poll());
      if (node == null || !seen.add(node.name)) {
        continue;
      }
      if (member.method()) {
        for (MethodNode method : node.methods) {
          if (method.name.equals(member.name())) {
            sameName = true;
            if (method.desc.equals(member.descriptor()) && linkable(method.access, wantStatic)) {
              return SAME;
            }
          }
        }
      } else {
        for (FieldNode field : node.fields) {
          if (field.name.equals(member.name())) {
            sameName = true;
            if (field.desc.equals(member.descriptor()) && linkable(field.access, wantStatic)) {
              return SAME;
            }
          }
        }
      }
      if (member.name().equals("<init>")) {
        break;
      }
      if (node.superName != null) {
        pending.add(node.superName);
      }
      pending.addAll(node.interfaces);
    }
    return sameName ? CHANGED : REMOVED;
  }

  private static boolean linkable(int access, boolean wantStatic) {
    return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0
        && ((access & Opcodes.ACC_STATIC) != 0) == wantStatic;
  }
}
