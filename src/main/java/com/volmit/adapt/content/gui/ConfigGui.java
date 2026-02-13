package com.volmit.adapt.content.gui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.service.ConfigInputSVC;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.GuiEffects;
import com.volmit.adapt.util.GuiLayout;
import com.volmit.adapt.util.GuiTheme;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.MaterialBlock;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.UIElement;
import com.volmit.adapt.util.UIWindow;
import com.volmit.adapt.util.Window;
import com.volmit.adapt.util.config.ConfigDocumentation;
import com.volmit.adapt.util.config.TomlCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class ConfigGui {
    private static final String TAG_PREFIX = "config/adapt";
    private static final String SOURCE_TAG_CORE = "core-config";
    private static final String ROOT_CORE = "core";
    private static final String ROOT_CORE_GENERAL = ROOT_CORE + ".$general";
    private static final String ROOT_SKILLS = "skills";
    private static final String ROOT_ADAPTATIONS = "adaptations";
    private static final String ROOT_ADAPTATIONS_SKILLS = ROOT_ADAPTATIONS + ".$skills";
    private static final String ROOT_ADAPTATIONS_ALL = ROOT_ADAPTATIONS + ".$all";
    private static final Object WRITE_LOCK = new Object();
    private static final int MAX_VALUE_PREVIEW = 64;
    private static final int PAGE_JUMP = 5;
    private static final int CONFIG_CONTENT_ROWS = GuiLayout.MAX_ROWS - 1;
    private static final long CLOSE_SUPPRESS_MS = 1200L;
    private static final int CLOSE_SUPPRESS_CLEAR_TICKS = 4;
    private static final Map<UUID, Long> CLOSE_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

    private ConfigGui() {
    }

    public static boolean canConfigure(Player player) {
        return player != null && (player.isOp() || player.hasPermission("adapt.configurator"));
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

        if (!canConfigure(player)) {
            Adapt.messagePlayer(player, C.RED + "You do not have permission to use the config menu.");
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            String path = sectionPath;
            int targetPage = page;
            J.s(() -> open(player, path, targetPage));
            return;
        }

        playPageTurn(player);
        String safePath = normalizePath(sectionPath);
        if (safePath.isBlank()) {
            openRoot(player, page);
            return;
        }

        if (safePath.equals(ROOT_SKILLS)) {
            openSkillIndex(player, page);
            return;
        }

        if (safePath.equals(ROOT_CORE)) {
            openCoreIndex(player, page);
            return;
        }

        if (safePath.equals(ROOT_CORE_GENERAL)) {
            openCoreGeneral(player, page);
            return;
        }

        if (safePath.equals(ROOT_ADAPTATIONS) || safePath.equals(ROOT_ADAPTATIONS_SKILLS)) {
            openAdaptationSkillIndex(player, page);
            return;
        }

        if (safePath.equals(ROOT_ADAPTATIONS_ALL)) {
            openAdaptationIndex(player, page);
            return;
        }

        if (safePath.startsWith(ROOT_ADAPTATIONS_SKILLS + ".")) {
            String skillName = safePath.substring(ROOT_ADAPTATIONS_SKILLS.length() + 1);
            openAdaptationIndexForSkill(player, skillName, page);
            return;
        }

        SectionTarget target = resolveSectionTarget(safePath, false);
        if (target == null || target.sectionObject() == null) {
            Adapt.messagePlayer(player, C.RED + "Unable to open config section: " + C.WHITE + safePath);
            return;
        }

        List<FieldEntry> entries = buildEntries(safePath, target.sectionObject(), target.sourceTag());
        openFieldEntries(player, safePath, entries, page);
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

        navigateTo(player, path, 0);
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
            EditTarget target = resolveEditTarget(path);
            if (target == null) {
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Failed to set config value at " + C.WHITE + path);
                }
                return false;
            }

            Object before = readPathValue(target.rootObject(), target.objectPath());
            String beforeToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());

            if (!setPathValue(target.rootObject(), target.objectPath(), value, true)) {
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Failed to set config value at " + C.WHITE + path);
                }
                return false;
            }

            try {
                String updatedToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());
                IO.writeAll(target.file(), updatedToml);
            } catch (Throwable e) {
                J.attempt(() -> IO.writeAll(target.file(), beforeToml));
                target.reload().getAsBoolean();
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Failed to persist config update: " + C.WHITE + e.getMessage());
                }
                return false;
            }

            if (!target.reload().getAsBoolean()) {
                J.attempt(() -> IO.writeAll(target.file(), beforeToml));
                target.reload().getAsBoolean();
                if (actor != null) {
                    Adapt.messagePlayer(actor, C.RED + "Config reload failed. Reverted file changes.");
                }
                return false;
            }

            target.afterReload().run();
            if (actor != null) {
                Adapt.messagePlayer(actor, C.GREEN + "Updated " + C.WHITE + path
                        + C.GRAY + " [" + summarizeValue(before) + C.GRAY + " -> " + summarizeValue(value) + C.GRAY + "]");
            }
            return true;
        }
    }

    public static void confirmAndApply(Player actor, String returnSectionPath, String valuePath, Object value) {
        confirmAndApply(actor, returnSectionPath, 0, valuePath, value);
    }

    public static void confirmAndApply(Player actor, String returnSectionPath, int returnPage, String valuePath, Object value) {
        String path = normalizePath(valuePath);
        if (path.isBlank()) {
            return;
        }

        String section = normalizePath(returnSectionPath);
        applyAndSave(actor, path, value);
        navigateTo(actor, section, Math.max(0, returnPage));
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

    private static UIElement createElementForEntry(Player player, String sectionPath, int currentPage, FieldEntry entry) {
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
        if (entry.descriptor().kind() == ElementKind.BOOLEAN && Boolean.TRUE.equals(entry.value())) {
            element.setEnchanted(true);
        }

        if (entry.descriptor().kind() == ElementKind.SECTION && entry.value() != null) {
            int nested = getSerializableFields(entry.value().getClass()).size();
            element.addLore(C.GRAY + "Contains " + C.WHITE + nested + C.GRAY + " setting" + (nested == 1 ? "" : "s"));
            element.addLore(C.DARK_GRAY + "Category: " + sectionCategory(entry.field().getName()));
        }

        int docsShown = 0;
        for (String line : entry.docs()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (docsShown >= 3) {
                element.addLore(C.DARK_GRAY + "...");
                break;
            }
            element.addLore(C.GRAY + line);
            docsShown++;
        }

        switch (entry.descriptor().kind()) {
            case BOOLEAN -> {
                element.addLore(Boolean.TRUE.equals(entry.value())
                        ? C.GREEN + "State: Enabled"
                        : C.RED + "State: Disabled");
                element.addLore(C.GREEN + "Left click: toggle");
                element.onLeftClick((e) -> {
                    boolean toggled = !Boolean.TRUE.equals(entry.value());
                    confirmAndApply(player, sectionPath, currentPage, entry.path(), toggled);
                });
            }
            case ENUM -> {
                element.addLore(C.GREEN + "Left click: next value");
                element.addLore(C.GREEN + "Right click: previous value");
                element.onLeftClick((e) -> {
                    Object next = cycleEnum(entry.field().getType(), entry.value(), 1);
                    if (next != null) {
                        confirmAndApply(player, sectionPath, currentPage, entry.path(), next);
                    }
                });
                element.onRightClick((e) -> {
                    Object previous = cycleEnum(entry.field().getType(), entry.value(), -1);
                    if (previous != null) {
                        confirmAndApply(player, sectionPath, currentPage, entry.path(), previous);
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

                    service.beginSession(player, entry.path(), sectionPath, currentPage, entry.field().getType(), displayName(entry.field().getName()));
                });
            }
            case SECTION -> {
                element.addLore(C.GREEN + "Left click: open section");
                element.onLeftClick((e) -> navigateTo(player, entry.path(), 0));
            }
            case MAP, LIST -> element.addLore(C.RED + "Read-only in Phase 1");
            case UNSUPPORTED -> element.addLore(C.RED + "Unsupported type");
        }

        return element;
    }

    private static Material materialFor(FieldEntry entry) {
        return switch (entry.descriptor().kind()) {
            case BOOLEAN -> Boolean.TRUE.equals(entry.value()) ? Material.LIME_DYE : Material.GRAY_DYE;
            case NUMBER -> Material.CLOCK;
            case STRING -> Material.NAME_TAG;
            case ENUM -> Material.BOOK;
            case SECTION -> materialForSection(entry.field().getName());
            case MAP -> Material.CHEST_MINECART;
            case LIST -> Material.BARREL;
            case UNSUPPORTED -> Material.BARRIER;
        };
    }

    private static Material materialForSection(String name) {
        String key = normalizeSortKey(name);
        if (key.contains("gui") || key.contains("menu") || key.contains("display") || key.contains("hud")) {
            return Material.BOOKSHELF;
        }
        if (key.contains("sound") || key.contains("audio")) {
            return Material.JUKEBOX;
        }
        if (key.contains("lang") || key.contains("locale") || key.contains("translation")) {
            return Material.WRITABLE_BOOK;
        }
        if (key.contains("sql") || key.contains("database") || key.contains("storage") || key.contains("mysql")) {
            return Material.ENDER_CHEST;
        }
        if (key.contains("xp") || key.contains("level") || key.contains("knowledge") || key.contains("power")) {
            return Material.EXPERIENCE_BOTTLE;
        }
        if (key.contains("world") || key.contains("biome") || key.contains("dimension") || key.contains("region")) {
            return Material.GRASS_BLOCK;
        }
        if (key.contains("thread") || key.contains("tick") || key.contains("async") || key.contains("performance") || key.contains("cache")) {
            return Material.REDSTONE;
        }
        if (key.contains("permission") || key.contains("blacklist") || key.contains("whitelist") || key.contains("security")) {
            return Material.SHIELD;
        }
        if (key.contains("debug") || key.contains("dev") || key.contains("test") || key.contains("verbose")) {
            return Material.SPYGLASS;
        }
        return Material.CHEST;
    }

    private static String sectionCategory(String name) {
        String key = normalizeSortKey(name);
        if (key.contains("gui") || key.contains("menu") || key.contains("display") || key.contains("hud")) {
            return "UI";
        }
        if (key.contains("sound") || key.contains("audio")) {
            return "Audio";
        }
        if (key.contains("lang") || key.contains("locale") || key.contains("translation")) {
            return "Localization";
        }
        if (key.contains("sql") || key.contains("database") || key.contains("storage") || key.contains("mysql")) {
            return "Storage";
        }
        if (key.contains("xp") || key.contains("level") || key.contains("knowledge") || key.contains("power")) {
            return "Progression";
        }
        if (key.contains("world") || key.contains("biome") || key.contains("dimension") || key.contains("region")) {
            return "World";
        }
        if (key.contains("thread") || key.contains("tick") || key.contains("async") || key.contains("performance") || key.contains("cache")) {
            return "Performance";
        }
        if (key.contains("permission") || key.contains("blacklist") || key.contains("whitelist") || key.contains("security")) {
            return "Access";
        }
        if (key.contains("debug") || key.contains("dev") || key.contains("test") || key.contains("verbose")) {
            return "Debug";
        }
        return "General";
    }

    private static List<FieldEntry> buildEntries(String sectionPath, Object sectionObject, String sourceTag) {
        List<FieldEntry> sections = new ArrayList<>();
        List<FieldEntry> values = new ArrayList<>();
        for (Field field : getSerializableFields(sectionObject.getClass())) {
            Object value = getFieldValue(field, sectionObject);
            String childPath = joinPath(sectionPath, field.getName());
            ElementDescriptor descriptor = describe(field, value);
            List<String> docs = ConfigDocumentation.buildFieldComments(sourceTag, childPath, field, value);
            FieldEntry entry = new FieldEntry(field, childPath, value, descriptor, docs);
            if (descriptor.kind() == ElementKind.SECTION) {
                sections.add(entry);
            } else {
                values.add(entry);
            }
        }

        sections.sort(Comparator.comparing(e -> normalizeSortKey(e.field().getName())));
        values.sort(Comparator.comparing(e -> normalizeSortKey(e.field().getName())));
        sections.addAll(values);
        return sections;
    }

    private static List<FieldEntry> buildCoreGeneralEntries() {
        List<FieldEntry> values = new ArrayList<>();
        Object root = AdaptConfig.get();
        for (Field field : getSerializableFields(root.getClass())) {
            Object value = getFieldValue(field, root);
            ElementDescriptor descriptor = describe(field, value);
            if (descriptor.kind() == ElementKind.SECTION) {
                continue;
            }

            String childPath = joinPath(ROOT_CORE, field.getName());
            List<String> docs = ConfigDocumentation.buildFieldComments(SOURCE_TAG_CORE, childPath, field, value);
            values.add(new FieldEntry(field, childPath, value, descriptor, docs));
        }

        values.sort(Comparator.comparing(e -> normalizeSortKey(e.field().getName())));
        return values;
    }

    private static void openFieldEntries(Player player, String safePath, List<FieldEntry> entries, int page) {
        GuiLayout.PagePlan plan = configPagePlan(entries.size());
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
                    Element element = createElementForEntry(player, safePath, currentPage, entry);
                    reveal.add(new GuiEffects.Placement(pos, row, element));
                }
            }
            GuiEffects.applyReveal(w, reveal);
        }

        int navRow = plan.rows() - 1;
        applyPageControls(w, player, safePath, navRow, currentPage, plan.pageCount(), entries.size(), start, end);
        if (!safePath.isBlank()) {
            String parent = parentPath(safePath);
            w.setElement(0, navRow, new UIElement("cfg-back")
                    .setMaterial(new MaterialBlock(Material.ARROW))
                    .setName(C.GRAY + "Back")
                    .onLeftClick((e) -> navigateTo(player, parent, 0)));
        }
        addSectionOverview(w, navRow, safePath, entries, currentPage, plan.pageCount());

        String titlePath = safePath.isBlank() ? "root" : safePath;
        if (safePath.equals(ROOT_CORE_GENERAL)) {
            titlePath = "core.general";
        }
        if (titlePath.length() > 24) {
            titlePath = "..." + titlePath.substring(titlePath.length() - 21);
        }
        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        w.setTitle(C.GRAY + "Configure: " + C.WHITE + titlePath + pageSuffix);
        w.onClosed((window) -> onGuiClosed(player, safePath));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private static void openCoreIndex(Player player, int page) {
        Object root = AdaptConfig.get();
        List<SectionIndexEntry> entries = new ArrayList<>();
        int generalValues = 0;
        for (Field field : getSerializableFields(root.getClass())) {
            Object value = getFieldValue(field, root);
            ElementDescriptor descriptor = describe(field, value);
            if (descriptor.kind() == ElementKind.SECTION) {
                int nested = value == null ? 0 : getSerializableFields(value.getClass()).size();
                entries.add(new SectionIndexEntry(
                        ROOT_CORE + "." + field.getName(),
                        displayName(field.getName()),
                        materialForSection(field.getName()),
                        "Open " + nested + " setting" + (nested == 1 ? "" : "s")
                ));
            } else {
                generalValues++;
            }
        }

        if (generalValues > 0) {
            entries.add(new SectionIndexEntry(
                    ROOT_CORE_GENERAL,
                    "General Settings",
                    Material.COMPARATOR,
                    "Open " + generalValues + " global option" + (generalValues == 1 ? "" : "s")
            ));
        }

        entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
        openSectionIndex(player, ROOT_CORE, page, "Configure: core", entries);
    }

    private static void openCoreGeneral(Player player, int page) {
        openFieldEntries(player, ROOT_CORE_GENERAL, buildCoreGeneralEntries(), page);
    }

    private static void openRoot(Player player, int page) {
        List<SectionIndexEntry> entries = new ArrayList<>();
        entries.add(new SectionIndexEntry(ROOT_ADAPTATIONS_SKILLS, "Adaptations", Material.NETHER_STAR, "Configure adaptation settings"));
        entries.add(new SectionIndexEntry(ROOT_CORE, "Core", Material.COMPARATOR, "Configure global Adapt settings"));
        entries.add(new SectionIndexEntry(ROOT_SKILLS, "Skills", Material.ENCHANTED_BOOK, "Configure skill settings"));
        entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
        openSectionIndex(player, "", page, "Configure Adapt", entries);
    }

    private static void openSkillIndex(Player player, int page) {
        List<Skill<?>> skills = getLoadedSkills();
        List<SectionIndexEntry> entries = new ArrayList<>();
        for (Skill<?> skill : skills) {
            if (skill == null) {
                continue;
            }

            entries.add(new SectionIndexEntry(
                    ROOT_SKILLS + "." + skill.getName(),
                    C.stripColor(skill.getDisplayName()),
                    skill.getIcon(),
                    "Configure " + skill.getName()
            ));
        }

        entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
        openSectionIndex(player, ROOT_SKILLS, page, "Configure: skills", entries);
    }

    private static void openAdaptationSkillIndex(Player player, int page) {
        List<SectionIndexEntry> entries = new ArrayList<>();
        entries.add(new SectionIndexEntry(
                ROOT_ADAPTATIONS_ALL,
                "All Adaptations (A-Z)",
                Material.NETHER_STAR,
                "Browse every adaptation alphabetically"
        ));

        for (Skill<?> skill : getLoadedSkills()) {
            if (skill == null) {
                continue;
            }

            int adaptationCount = skill.getAdaptations() == null ? 0 : skill.getAdaptations().size();
            if (adaptationCount <= 0) {
                continue;
            }

            entries.add(new SectionIndexEntry(
                    ROOT_ADAPTATIONS_SKILLS + "." + skill.getName(),
                    C.stripColor(skill.getDisplayName()),
                    skill.getIcon(),
                    "Browse " + adaptationCount + " adaptation" + (adaptationCount == 1 ? "" : "s")
            ));
        }

        List<SectionIndexEntry> head = new ArrayList<>();
        List<SectionIndexEntry> skillEntries = new ArrayList<>();
        for (SectionIndexEntry entry : entries) {
            if (ROOT_ADAPTATIONS_ALL.equals(entry.path())) {
                head.add(entry);
            } else {
                skillEntries.add(entry);
            }
        }
        skillEntries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
        head.addAll(skillEntries);
        openSectionIndex(player, ROOT_ADAPTATIONS_SKILLS, page, "Configure: adaptations", head);
    }

    private static void openAdaptationIndexForSkill(Player player, String skillName, int page) {
        Skill<?> skill = resolveSkill(skillName);
        if (skill == null) {
            Adapt.messagePlayer(player, C.RED + "Unknown skill for adaptation config: " + C.WHITE + skillName);
            navigateTo(player, ROOT_ADAPTATIONS_SKILLS, 0);
            return;
        }

        List<SectionIndexEntry> entries = new ArrayList<>();
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
            if (adaptation == null) {
                continue;
            }
            entries.add(new SectionIndexEntry(
                    ROOT_ADAPTATIONS + "." + adaptation.getName(),
                    C.stripColor(adaptation.getDisplayName()),
                    adaptation.getIcon(),
                    "Open " + adaptation.getName()
            ));
        }

        entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
        openSectionIndex(player, ROOT_ADAPTATIONS_SKILLS + "." + skill.getName(), page, "Configure: " + C.stripColor(skill.getDisplayName()), entries);
    }

    private static void openAdaptationIndex(Player player, int page) {
        List<SectionIndexEntry> entries = new ArrayList<>();
        for (Adaptation<?> adaptation : getLoadedAdaptations()) {
            if (adaptation == null || adaptation.getSkill() == null) {
                continue;
            }

            entries.add(new SectionIndexEntry(
                    ROOT_ADAPTATIONS + "." + adaptation.getName(),
                    C.stripColor(adaptation.getDisplayName()),
                    adaptation.getIcon(),
                    "Skill: " + adaptation.getSkill().getName()
            ));
        }

        entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
        openSectionIndex(player, ROOT_ADAPTATIONS_ALL, page, "Configure: all adaptations", entries);
    }

    private static void openSectionIndex(Player player, String sectionPath, int page, String title, List<SectionIndexEntry> entries) {
        String safePath = normalizePath(sectionPath);
        GuiLayout.PagePlan plan = configPagePlan(entries.size());
        int currentPage = GuiLayout.clampPage(page, plan.pageCount());
        int start = currentPage * plan.itemsPerPage();
        int end = Math.min(entries.size(), start + plan.itemsPerPage());

        Window w = new UIWindow(player);
        GuiTheme.apply(w, tagForSection(safePath));
        w.setViewportHeight(plan.rows());

        if (entries.isEmpty()) {
            w.setElement(0, 0, new UIElement("cfg-empty")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.GRAY + "No entries"));
        } else {
            List<GuiEffects.Placement> reveal = new ArrayList<>();
            for (int row = 0; row < plan.contentRows(); row++) {
                int rowStart = start + (row * GuiLayout.WIDTH);
                if (rowStart >= end) {
                    break;
                }

                int rowCount = Math.min(GuiLayout.WIDTH, end - rowStart);
                for (int i = 0; i < rowCount; i++) {
                    SectionIndexEntry entry = entries.get(rowStart + i);
                    int pos = GuiLayout.centeredPosition(i, rowCount);
                    Element element = new UIElement("cfg-index-" + entry.path())
                            .setMaterial(new MaterialBlock(entry.material()))
                            .setName(C.WHITE + entry.displayName())
                            .addLore(C.GRAY + entry.lore())
                            .addLore(C.DARK_GRAY + "Path: " + entry.path())
                            .setProgress(1D)
                            .onLeftClick((e) -> navigateTo(player, entry.path(), 0));
                    reveal.add(new GuiEffects.Placement(pos, row, element));
                }
            }
            GuiEffects.applyReveal(w, reveal);
        }

        int navRow = plan.rows() - 1;
        applyPageControls(w, player, safePath, navRow, currentPage, plan.pageCount(), entries.size(), start, end);
        if (!safePath.isBlank()) {
            w.setElement(0, navRow, new UIElement("cfg-back")
                    .setMaterial(new MaterialBlock(Material.ARROW))
                    .setName(C.GRAY + "Back")
                    .onLeftClick((e) -> navigateTo(player, parentPath(safePath), 0)));
        }
        addIndexOverview(w, navRow, safePath, entries.size(), currentPage, plan.pageCount(), title);

        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        w.setTitle(C.GRAY + title + pageSuffix);
        w.onClosed((window) -> onGuiClosed(player, safePath));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private static GuiLayout.PagePlan configPagePlan(int totalEntries) {
        int items = Math.max(0, totalEntries);
        int rows = GuiLayout.MAX_ROWS;
        int contentRows = CONFIG_CONTENT_ROWS;
        int itemsPerPage = contentRows * GuiLayout.WIDTH;
        int pageCount = Math.max(1, (int) Math.ceil(items / (double) itemsPerPage));
        return new GuiLayout.PagePlan(rows, contentRows, true, itemsPerPage, pageCount);
    }

    private static void applyPageControls(
            Window window,
            Player player,
            String safePath,
            int navRow,
            int currentPage,
            int pageCount,
            int totalEntries,
            int start,
            int end
    ) {
        if (pageCount <= 1) {
            return;
        }

        int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
        int jumpForward = Math.min(pageCount - 1, currentPage + PAGE_JUMP);

        if (currentPage > 0) {
            window.setElement(-4, navRow, new UIElement("cfg-prev")
                    .setMaterial(new MaterialBlock(Material.ARROW))
                    .setName(C.WHITE + "Previous")
                    .addLore(C.GRAY + "Left click: previous page")
                    .addLore(C.GRAY + "Right click: jump -" + PAGE_JUMP + " pages")
                    .onLeftClick((e) -> navigateTo(player, safePath, currentPage - 1))
                    .onRightClick((e) -> navigateTo(player, safePath, jumpBack)));
            window.setElement(-3, navRow, new UIElement("cfg-first")
                    .setMaterial(new MaterialBlock(Material.LECTERN))
                    .setName(C.GRAY + "First")
                    .onLeftClick((e) -> navigateTo(player, safePath, 0)));
        }

        if (currentPage < pageCount - 1) {
            window.setElement(4, navRow, new UIElement("cfg-next")
                    .setMaterial(new MaterialBlock(Material.ARROW))
                    .setName(C.WHITE + "Next")
                    .addLore(C.GRAY + "Left click: next page")
                    .addLore(C.GRAY + "Right click: jump +" + PAGE_JUMP + " pages")
                    .onLeftClick((e) -> navigateTo(player, safePath, currentPage + 1))
                    .onRightClick((e) -> navigateTo(player, safePath, jumpForward)));
            window.setElement(3, navRow, new UIElement("cfg-last")
                    .setMaterial(new MaterialBlock(Material.LECTERN))
                    .setName(C.GRAY + "Last")
                    .onLeftClick((e) -> navigateTo(player, safePath, pageCount - 1)));
        }

        int from = totalEntries <= 0 ? 0 : (start + 1);
        int to = totalEntries <= 0 ? 0 : end;
        window.setElement(-1, navRow, new UIElement("cfg-page-info")
                .setMaterial(new MaterialBlock(Material.PAPER))
                .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + pageCount)
                .addLore(C.GRAY + "Showing " + from + "-" + to + " of " + totalEntries)
                .setProgress(1D));
    }

    private static void addSectionOverview(
            Window window,
            int navRow,
            String path,
            List<FieldEntry> entries,
            int currentPage,
            int pageCount
    ) {
        int sections = 0;
        int editable = 0;
        for (FieldEntry entry : entries) {
            if (entry.descriptor().kind() == ElementKind.SECTION) {
                sections++;
            }
            if (entry.descriptor().editable()) {
                editable++;
            }
        }

        String safePath = path == null || path.isBlank() ? "root" : path;
        window.setElement(1, navRow, new UIElement("cfg-overview")
                .setMaterial(new MaterialBlock(Material.BOOK))
                .setName(C.AQUA + "Overview")
                .addLore(C.GRAY + "Path: " + C.WHITE + safePath)
                .addLore(C.GRAY + "Sections: " + C.WHITE + sections)
                .addLore(C.GRAY + "Editable: " + C.WHITE + editable)
                .addLore(C.GRAY + "Entries: " + C.WHITE + entries.size())
                .setProgress(1D));

        window.setElement(2, navRow, new UIElement("cfg-help")
                .setMaterial(new MaterialBlock(Material.KNOWLEDGE_BOOK))
                .setName(C.GRAY + "Help")
                .addLore(C.GRAY + "LMB: open/edit/toggle")
                .addLore(C.GRAY + "RMB: enum prev / page jump")
                .addLore(C.GRAY + "ESC: back to parent page")
                .addLore(C.DARK_GRAY + "Page " + (currentPage + 1) + "/" + pageCount)
                .setProgress(1D));
    }

    private static void addIndexOverview(
            Window window,
            int navRow,
            String path,
            int totalEntries,
            int currentPage,
            int pageCount,
            String title
    ) {
        String safePath = path == null || path.isBlank() ? "root" : path;
        window.setElement(1, navRow, new UIElement("cfg-index-overview")
                .setMaterial(new MaterialBlock(Material.BOOK))
                .setName(C.AQUA + "Directory")
                .addLore(C.GRAY + "Path: " + C.WHITE + safePath)
                .addLore(C.GRAY + "Entries: " + C.WHITE + totalEntries)
                .addLore(C.GRAY + "Page: " + C.WHITE + (currentPage + 1) + "/" + pageCount)
                .addLore(C.DARK_GRAY + title)
                .setProgress(1D));

        window.setElement(2, navRow, new UIElement("cfg-index-help")
                .setMaterial(new MaterialBlock(Material.KNOWLEDGE_BOOK))
                .setName(C.GRAY + "Navigation")
                .addLore(C.GRAY + "LMB: open section")
                .addLore(C.GRAY + "RMB on arrows: jump pages")
                .addLore(C.GRAY + "ESC: back to parent page")
                .setProgress(1D));
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

        if (normalized.equals(ROOT_ADAPTATIONS) || normalized.equals(ROOT_ADAPTATIONS_SKILLS)) {
            return "";
        }

        if (normalized.equals(ROOT_CORE_GENERAL)) {
            return ROOT_CORE;
        }

        if (normalized.equals(ROOT_ADAPTATIONS_ALL)) {
            return ROOT_ADAPTATIONS_SKILLS;
        }

        if (normalized.startsWith(ROOT_ADAPTATIONS_SKILLS + ".")) {
            return ROOT_ADAPTATIONS_SKILLS;
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

    private static SectionTarget resolveSectionTarget(String path, boolean createMissing) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return new SectionTarget(SOURCE_TAG_CORE, resolveSectionObject(AdaptConfig.get(), "", createMissing));
        }

        if (normalized.equals(ROOT_CORE) || normalized.startsWith(ROOT_CORE + ".")) {
            String objectPath = stripPrefix(normalized, ROOT_CORE);
            return new SectionTarget(SOURCE_TAG_CORE, resolveSectionObject(AdaptConfig.get(), objectPath, createMissing));
        }

        if (normalized.equals(ROOT_SKILLS) || normalized.startsWith(ROOT_SKILLS + ".")) {
            String payload = stripPrefix(normalized, ROOT_SKILLS);
            if (payload.isBlank()) {
                return null;
            }

            String[] parts = payload.split("\\Q.\\E", 2);
            Skill<?> skill = resolveSkill(parts[0]);
            if (skill == null || skill.getConfig() == null) {
                return null;
            }

            String objectPath = parts.length > 1 ? parts[1] : "";
            return new SectionTarget("skill:" + skill.getName(), resolveSectionObject(skill.getConfig(), objectPath, createMissing));
        }

        if (normalized.equals(ROOT_ADAPTATIONS) || normalized.startsWith(ROOT_ADAPTATIONS + ".")) {
            String payload = stripPrefix(normalized, ROOT_ADAPTATIONS);
            if (payload.isBlank()) {
                return null;
            }

            String[] parts = payload.split("\\Q.\\E", 2);
            Adaptation<?> adaptation = resolveAdaptation(parts[0]);
            if (adaptation == null || adaptation.getConfig() == null) {
                return null;
            }

            String objectPath = parts.length > 1 ? parts[1] : "";
            return new SectionTarget("adaptation:" + adaptation.getName(), resolveSectionObject(adaptation.getConfig(), objectPath, createMissing));
        }

        return new SectionTarget(SOURCE_TAG_CORE, resolveSectionObject(AdaptConfig.get(), normalized, createMissing));
    }

    private static EditTarget resolveEditTarget(String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.equals(ROOT_CORE) || normalized.startsWith(ROOT_CORE + ".")) {
            String objectPath = stripPrefix(normalized, ROOT_CORE);
            if (objectPath.isBlank()) {
                return null;
            }
            return new EditTarget(
                    SOURCE_TAG_CORE,
                    AdaptConfig.get(),
                    objectPath,
                    Adapt.instance.getDataFile("adapt", "adapt.toml"),
                    AdaptConfig::reload,
                    ConfigGui::refreshGlobalRuntimeSettings
            );
        }

        if (normalized.equals(ROOT_SKILLS) || normalized.startsWith(ROOT_SKILLS + ".")) {
            String payload = stripPrefix(normalized, ROOT_SKILLS);
            if (payload.isBlank()) {
                return null;
            }

            String[] parts = payload.split("\\Q.\\E", 2);
            Skill<?> skill = resolveSkill(parts[0]);
            if (skill == null || skill.getConfig() == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
                return null;
            }

            String objectPath = parts.length > 1 ? parts[1] : "";
            if (objectPath.isBlank()) {
                return null;
            }

            return new EditTarget(
                    "skill:" + skill.getName(),
                    skill.getConfig(),
                    objectPath,
                    Adapt.instance.getDataFile("adapt", "skills", skill.getName() + ".toml"),
                    () -> Adapt.instance.getAdaptServer().getSkillRegistry().hotReloadSkillConfig(skill.getName()),
                    () -> {
                    }
            );
        }

        if (normalized.equals(ROOT_ADAPTATIONS) || normalized.startsWith(ROOT_ADAPTATIONS + ".")) {
            String payload = stripPrefix(normalized, ROOT_ADAPTATIONS);
            if (payload.isBlank()) {
                return null;
            }

            String[] parts = payload.split("\\Q.\\E", 2);
            Adaptation<?> adaptation = resolveAdaptation(parts[0]);
            if (adaptation == null || adaptation.getConfig() == null || !(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
                return null;
            }

            String objectPath = parts.length > 1 ? parts[1] : "";
            if (objectPath.isBlank()) {
                return null;
            }

            return new EditTarget(
                    "adaptation:" + adaptation.getName(),
                    adaptation.getConfig(),
                    objectPath,
                    Adapt.instance.getDataFile("adapt", "adaptations", adaptation.getName() + ".toml"),
                    () -> simpleAdaptation.reloadConfigFromDisk(false),
                    () -> {
                    }
            );
        }

        return new EditTarget(
                SOURCE_TAG_CORE,
                AdaptConfig.get(),
                normalized,
                Adapt.instance.getDataFile("adapt", "adapt.toml"),
                AdaptConfig::reload,
                ConfigGui::refreshGlobalRuntimeSettings
        );
    }

    private static String stripPrefix(String path, String prefix) {
        String normalized = normalizePath(path);
        if (normalized.equals(prefix)) {
            return "";
        }

        String withDot = prefix + ".";
        if (normalized.startsWith(withDot)) {
            return normalized.substring(withDot.length());
        }
        return normalized;
    }

    private static Skill<?> resolveSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return null;
        }
        if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
            return null;
        }
        return Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillName);
    }

    private static Adaptation<?> resolveAdaptation(String adaptationName) {
        if (adaptationName == null || adaptationName.isBlank()) {
            return null;
        }

        for (Skill<?> skill : getLoadedSkills()) {
            if (skill == null) {
                continue;
            }

            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation == null || adaptation.getName() == null) {
                    continue;
                }

                if (adaptation.getName().equalsIgnoreCase(adaptationName)) {
                    return adaptation;
                }
            }
        }

        return null;
    }

    private static List<Skill<?>> getLoadedSkills() {
        if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
            return List.of();
        }

        List<Skill<?>> skills = new ArrayList<>(Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills());
        skills.sort(Comparator.comparing(skill -> normalizeSortKey(C.stripColor(skill.getDisplayName()))));
        return skills;
    }

    private static List<Adaptation<?>> getLoadedAdaptations() {
        List<Adaptation<?>> adaptations = new ArrayList<>();
        for (Skill<?> skill : getLoadedSkills()) {
            if (skill == null) {
                continue;
            }
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation == null) {
                    continue;
                }
                adaptations.add(adaptation);
            }
        }

        adaptations.sort(Comparator.comparing(adaptation -> normalizeSortKey(C.stripColor(adaptation.getDisplayName()))));
        return adaptations;
    }

    private static String normalizeSortKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = C.stripColor(value).toLowerCase(Locale.ROOT).trim();
        return normalized.replaceFirst("^[^\\p{L}\\p{N}]+", "");
    }

    private static void playPageTurn(Player player) {
        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        spw.play(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
    }

    private static void navigateTo(Player player, String path, int page) {
        if (player == null) {
            return;
        }
        suppressClose(player);
        open(player, path, page);
    }

    public static void suppressClose(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long suppressUntil = M.ms() + CLOSE_SUPPRESS_MS;
        CLOSE_SUPPRESS_UNTIL.put(playerId, suppressUntil);
        J.s(() -> {
            Long current = CLOSE_SUPPRESS_UNTIL.get(playerId);
            if (current != null && current == suppressUntil) {
                CLOSE_SUPPRESS_UNTIL.remove(playerId);
            }
        }, CLOSE_SUPPRESS_CLEAR_TICKS);
    }

    private static boolean consumeCloseSuppression(Player player) {
        if (player == null) {
            return false;
        }

        Long until = CLOSE_SUPPRESS_UNTIL.get(player.getUniqueId());
        if (until == null) {
            return false;
        }

        if (until >= M.ms()) {
            CLOSE_SUPPRESS_UNTIL.remove(player.getUniqueId());
            return true;
        }

        CLOSE_SUPPRESS_UNTIL.remove(player.getUniqueId());
        return false;
    }

    private static void onGuiClosed(Player player, String currentPath) {
        if (player == null) {
            return;
        }

        Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString());
        if (consumeCloseSuppression(player)) {
            return;
        }

        if (AdaptConfig.get().isEscClosesAllGuis()) {
            return;
        }

        String safePath = normalizePath(currentPath);
        if (safePath.isBlank()) {
            return;
        }

        String parent = parentPath(safePath);
        J.s(() -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
                open(player, parent, 0);
            }
        }, 1);
    }

    private record FieldEntry(Field field, String path, Object value, ElementDescriptor descriptor, List<String> docs) {
    }

    private record ElementDescriptor(ElementKind kind, boolean editable) {
    }

    private record SectionIndexEntry(String path, String displayName, Material material, String lore) {
    }

    private record SectionTarget(String sourceTag, Object sectionObject) {
    }

    private record EditTarget(
            String sourceTag,
            Object rootObject,
            String objectPath,
            File file,
            BooleanSupplier reload,
            Runnable afterReload
    ) {
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
