package art.arcane.adapt.apicompat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Links every reference a plugin jar makes into the server's API (Bukkit, Paper, Adventure, ...) against the API jars
 * of one server version, the way the JVM resolves a call site: owner class present, member declared on it or
 * inherited, same static-ness and the same class-or-interface kind the instruction expects. Server jars win over the
 * plugin jar, as the server class loader is consulted first. References from Adapt's own classes fail the check;
 * references from bundled libraries are listed only, since the JVM links them lazily and Adapt may never reach them.
 */
public final class ServerLinkageCheck {
  private static final List<String> SERVER_PACKAGES = List.of("org/bukkit/", "io/papermc/", "com/destroystokyo/",
      "net/kyori/", "net/md_5/", "org/spigotmc/", "co/aikar/", "com/mojang/brigadier/");
  private static final List<String> UNCHECKED_PACKAGES = List.of("org/bukkit/craftbukkit/");
  private static final List<String> LIBRARY_PACKAGES = List.of("art/arcane/adapt/util/arcane/", "art/arcane/adapt/util/advancements/",
      "art/arcane/adapt/util/jvmdg/", "art/arcane/adapt/util/libby/", "art/arcane/adapt/util/bstats/");

  private static final Set<String> OBJECT_METHODS = Set.of("hashCode()I", "equals(Ljava/lang/Object;)Z",
      "toString()Ljava/lang/String;", "getClass()Ljava/lang/Class;", "clone()Ljava/lang/Object;", "notify()V",
      "notifyAll()V", "wait()V", "wait(J)V", "wait(JI)V", "finalize()V");
  private static final MethodNode JDK_METHOD = new MethodNode();
  private static final FieldNode JDK_FIELD = new FieldNode(0, "", "", null, null);

  private final Map<String, ClassNode> pluginClasses;
  private final List<ZipFile> serverJars = new ArrayList<>();
  private final Map<String, ZipFile> serverIndex = new HashMap<>();
  private final Map<String, ClassNode> serverCache = new HashMap<>();

