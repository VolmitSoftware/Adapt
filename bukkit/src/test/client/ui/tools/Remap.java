import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Remap {
    private Remap() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 5) {
            throw new IllegalArgumentException("Remap <input> <output> <mappings> <from> <to> [classpath...]");
        }
        Path input = Path.of(arguments[0]);
        Path output = Path.of(arguments[1]);
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(Path.of(arguments[2]), tree);
        int source = namespace(tree, arguments[3]);
        int target = namespace(tree, arguments[4]);
        IMappingProvider provider = acceptor -> {
            for (MappingTree.ClassMapping type : tree.getClasses()) {
                String owner = type.getName(source);
                if (owner == null) {
                    continue;
                }
                String renamed = type.getName(target);
                acceptor.acceptClass(owner, renamed == null ? owner : renamed);
                for (MappingTree.MethodMapping method : type.getMethods()) {
                    String name = method.getName(source);
                    String desc = method.getDesc(source);
                    String mapped = method.getName(target);
                    if (name != null && desc != null && mapped != null) {
                        acceptor.acceptMethod(new IMappingProvider.Member(owner, name, desc), mapped);
                    }
                }
                for (MappingTree.FieldMapping field : type.getFields()) {
                    String name = field.getName(source);
                    String mapped = field.getName(target);
                    if (name != null && mapped != null) {
                        acceptor.acceptField(new IMappingProvider.Member(owner, name, field.getDesc(source)), mapped);
                    }
                }
            }
        };
        boolean fieldDescriptors = tree.getClasses().stream().flatMap(type -> type.getFields().stream())
                .allMatch(field -> field.getDesc(source) != null);
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(provider)
                .ignoreFieldDesc(!fieldDescriptors)
                .extension(new MixinExtension())
                .build();
        List<Path> classpath = new ArrayList<>();
        for (int index = 5; index < arguments.length; index++) {
            classpath.add(Path.of(arguments[index]));
        }
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            consumer.addNonClassFiles(input, NonClassCopyMode.UNCHANGED, remapper);
            remapper.readInputs(input);
            remapper.readClassPath(classpath.toArray(Path[]::new));
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
    }

    private static int namespace(MemoryMappingTree tree, String name) {
        if (name.equals(tree.getSrcNamespace())) {
            return MappingTree.SRC_NAMESPACE_ID;
        }
        int id = tree.getNamespaceId(name);
        if (id == MappingTree.NULL_NAMESPACE_ID) {
            throw new IllegalArgumentException("Namespace " + name + " not in " + tree.getSrcNamespace() + " " + tree.getDstNamespaces());
        }
        return id;
    }
}
