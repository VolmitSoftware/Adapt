package art.arcane.adapt.apicompat;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Writes a compile-only jar with exactly the baseline surface, so the third-party sample compiles against the
 * pre-port API and nothing newer. Method bodies throw.
 */
public final class ApiStubJar {
  private ApiStubJar() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      throw new IllegalArgumentException("usage: ApiStubJar <baseline> <output jar>");
    }
    ApiBaseline baseline = ApiBaseline.read(Path.of(args[0]));
    Map<String, ApiBaseline.TypeEntry> types = new LinkedHashMap<>();
    for (ApiBaseline.TypeEntry type : baseline.types()) {
      types.put(type.name(), type);
    }
    Map<String, List<ApiBaseline.MemberEntry>> members = new LinkedHashMap<>();
    for (ApiBaseline.MemberEntry member : baseline.members()) {
      members.computeIfAbsent(member.owner(), (String ignored) -> new ArrayList<>()).add(member);
    }

    Path output = Path.of(args[1]);
    Files.createDirectories(output.toAbsolutePath().getParent());
    try (OutputStream file = Files.newOutputStream(output); JarOutputStream jar = new JarOutputStream(file)) {
      for (ApiBaseline.TypeEntry type : types.values()) {
        jar.putNextEntry(new JarEntry(type.name() + ".class"));
        jar.write(stub(type, types, members.getOrDefault(type.name(), List.of())));
        jar.closeEntry();
      }
    }
  }

  private static byte[] stub(ApiBaseline.TypeEntry type, Map<String, ApiBaseline.TypeEntry> types,
                             List<ApiBaseline.MemberEntry> members) {
    ClassWriter writer = new ClassWriter(0);
    boolean isInterface = (type.access() & Opcodes.ACC_INTERFACE) != 0;
    writer.visit(Opcodes.V21, type.access() | (isInterface ? 0 : Opcodes.ACC_SUPER), type.name(), type.signature(),
        type.superName(), type.interfaces().toArray(new String[0]));
    inner(writer, type, types);
    for (ApiBaseline.TypeEntry candidate : types.values()) {
      if (outer(candidate.name()).equals(type.name())) {
        inner(writer, candidate, types);
      }
    }
    for (ApiBaseline.MemberEntry member : members) {
      if (!member.method()) {
        writer.visitField(member.access(), member.name(), member.descriptor(), member.signature(), null).visitEnd();
        continue;
      }
      MethodVisitor method = writer.visitMethod(member.access(), member.name(), member.descriptor(),
          member.signature(), null);
      if ((member.access() & Opcodes.ACC_ABSTRACT) == 0) {
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ATHROW);
        int locals = (Type.getArgumentsAndReturnSizes(member.descriptor()) >> 2)
            - ((member.access() & Opcodes.ACC_STATIC) != 0 ? 1 : 0);
        method.visitMaxs(1, locals);
      }
      method.visitEnd();
    }
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static void inner(ClassWriter writer, ApiBaseline.TypeEntry type, Map<String, ApiBaseline.TypeEntry> types) {
    String outer = outer(type.name());
    if (outer.isEmpty() || !types.containsKey(outer) || type.innerAccess() < 0) {
      return;
    }
    writer.visitInnerClass(type.name(), outer, type.name().substring(outer.length() + 1), type.innerAccess());
  }

  private static String outer(String name) {
    int nested = name.lastIndexOf('$');
    return nested < 0 ? "" : name.substring(0, nested);
  }
}
