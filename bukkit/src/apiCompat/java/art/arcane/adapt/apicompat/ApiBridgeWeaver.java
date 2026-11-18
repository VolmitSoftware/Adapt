package art.arcane.adapt.apicompat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Copies the core jar and adds the Bukkit-typed members declared by {@code @ApiBridge} methods in the Bukkit module to
 * the core classes they name. Each woven member is public and synthetic and only calls its bridge method.
 */
public final class ApiBridgeWeaver {
  static final String ANNOTATION = "Lart/arcane/adapt/bukkit/compat/ApiBridge;";
  private static final String CONSTRUCTOR = "<init>";

  private ApiBridgeWeaver() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException("usage: ApiBridgeWeaver <core jar> <bridge classes dir> <output jar>");
    }
    Map<String, List<Bridge>> bridges = bridges(Path.of(args[1]));
    int woven = weave(Path.of(args[0]), bridges, Path.of(args[2]));
    System.out.println("Woven " + woven + " pre-port API bridges into " + bridges.size() + " core classes");
  }

  static Map<String, List<Bridge>> bridges(Path root) throws IOException {
    Map<String, List<Bridge>> bridges = new LinkedHashMap<>();
    Path compat = root.resolve("art/arcane/adapt/bukkit/compat");
    if (!Files.isDirectory(compat)) {
      return bridges;
    }
    List<Path> files;
    try (Stream<Path> listing = Files.list(compat)) {
      files = listing.filter((Path file) -> file.toString().endsWith(".class")).sorted().toList();
    }
    for (Path file : files) {
      ClassNode node = new ClassNode();
      try (InputStream input = Files.newInputStream(file)) {
        new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
      }
      for (MethodNode method : node.methods) {
        AnnotationNode annotation = annotation(method);
        if (annotation == null) {
          continue;
        }
        if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
          throw new IOException(node.name + "." + method.name + " must be public static to be an API bridge");
        }
        Type target = null;
        boolean instance = true;
        String name = method.name;
        for (int index = 0; index < annotation.values.size(); index += 2) {
          String key = (String) annotation.values.get(index);
          Object value = annotation.values.get(index + 1);
          switch (key) {
            case "value" -> target = (Type) value;
            case "instance" -> instance = (Boolean) value;
            case "name" -> name = ((String) value).isEmpty() ? method.name : (String) value;
            default -> throw new IOException("Unknown ApiBridge attribute " + key);
          }
        }
        if (target == null) {
          throw new IOException(node.name + "." + method.name + " names no target");
        }
        Type[] arguments = Type.getArgumentTypes(method.desc);
        String descriptor = method.desc;
        if (name.equals(CONSTRUCTOR)) {
          if (instance || !Type.getReturnType(method.desc).getInternalName().equals(target.getInternalName())) {
            throw new IOException(node.name + "." + method.name + " must be a static factory returning "
                + target.getClassName() + " to bridge a constructor");
          }
          descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, arguments);
        } else if (instance) {
          if (arguments.length == 0 || !arguments[0].getInternalName().equals(target.getInternalName())) {
            throw new IOException(node.name + "." + method.name + " must take " + target.getClassName() + " first");
          }
          Type[] rest = new Type[arguments.length - 1];
          System.arraycopy(arguments, 1, rest, 0, rest.length);
          descriptor = Type.getMethodDescriptor(Type.getReturnType(method.desc), rest);
        }
        bridges.computeIfAbsent(target.getInternalName(), (String ignored) -> new ArrayList<>())
            .add(new Bridge(node.name, method.name, method.desc, name, descriptor, instance));
      }
    }
    return bridges;
  }

  static int weave(Path coreJar, Map<String, List<Bridge>> bridges, Path output) throws IOException {
    Map<String, List<Bridge>> remaining = new LinkedHashMap<>(bridges);
    int woven = 0;
    Files.createDirectories(output.toAbsolutePath().getParent());
    try (JarFile input = new JarFile(coreJar.toFile());
         OutputStream file = Files.newOutputStream(output);
         JarOutputStream jar = new JarOutputStream(file)) {
      Enumeration<JarEntry> entries = input.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        byte[] bytes;
        try (InputStream stream = input.getInputStream(entry)) {
          bytes = stream.readAllBytes();
        }
        String name = entry.getName();
        if (name.endsWith(".class")) {
          List<Bridge> targets = remaining.remove(name.substring(0, name.length() - ".class".length()));
          if (targets != null) {
            bytes = weave(bytes, targets);
            woven += targets.size();
          }
        }
        JarEntry copy = new JarEntry(name);
        copy.setTime(entry.getTime());
        jar.putNextEntry(copy);
        jar.write(bytes);
        jar.closeEntry();
      }
    }
    if (!remaining.isEmpty()) {
      throw new IOException("API bridges name classes that are not in " + coreJar.getFileName() + ": "
          + remaining.keySet());
    }
    return woven;
  }

  static byte[] weave(byte[] bytes, List<Bridge> bridges) throws IOException {
    ClassNode node = new ClassNode();
    new ClassReader(bytes).accept(node, 0);
    for (Bridge bridge : bridges) {
      for (MethodNode existing : node.methods) {
        if (existing.name.equals(bridge.name()) && existing.desc.equals(bridge.descriptor())) {
          throw new IOException(node.name + "." + bridge.name() + bridge.descriptor()
              + " already exists; remove the bridge " + bridge.owner() + "." + bridge.method());
        }
      }
      if (bridge.name().equals(CONSTRUCTOR)) {
        node.methods.add(constructor(node, bridge));
        continue;
      }
      int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | (bridge.instance() ? 0 : Opcodes.ACC_STATIC);
      MethodNode method = new MethodNode(access, bridge.name(), bridge.descriptor(), null, null);
      InsnList code = method.instructions;
      int slot = 0;
      if (bridge.instance()) {
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        slot = 1;
      }
      for (Type argument : Type.getArgumentTypes(bridge.descriptor())) {
        code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
        slot += argument.getSize();
      }
      code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, bridge.owner(), bridge.method(), bridge.methodDescriptor(),
          false));
      code.add(new InsnNode(Type.getReturnType(bridge.descriptor()).getOpcode(Opcodes.IRETURN)));
      method.maxLocals = slot;
      method.maxStack = Math.max(slot, Type.getReturnType(bridge.descriptor()).getSize());
      node.methods.add(method);
    }
    ClassWriter writer = new ClassWriter(0);
    node.accept(writer);
    return writer.toByteArray();
  }

  /**
   * A constructor bridge builds the instance through the current constructor (keeping its validation) in the bridge
   * factory, then copies every instance field into {@code this}. Only for classes whose superclass has a no-argument
   * constructor, such as records.
   */
  private static MethodNode constructor(ClassNode node, Bridge bridge) throws IOException {
    if (!node.superName.equals("java/lang/Record") && !node.superName.equals("java/lang/Object")) {
      throw new IOException(node.name + " extends " + node.superName + "; constructor bridges need Object or Record");
    }
    MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, CONSTRUCTOR, bridge.descriptor(),
        null, null);
    InsnList code = method.instructions;
    code.add(new VarInsnNode(Opcodes.ALOAD, 0));
    code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, CONSTRUCTOR, "()V", false));
    int slot = 1;
    for (Type argument : Type.getArgumentTypes(bridge.descriptor())) {
      code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
      slot += argument.getSize();
    }
    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, bridge.owner(), bridge.method(), bridge.methodDescriptor(),
        false));
    code.add(new VarInsnNode(Opcodes.ASTORE, slot));
    int stack = Math.max(slot, 3);
    for (FieldNode field : node.fields) {
      if ((field.access & Opcodes.ACC_STATIC) != 0) {
        continue;
      }
      code.add(new VarInsnNode(Opcodes.ALOAD, 0));
      code.add(new VarInsnNode(Opcodes.ALOAD, slot));
      code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, field.name, field.desc));
      code.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, field.name, field.desc));
      stack = Math.max(stack, 1 + Type.getType(field.desc).getSize());
    }
    code.add(new InsnNode(Opcodes.RETURN));
    method.maxLocals = slot + 1;
    method.maxStack = stack;
    return method;
  }

  private static AnnotationNode annotation(MethodNode method) {
    if (method.invisibleAnnotations == null) {
      return null;
    }
    for (AnnotationNode annotation : method.invisibleAnnotations) {
      if (annotation.desc.equals(ANNOTATION)) {
        return annotation;
      }
    }
    return null;
  }

  record Bridge(String owner, String method, String methodDescriptor, String name, String descriptor,
                boolean instance) {
  }
}