  private ServerLinkageCheck(Map<String, ClassNode> pluginClasses) {
    this.pluginClasses = pluginClasses;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException("usage: ServerLinkageCheck <plugin jar> <report> <server jars, path separated>");
    }
    ServerLinkageCheck check = new ServerLinkageCheck(classes(Path.of(args[0])));
    for (String jar : args[2].split(File.pathSeparator)) {
      if (!jar.isBlank()) {
        check.addServerJar(Path.of(jar));
      }
    }
    Set<String> failures = new TreeSet<>();
    Set<String> libraryNotes = new TreeSet<>();
    for (ClassNode node : check.pluginClasses.values()) {
      Set<String> problems = check.problems(node);
      (isLibrary(node.name) ? libraryNotes : failures).addAll(problems);
    }
    for (ZipFile jar : check.serverJars) {
      jar.close();
    }
    List<String> report = new ArrayList<>();
    report.add("# Adapt references that do not link (" + failures.size() + ")");
    report.addAll(failures);
    report.add("# Bundled library references that do not link, linked lazily (" + libraryNotes.size() + ")");
    report.addAll(libraryNotes);
    Path output = Path.of(args[1]);
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.write(output, report, StandardCharsets.UTF_8);
    System.out.println("Server linkage: " + check.pluginClasses.size() + " classes, " + failures.size()
        + " Adapt failures, " + libraryNotes.size() + " library notes -> " + output);
    if (!failures.isEmpty()) {
      for (String failure : failures) {
        System.err.println("  " + failure);
      }
      System.exit(1);
    }
  }

  private static boolean isLibrary(String name) {
    if (!name.startsWith("art/arcane/adapt/")) {
      return true;
    }
    for (String prefix : LIBRARY_PACKAGES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, ClassNode> classes(Path jar) throws IOException {
    Map<String, ClassNode> classes = new HashMap<>();
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
          try (InputStream input = zip.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(node.name, node);
          }
        }
      }
    }
    return classes;
  }

  private void addServerJar(Path jar) throws IOException {
    ZipFile zip = new ZipFile(jar.toFile());
    serverJars.add(zip);
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      String name = entries.nextElement().getName();
      if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
        serverIndex.putIfAbsent(name.substring(0, name.length() - ".class".length()), zip);
      }
    }
  }

  private static boolean serverOwned(String owner) {
    for (String prefix : UNCHECKED_PACKAGES) {
      if (owner.startsWith(prefix)) {
        return false;
      }
    }
    for (String prefix : SERVER_PACKAGES) {
      if (owner.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private ClassNode lookup(String name) {
    ZipFile jar = serverIndex.get(name);
    if (jar == null) {
      return pluginClasses.get(name);
    }
    ClassNode cached = serverCache.get(name);
    if (cached != null) {
      return cached;
    }
    try (InputStream input = jar.getInputStream(jar.getEntry(name + ".class"))) {
      ClassNode node = new ClassNode();
      new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
      serverCache.put(name, node);
      return node;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read " + name, exception);
    }
  }

  private Set<String> problems(ClassNode node) {
    Set<String> problems = new TreeSet<>();
    String where = node.name;
    if (node.superName != null) {
      requireKind(where, node.superName, false, problems);
    }
    for (String implemented : node.interfaces) {
      requireKind(where, implemented, true, problems);
    }
    for (MethodNode method : node.methods) {
      for (AbstractInsnNode instruction : method.instructions) {
        if (instruction instanceof MethodInsnNode call) {
          checkMethod(where, call.owner, call.name, call.desc, call.getOpcode(), call.itf, problems);
        } else if (instruction instanceof FieldInsnNode field) {
          checkField(where, field.owner, field.name, field.desc, field.getOpcode(), problems);
        } else if (instruction instanceof TypeInsnNode type) {
          requireClass(where, Type.getObjectType(type.desc), problems);
        } else if (instruction instanceof LdcInsnNode constant && constant.cst instanceof Type type) {
          requireClass(where, type, problems);
        } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
          for (Object argument : dynamic.bsmArgs) {
            if (argument instanceof Handle handle) {
              checkHandle(where, handle, problems);
            }
          }
        }
      }
    }
    return problems;
  }

  private void requireClass(String where, Type type, Set<String> problems) {
    Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
    if (element.getSort() == Type.OBJECT) {
      String name = element.getInternalName();
      if (serverOwned(name) && lookup(name) == null) {
        problems.add(where + " -> class " + name + " missing");
      }
    }
  }

  private void requireKind(String where, String name, boolean expectInterface, Set<String> problems) {
    if (!serverOwned(name)) {
      return;
    }
    ClassNode owner = lookup(name);
    if (owner == null) {
      problems.add(where + " -> class " + name + " missing");
    } else if (isInterface(owner) != expectInterface) {
      problems.add(where + " -> " + name + " is " + (isInterface(owner) ? "an interface" : "a class") + " on this server");
    }
  }

  private void checkHandle(String where, Handle handle, Set<String> problems) {
    int tag = handle.getTag();
    if (tag <= Opcodes.H_PUTSTATIC) {
      int opcode = tag == Opcodes.H_GETFIELD ? Opcodes.GETFIELD : tag == Opcodes.H_GETSTATIC ? Opcodes.GETSTATIC
          : tag == Opcodes.H_PUTFIELD ? Opcodes.PUTFIELD : Opcodes.PUTSTATIC;
      checkField(where, handle.getOwner(), handle.getName(), handle.getDesc(), opcode, problems);
      return;
    }
    int opcode = tag == Opcodes.H_INVOKESTATIC ? Opcodes.INVOKESTATIC : tag == Opcodes.H_INVOKEINTERFACE ? Opcodes.INVOKEINTERFACE
        : tag == Opcodes.H_INVOKESPECIAL || tag == Opcodes.H_NEWINVOKESPECIAL ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
    checkMethod(where, handle.getOwner(), handle.getName(), handle.getDesc(), opcode, handle.isInterface(), problems);
  }

  private void checkMethod(String where, String ownerName, String name, String descriptor, int opcode, boolean itf,
                           Set<String> problems) {
    if (ownerName.startsWith("[") || !serverOwned(ownerName)) {
      return;
    }
    ClassNode owner = lookup(ownerName);
    String member = ownerName + "." + name + descriptor;
    if (owner == null) {
      problems.add(where + " -> class " + ownerName + " missing (" + name + ")");
      return;
    }
    if (opcode == Opcodes.INVOKEINTERFACE && !isInterface(owner)) {
      problems.add(where + " -> " + member + ": invokeinterface on a class");
      return;
    }
    if (opcode == Opcodes.INVOKEVIRTUAL && isInterface(owner)) {
      problems.add(where + " -> " + member + ": invokevirtual on an interface");
      return;
    }
    if ((opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL) && itf != isInterface(owner)) {
      problems.add(where + " -> " + member + ": interface flag does not match the owner kind");
      return;
    }
    MethodNode found = findMethod(owner, name, descriptor);
    if (found == null) {
      problems.add(where + " -> " + member + " missing");
    } else if (found != JDK_METHOD && ((found.access & Opcodes.ACC_STATIC) != 0) != (opcode == Opcodes.INVOKESTATIC)) {
      problems.add(where + " -> " + member + ": static-ness differs");
    }
  }

  private void checkField(String where, String ownerName, String name, String descriptor, int opcode, Set<String> problems) {
    if (!serverOwned(ownerName)) {
      return;
    }
    ClassNode owner = lookup(ownerName);
    String member = ownerName + "." + name + ":" + descriptor;
    if (owner == null) {
      problems.add(where + " -> class " + ownerName + " missing (" + name + ")");
      return;
    }
    FieldNode found = findField(owner, name, descriptor);
    boolean expectStatic = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
    if (found == null) {
      problems.add(where + " -> " + member + " missing");
    } else if (found != JDK_FIELD && ((found.access & Opcodes.ACC_STATIC) != 0) != expectStatic) {
      problems.add(where + " -> " + member + ": static-ness differs");
    }
  }

  /** The member, {@link #JDK_METHOD} when the search reached a JDK type it cannot see, or null when absent. */
  private MethodNode findMethod(ClassNode owner, String name, String descriptor) {
    for (MethodNode method : owner.methods) {
      if (method.name.equals(name) && method.desc.equals(descriptor)) {
        return method;
      }
    }
    List<String> parents = new ArrayList<>(owner.interfaces);
    parents.add(owner.superName == null ? "java/lang/Object" : owner.superName);
    MethodNode result = null;
    for (String parentName : parents) {
      ClassNode parent = lookup(parentName);
      MethodNode found;
      if (parentName.equals("java/lang/Object")) {
        found = OBJECT_METHODS.contains(name + descriptor) ? JDK_METHOD : null;
      } else {
        found = parent == null ? JDK_METHOD : findMethod(parent, name, descriptor);
      }
      if (found != null && found != JDK_METHOD) {
        return found;
      }
      if (found == JDK_METHOD && !serverOwned(parentName)) {
        result = JDK_METHOD;
      }
    }
    return result;
  }

  /** The field, {@link #JDK_FIELD} when the search reached a JDK type it cannot see, or null when absent. */
  private FieldNode findField(ClassNode owner, String name, String descriptor) {
    for (FieldNode field : owner.fields) {
      if (field.name.equals(name) && field.desc.equals(descriptor)) {
        return field;
      }
    }
    List<String> parents = new ArrayList<>(owner.interfaces);
    if (owner.superName != null) {
      parents.add(owner.superName);
    }
    FieldNode result = null;
    for (String parentName : parents) {
      ClassNode parent = lookup(parentName);
      FieldNode found = parentName.equals("java/lang/Object") ? null
          : parent == null ? JDK_FIELD : findField(parent, name, descriptor);
      if (found != null && found != JDK_FIELD) {
        return found;
      }
      if (found == JDK_FIELD && !serverOwned(parentName)) {
        result = JDK_FIELD;
      }
    }
    return result;
  }

  private static boolean isInterface(ClassNode node) {
    return (node.access & Opcodes.ACC_INTERFACE) != 0;
  }
}
