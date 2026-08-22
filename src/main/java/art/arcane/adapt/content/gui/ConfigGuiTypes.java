package art.arcane.adapt.content.gui;

import org.bukkit.Material;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;

enum ElementKind {
  BOOLEAN,
  NUMBER,
  STRING,
  ENUM,
  SECTION,
  MAP,
  LIST,
  UNSUPPORTED
}

record FieldEntry(Field field, String path, Object value,
                  ElementDescriptor descriptor, List<String> docs) {
}

record ElementDescriptor(ElementKind kind, boolean editable) {
}

record SectionIndexEntry(String path, String displayName, Material material,
                         String lore) {
}

record SectionTarget(String sourceTag, Object sectionObject) {
}

record EditTarget(
    String sourceTag,
    Object rootObject,
    String objectPath,
    File file,
    BooleanSupplier reload,
    Runnable afterReload
) {
}
