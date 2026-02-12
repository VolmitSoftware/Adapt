package com.volmit.adapt.content.gui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.service.ConfigInputSVC;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.GuiConfirm;
import com.volmit.adapt.util.GuiEffects;
import com.volmit.adapt.util.GuiLayout;
import com.volmit.adapt.util.GuiTheme;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.MaterialBlock;
import com.volmit.adapt.util.UIElement;
import com.volmit.adapt.util.UIWindow;
import com.volmit.adapt.util.Window;
import com.volmit.adapt.util.config.ConfigDocumentation;
import com.volmit.adapt.util.config.TomlCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConfigGui {
    private static final String TAG_PREFIX = "config/adapt";
    private static final String SOURCE_TAG = "core-config";
    private static final Object WRITE_LOCK = new Object();
    private static final int MAX_VALUE_PREVIEW = 64;

    private ConfigGui() {
    }

    public static void open(Player player) {
        open(player, "", 0);
    }

    public static void open(Player player, String sectionPath) {
        open(player, sectionPath, 0);
    }

    public static void open(Player player, String sectionPath, int page) {
        if (player == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            String path = sectionPath;
            int targetPage = page;
            J.s(() -> open(player, path, targetPage));
            return;
        }

        String safePath = normalizePath(sectionPath);
        Object section = resolveSectionObject(AdaptConfig.get(), safePath, false);
        if (section == null) {
            Adapt.messagePlayer(player, C.RED + "Unable to open config section: " + C.WHITE + (safePath.isBlank() ? "<root>" : safePath));
            return;
        }

        boolean reserveNavigation = !safePath.isBlank();
        List<FieldEntry> entries = buildEntries(safePath, section);
        GuiLayout.PagePlan plan = GuiLayout.plan(entries.size(), reserveNavigation);
        int currentPage = GuiLayout.clampPage(page, plan.pageCount());
        int start = currentPage * plan.itemsPerPage();
        int end = Math.min(entries.size(), start + plan.itemsPerPage());

        Window w = new UIWindow(player);
        GuiTheme.apply(w, tagForSection(safePath));
        w.setViewportHeight(plan.rows());

        if (entries.isEmpty()) {
            w.setElement(0, 0, new UIElement("cfg-empty")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.GRAY + "No settings in this section"));
        } else {
            List<GuiEffects.Placement> reveal = new ArrayList<>();
            for (int row = 0; row < plan.contentRows(); row++) {
                int rowStart = start + (row * GuiLayout.WIDTH);
                if (rowStart >= end) {
                    break;
                }

                int rowCount = Math.min(GuiLayout.WIDTH, end - rowStart);
                for (int i = 0; i < rowCount; i++) {
                    FieldEntry entry = entries.get(rowStart + i);
                    int pos = GuiLayout.centeredPosition(i, rowCount);
                    Element element = createElementForEntry(player, safePath, entry);
                    reveal.add(new GuiEffects.Placement(pos, row, element));
                }
            }
            GuiEffects.applyReveal(w, reveal);
        }

        if (plan.hasNavigationRow()) {
            int navRow = plan.rows() - 1;
            if (currentPage > 0) {
                w.setElement(-4, navRow, new UIElement("cfg-prev")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Previous")
                        .onLeftClick((e) -> open(player, safePath, currentPage - 1)));
            }
            if (currentPage < plan.pageCount() - 1) {
                w.setElement(4, navRow, new UIElement("cfg-next")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Next")
                        .onLeftClick((e) -> open(player, safePath, currentPage + 1)));
            }
            if (!safePath.isBlank()) {
                String parent = parentPath(safePath);
                w.setElement(0, navRow, new UIElement("cfg-back")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.GRAY + "Back")
                        .onLeftClick((e) -> open(player, parent, 0)));
            }
        }

        String titlePath = safePath.isBlank() ? "root" : safePath;
        if (titlePath.length() > 24) {
            titlePath = "..." + titlePath.substring(titlePath.length() - 21);
        }
        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        w.setTitle(C.GRAY + "Config: " + C.WHITE + titlePath + pageSuffix);
        w.onClosed((window) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString()));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    public static void reopenFromTag(Player player, String tag) {
        if (player == null) {
            return;
        }

        if (tag == null || tag.isBlank()) {
            open(player);
            return;
        }

        if (!tag.startsWith(TAG_PREFIX)) {
            open(player);
            return;
        }

        String path = "";
        if (tag.length() > TAG_PREFIX.length()) {
            path = tag.substring(TAG_PREFIX.length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }

        open(player, path);
    }

    public static ParseResult parseInputValue(Class<?> type, String raw) {
        if (type == null) {
            return ParseResult.fail("Unknown target type.");
        }

        Class<?> normalized = normalizeType(type);
        String trimmed = raw == null ? "" : raw.trim();

        try {
            if (normalized == String.class) {
                return ParseResult.ok(raw == null ? "" : raw);
            }

            if (normalized == Character.class) {
                if (trimmed.length() != 1) {
                    return ParseResult.fail("Expected exactly one character.");
                }
                return ParseResult.ok(trimmed.charAt(0));
            }

            if (normalized == Boolean.class) {
                if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("yes") || trimmed.equalsIgnoreCase("on")) {
                    return ParseResult.ok(true);
                }
                if (trimmed.equalsIgnoreCase("false") || trimmed.equalsIgnoreCase("no") || trimmed.equalsIgnoreCase("off")) {
                    return ParseResult.ok(false);
                }
                return ParseResult.fail("Expected boolean value: true/false.");
            }

            if (normalized.isEnum()) {
                Object constant = parseEnumConstant(normalized, trimmed);
                if (constant == null) {
                    return ParseResult.fail("Expected one of: " + enumConstants(normalized));
                }
                return ParseResult.ok(constant);
            }

            if (normalized == Integer.class) {
                return ParseResult.ok(Integer.parseInt(trimmed));
            }
            if (normalized == Long.class) {
                return ParseResult.ok(Long.parseLong(trimmed));
            }
            if (normalized == Double.class) {
                double v = Double.parseDouble(trimmed);
                if (!Double.isFinite(v)) {
                    return ParseResult.fail("Expected a finite number.");
                }
                return ParseResult.ok(v);
            }
            if (normalized == Float.class) {
                float v = Float.parseFloat(trimmed);
                if (!Float.isFinite(v)) {
                    return ParseResult.fail("Expected a finite number.");
                }
                return ParseResult.ok(v);
            }
            if (normalized == Short.class) {
                return ParseResult.ok(Short.parseShort(trimmed));
            }
            if (normalized == Byte.class) {
                return ParseResult.ok(Byte.parseByte(trimmed));
            }
        } catch (Throwable e) {
            return ParseResult.fail("Invalid value for type " + typeName(type) + ".");
        }

        return ParseResult.fail("Unsupported type: " + typeName(type) + ".");
    }

    public static boolean applyAndSave(Player actor, String valuePath, Object value) {
        String path = normalizePath(valuePath);
        if (path.isBlank()) {
            return false;
        }

        synchronized (WRITE_LOCK) {
            AdaptConfig config = AdaptConfig.get();
            Object before = readPathValue(config, path);
            String beforeToml = TomlCodec.toToml(config, SOURCE_TAG);

            if (!setPathValue(config, path, value, true)) {
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Failed to set config value at " + C.WHITE + path);
                }
                return false;
            }

            File file = Adapt.instance.getDataFile("adapt", "adapt.toml");

            try {
                String updatedToml = TomlCodec.toToml(config, SOURCE_TAG);
                IO.writeAll(file, updatedToml);
            } catch (Throwable e) {
                J.attempt(() -> IO.writeAll(file, beforeToml));
                AdaptConfig.reload();
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Failed to persist config update: " + C.WHITE + e.getMessage());
                }
                return false;
            }

            if (!AdaptConfig.reload()) {
                J.attempt(() -> IO.writeAll(file, beforeToml));
                AdaptConfig.reload();
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Config reload failed. Reverted file changes.");
                }
                return false;
            }

            refreshGlobalRuntimeSettings();
            if (actor != null) {
                Adapt.messagePlayer(actor, C.GREEN + "Updated " + C.WHITE + path
                        + C.GRAY + " [" + summarizeValue(before) + C.GRAY + " -> " + summarizeValue(value) + C.GRAY + "]");
            }
            return true;
        }
    }

    public static void confirmAndApply(Player actor, String returnSectionPath, String valuePath, Object value) {
        String path = normalizePath(valuePath);
        if (path.isBlank()) {
            return;
        }

        String section = normalizePath(returnSectionPath);
        Object before = readPathValue(AdaptConfig.get(), path);
        String message = C.GRAY + summarizeValue(before) + C.DARK_GRAY + " -> " + C.AQUA + summarizeValue(value);
        GuiConfirm.open(
                actor,
                "Apply " + displayName(path),
                message,
                () -> {
                    if (applyAndSave(actor, path, value)) {
                        open(actor, section, 0);
                    }
                },
                () -> open(actor, section, 0)
        );
    }

    public static String typeName(Class<?> type) {
        if (type == null) {
            return "unknown";
        }

        Class<?> normalized = normalizeType(type);
        if (normalized.isEnum()) {
            return "enum";
        }
        return normalized.getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static ElementDescriptor describe(Field field, Object value) {
        Class<?> type = normalizeType(field.getType());
        if (type == Boolean.class) {
            return new ElementDescriptor(ElementKind.BOOLEAN, true);
        }

        if (isNumericType(type)) {
            return new ElementDescriptor(ElementKind.NUMBER, true);
        }

        if (type == String.class || type == Character.class) {
            return new ElementDescriptor(ElementKind.STRING, true);
        }

        if (type.isEnum()) {
            return new ElementDescriptor(ElementKind.ENUM, true);
        }

        if (Map.class.isAssignableFrom(type)) {
            return new ElementDescriptor(ElementKind.MAP, false);
        }

        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return new ElementDescriptor(ElementKind.LIST, false);
        }

        if (value != null || isSectionType(type)) {
            return new ElementDescriptor(ElementKind.SECTION, false);
        }

        return new ElementDescriptor(ElementKind.UNSUPPORTED, false);
    }

    private static UIElement createElementForEntry(Player player, String sectionPath, FieldEntry entry) {
        Material material = materialFor(entry);
        String typePrefix = switch (entry.descriptor().kind()) {
            case BOOLEAN -> C.GREEN + "[Boolean] ";
            case NUMBER -> C.AQUA + "[Number] ";
            case STRING -> C.YELLOW + "[Text] ";
            case ENUM -> C.LIGHT_PURPLE + "[Enum] ";
            case SECTION -> C.BLUE + "[Section] ";
            case MAP -> C.GOLD + "[Map] ";
            case LIST -> C.GOLD + "[List] ";
            case UNSUPPORTED -> C.RED + "[Unsupported] ";
        };
        String name = displayName(entry.field().getName());
        String value = summarizeValue(entry.value());

        UIElement element = new UIElement("cfg-" + entry.path())
                .setMaterial(new MaterialBlock(material))
                .setName(typePrefix + C.WHITE + name);
        element.addLore(C.GRAY + "Value: " + C.AQUA + value);
        element.addLore(C.DARK_GRAY + "Path: " + entry.path());
        element.setProgress(1D);

        for (String line : entry.docs()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            element.addLore(C.DARK_GRAY + line);
        }

        switch (entry.descriptor().kind()) {
            case BOOLEAN -> {
                element.addLore(C.GREEN + "Left click: toggle");
                element.onLeftClick((e) -> {
                    boolean toggled = !Boolean.TRUE.equals(entry.value());
                    confirmAndApply(player, sectionPath, entry.path(), toggled);
                });
            }
            case ENUM -> {
                element.addLore(C.GREEN + "Left click: next value");
                element.addLore(C.GREEN + "Right click: previous value");
                element.onLeftClick((e) -> {
                    Object next = cycleEnum(entry.field().getType(), entry.value(), 1);
                    if (next != null) {
                        confirmAndApply(player, sectionPath, entry.path(), next);
                    }
                });
                element.onRightClick((e) -> {
                    Object previous = cycleEnum(entry.field().getType(), entry.value(), -1);
                    if (previous != null) {
                        confirmAndApply(player, sectionPath, entry.path(), previous);
                    }
                });
            }
            case NUMBER, STRING -> {
                element.addLore(C.YELLOW + "Left click: edit in chat");
                element.onLeftClick((e) -> {
                    ConfigInputSVC service = Adapt.service(ConfigInputSVC.class);
                    if (service == null) {
                        Adapt.messagePlayer(player, C.RED + "Config input service is unavailable.");
                        return;
                    }

                    service.beginSession(player, entry.path(), sectionPath, entry.field().getType(), displayName(entry.field().getName()));
                });
            }
            case SECTION -> {
                element.addLore(C.GREEN + "Left click: open section");
                element.onLeftClick((e) -> open(player, entry.path(), 0));
            }
            case MAP, LIST -> element.addLore(C.RED + "Read-only in Phase 1");
            case UNSUPPORTED -> element.addLore(C.RED + "Unsupported type");
        }

        return element;
    }

    private static Material materialFor(FieldEntry entry) {
        return switch (entry.descriptor().kind()) {
            case BOOLEAN, NUMBER, STRING, ENUM, SECTION, MAP, LIST -> Material.PAPER;
            case UNSUPPORTED -> Material.BARRIER;
        };
    }

    private static List<FieldEntry> buildEntries(String sectionPath, Object sectionObject) {
        List<FieldEntry> sections = new ArrayList<>();
        List<FieldEntry> values = new ArrayList<>();
        for (Field field : getSerializableFields(sectionObject.getClass())) {
            Object value = getFieldValue(field, sectionObject);
            String childPath = joinPath(sectionPath, field.getName());
            ElementDescriptor descriptor = describe(field, value);
            List<String> docs = ConfigDocumentation.buildFieldComments(SOURCE_TAG, childPath, field, value);
            FieldEntry entry = new FieldEntry(field, childPath, value, descriptor, docs);
            if (descriptor.kind() == ElementKind.SECTION) {
                sections.add(entry);
            } else {
                values.add(entry);
            }
        }

        sections.sort(Comparator.comparing(e -> e.field().getName()));
        values.sort(Comparator.comparing(e -> e.field().getName()));
        sections.addAll(values);
        return sections;
    }

    private static String tagForSection(String sectionPath) {
        String path = normalizePath(sectionPath);
        if (path.isBlank()) {
            return TAG_PREFIX;
        }
        return TAG_PREFIX + "/" + path;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }

        String normalized = path.trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String parentPath(String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return "";
        }

        int dot = normalized.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return normalized.substring(0, dot);
    }

    private static String joinPath(String base, String child) {
        String left = normalizePath(base);
        if (left.isBlank()) {
            return child;
        }
        return left + "." + child;
    }

    private static Object resolveSectionObject(Object root, String sectionPath, boolean createMissing) {
        if (root == null) {
            return null;
        }

        String normalized = normalizePath(sectionPath);
        if (normalized.isBlank()) {
            return root;
        }

        Object current = root;
        for (String segment : normalized.split("\\Q.\\E")) {
            Field field = findField(current.getClass(), segment);
            if (field == null) {
                return null;
            }

            Object next = getFieldValue(field, current);
            if (next == null && createMissing) {
                next = instantiate(field.getType());
                if (next == null) {
                    return null;
                }
                if (!setFieldValue(field, current, next)) {
                    return null;
                }
            }

            if (next == null) {
                return null;
            }

            current = next;
        }

        return current;
    }

    private static boolean setPathValue(Object root, String path, Object value, boolean createMissing) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return false;
        }

        String[] segments = normalized.split("\\Q.\\E");
        if (segments.length == 0) {
            return false;
        }

        StringBuilder parentPath = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) {
                parentPath.append('.');
            }
            parentPath.append(segments[i]);
        }

        Object section = resolveSectionObject(root, parentPath.toString(), createMissing);
        if (section == null) {
            return false;
        }

        Field targetField = findField(section.getClass(), segments[segments.length - 1]);
        if (targetField == null) {
            return false;
        }

        Object typedValue = coerceValue(value, targetField.getType());
        return setFieldValue(targetField, section, typedValue);
    }

    private static Object readPathValue(Object root, String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return null;
        }

        String[] segments = normalized.split("\\Q.\\E");
        if (segments.length == 0) {
            return null;
        }

        StringBuilder parentPath = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) {
                parentPath.append('.');
            }
            parentPath.append(segments[i]);
        }

        Object section = resolveSectionObject(root, parentPath.toString(), false);
        if (section == null) {
            return null;
        }

        Field field = findField(section.getClass(), segments[segments.length - 1]);
        if (field == null) {
            return null;
        }

        return getFieldValue(field, section);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private static List<Field> getSerializableFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        collectFields(type, fields);
        return fields;
    }

    private static void collectFields(Class<?> type, List<Field> out) {
        if (type == null || type == Object.class) {
            return;
        }

        collectFields(type.getSuperclass(), out);
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            field.setAccessible(true);
            out.add(field);
        }
    }

    private static Object instantiate(Class<?> type) {
        Class<?> normalized = normalizeType(type);
        if (normalized.isPrimitive() || normalized.isEnum() || normalized == String.class || isNumericType(normalized) || normalized == Boolean.class) {
            return null;
        }

        try {
            return normalized.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean setFieldValue(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getFieldValue(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object coerceValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        Class<?> normalizedTarget = normalizeType(targetType);
        Class<?> valueType = value.getClass();
        if (normalizedTarget.isAssignableFrom(valueType)) {
            return value;
        }

        ParseResult parsed = parseInputValue(targetType, String.valueOf(value));
        return parsed.success() ? parsed.value() : value;
    }

    private static Class<?> normalizeType(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }

        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static boolean isNumericType(Class<?> type) {
        return type == Integer.class
                || type == Long.class
                || type == Double.class
                || type == Float.class
                || type == Short.class
                || type == Byte.class;
    }

    private static boolean isSectionType(Class<?> type) {
        Class<?> normalized = normalizeType(type);
        if (normalized == null) {
            return false;
        }

        if (normalized.isPrimitive() || normalized.isEnum()) {
            return false;
        }

        if (normalized == String.class || normalized == Character.class || normalized == Boolean.class || isNumericType(normalized)) {
            return false;
        }

        if (Map.class.isAssignableFrom(normalized) || Collection.class.isAssignableFrom(normalized) || normalized.isArray()) {
            return false;
        }

        return true;
    }

    private static Object cycleEnum(Class<?> enumType, Object current, int direction) {
        Class<?> normalized = normalizeType(enumType);
        if (normalized == null || !normalized.isEnum()) {
            return null;
        }

        Object[] constants = normalized.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return null;
        }

        int currentIndex = 0;
        if (current != null) {
            for (int i = 0; i < constants.length; i++) {
                if (Objects.equals(constants[i], current)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int nextIndex = currentIndex + direction;
        if (nextIndex < 0) {
            nextIndex = constants.length - 1;
        } else if (nextIndex >= constants.length) {
            nextIndex = 0;
        }
        return constants[nextIndex];
    }

    private static Object parseEnumConstant(Class<?> enumType, String value) {
        if (enumType == null || !enumType.isEnum() || value == null) {
            return null;
        }

        for (Object constant : enumType.getEnumConstants()) {
            if (constant == null) {
                continue;
            }

            if (constant.toString().equalsIgnoreCase(value)) {
                return constant;
            }
        }

        return null;
    }

    private static String enumConstants(Class<?> enumType) {
        if (enumType == null || !enumType.isEnum()) {
            return "";
        }

        List<String> values = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            if (constant == null) {
                continue;
            }
            values.add(constant.toString());
        }
        return String.join(", ", values);
    }

    private static String displayName(String key) {
        if (key == null || key.isBlank()) {
            return "Unnamed";
        }

        String spaced = key
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (spaced.isBlank()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String summarizeValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Map<?, ?> map) {
            return "map(" + map.size() + ")";
        }
        if (value instanceof Collection<?> collection) {
            return "list(" + collection.size() + ")";
        }
        if (value.getClass().isArray()) {
            return "array";
        }

        String text = String.valueOf(value)
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        if (text.length() > MAX_VALUE_PREVIEW) {
            return text.substring(0, MAX_VALUE_PREVIEW - 3) + "...";
        }
        return text;
    }

    private static void refreshGlobalRuntimeSettings() {
        Adapt.wordKey.clear();
        if (AdaptConfig.get().isAutoUpdateLanguage()) {
            Localizer.updateLanguageFile();
        }

        if (AdaptConfig.get().isCustomModels()) {
            CustomModel.reloadFromDisk();
        } else {
            CustomModel.clear();
        }
    }

    private record FieldEntry(Field field, String path, Object value, ElementDescriptor descriptor, List<String> docs) {
    }

    private record ElementDescriptor(ElementKind kind, boolean editable) {
    }

    private enum ElementKind {
        BOOLEAN,
        NUMBER,
        STRING,
        ENUM,
        SECTION,
        MAP,
        LIST,
        UNSUPPORTED
    }

    public record ParseResult(boolean success, Object value, String error) {
        public static ParseResult ok(Object value) {
            return new ParseResult(true, value, "");
        }

        public static ParseResult fail(String error) {
            return new ParseResult(false, null, error == null ? "Invalid value." : error);
        }
    }
}
