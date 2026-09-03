package art.arcane.adapt.content.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.service.ConfigInputSVC;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDocumentation;
import art.arcane.adapt.util.config.TomlCodec;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.io.IO;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

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

  private ConfigGui() {
  }

  public static boolean canConfigure(Player player) {
    return player != null && (player.isOp() || player.hasPermission("adapt.configurator"));
  }

  public static void open(Player player) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.getUniqueId())) {
      open(player, "", 0);
    }
  }

  public static void open(Player player, String sectionPath) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.getUniqueId())) {
      open(player, sectionPath, 0);
    }
  }

  public static void open(Player player, String sectionPath, int page) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.getUniqueId())) {
      if (player == null) {
        return;
      }

      if (!canConfigure(player)) {
        Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(ConfigMessages.NO_PERMISSION));
        return;
      }

      if (!J.isPrimaryThread()) {
        String path = sectionPath;
        int targetPage = page;
        J.runEntity(player, () -> open(player, path, targetPage));
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
        Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(
            ConfigMessages.UNABLE_OPEN_SECTION,
            untrusted("path", safePath)
        ));
        return;
      }

      List<FieldEntry> entries = buildEntries(safePath, target.sectionObject(), target.sourceTag());
      openFieldEntries(player, safePath, entries, page);
    }
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
    return ConfigGuiValueCodec.parseInputValue(type, raw);
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
          Adapt.messagePlayer(actor, C.RED + AdaptLanguage.text(ConfigMessages.FAILED_SET_VALUE, untrusted("path", path)));
        }
        return false;
      }

      Object before = readPathValue(target.rootObject(), target.objectPath());
      String beforeToml = TomlCodec.toToml(target.rootObject(), target.sourceTag());

      if (!setPathValue(target.rootObject(), target.objectPath(), value, true)) {
        if (actor != null) {
          Adapt.messagePlayer(actor, C.RED + AdaptLanguage.text(ConfigMessages.FAILED_SET_VALUE, untrusted("path", path)));
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
          Adapt.messagePlayer(actor, C.RED + AdaptLanguage.text(
              ConfigMessages.FAILED_PERSIST_UPDATE,
              untrusted("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
          ));
        }
        return false;
      }

      if (!target.reload().getAsBoolean()) {
        J.attempt(() -> IO.writeAll(target.file(), beforeToml));
        target.reload().getAsBoolean();
        if (actor != null) {
          Adapt.messagePlayer(actor, C.RED + AdaptLanguage.text(ConfigMessages.RELOAD_REVERTED));
        }
        return false;
      }

      target.afterReload().run();
      if (actor != null) {
        Adapt.messagePlayer(actor, C.GREEN + AdaptLanguage.text(
            ConfigMessages.UPDATED_VALUE,
            untrusted("path", path),
            untrusted("before", summarizeValue(before)),
            untrusted("after", summarizeValue(value))
        ));
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
    return ConfigGuiValueCodec.typeName(type);
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

  private static Element createElementForEntry(Player player, String sectionPath, int currentPage, FieldEntry entry) {
    Material material = materialFor(entry);
    String typePrefix = switch (entry.descriptor().kind()) {
      case BOOLEAN -> C.GREEN + "[" + AdaptLanguage.text(ConfigMessages.TYPE_BOOLEAN) + "] ";
      case NUMBER -> C.AQUA + "[" + AdaptLanguage.text(ConfigMessages.TYPE_NUMBER) + "] ";
      case STRING -> C.YELLOW + "[" + AdaptLanguage.text(ConfigMessages.TYPE_TEXT) + "] ";
      case ENUM -> C.LIGHT_PURPLE + "[" + AdaptLanguage.text(ConfigMessages.TYPE_ENUM) + "] ";
      case SECTION -> C.BLUE + "[" + AdaptLanguage.text(ConfigMessages.TYPE_SECTION) + "] ";
      case MAP -> C.GOLD + "[" + AdaptLanguage.text(ConfigMessages.TYPE_MAP) + "] ";
      case LIST -> C.GOLD + "[" + AdaptLanguage.text(ConfigMessages.TYPE_LIST) + "] ";
      case UNSUPPORTED -> C.RED + "[" + AdaptLanguage.text(ConfigMessages.TYPE_UNSUPPORTED) + "] ";
    };
    String name = displayName(entry.field().getName());
    String value = summarizeValue(entry.value());

    Element element = new UIElement("cfg-" + entry.path())
        .setMaterial(new MaterialBlock(material))
        .setName(typePrefix + C.WHITE + name);
    element.addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.VALUE, untrusted("value", value)));
    element.addLore(C.DARK_GRAY + AdaptLanguage.text(ConfigMessages.PATH, untrusted("path", entry.path())));
    element.setProgress(1D);
    if (entry.descriptor().kind() == ElementKind.BOOLEAN && Boolean.TRUE.equals(entry.value())) {
      element.setEnchanted(true);
    }

    if (entry.descriptor().kind() == ElementKind.SECTION && entry.value() != null) {
      int nested = getSerializableFields(entry.value().getClass()).size();
      element.addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.CONTAINS_SETTINGS, trusted("count", nested)));
      element.addLore(C.DARK_GRAY + AdaptLanguage.text(
          ConfigMessages.CATEGORY,
          untrusted("category", sectionCategory(entry.field().getName()))
      ));
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

    ElementKind kind = entry.descriptor().kind();
    if (kind == ElementKind.BOOLEAN) {
      element.addLore(Boolean.TRUE.equals(entry.value())
          ? C.GREEN + AdaptLanguage.text(ConfigMessages.STATE_ENABLED)
          : C.RED + AdaptLanguage.text(ConfigMessages.STATE_DISABLED));
      element.addLore(C.GREEN + AdaptLanguage.text(ConfigMessages.LEFT_CLICK_TOGGLE));
      element.onLeftClick((e) -> {
        boolean toggled = !Boolean.TRUE.equals(entry.value());
        confirmAndApply(player, sectionPath, currentPage, entry.path(), toggled);
      });
    } else if (kind == ElementKind.ENUM) {
      element.addLore(C.GREEN + AdaptLanguage.text(ConfigMessages.LEFT_CLICK_NEXT_VALUE));
      element.addLore(C.GREEN + AdaptLanguage.text(ConfigMessages.RIGHT_CLICK_PREVIOUS_VALUE));
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
    } else if (kind == ElementKind.NUMBER || kind == ElementKind.STRING) {
      element.addLore(C.YELLOW + AdaptLanguage.text(ConfigMessages.LEFT_CLICK_EDIT_CHAT));
      element.onLeftClick((e) -> {
        ConfigInputSVC service = Adapt.service(ConfigInputSVC.class);
        if (service == null) {
          Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(ConfigMessages.INPUT_UNAVAILABLE));
          return;
        }

        service.beginSession(player, entry.path(), sectionPath, currentPage, entry.field().getType(), displayName(entry.field().getName()));
      });
    } else if (kind == ElementKind.SECTION) {
      element.addLore(C.GREEN + AdaptLanguage.text(ConfigMessages.LEFT_CLICK_OPEN_SECTION));
      element.onLeftClick((e) -> navigateTo(player, entry.path(), 0));
    } else if (kind == ElementKind.MAP || kind == ElementKind.LIST) {
      element.addLore(C.RED + AdaptLanguage.text(ConfigMessages.READ_ONLY));
    } else if (kind == ElementKind.UNSUPPORTED) {
      element.addLore(C.RED + AdaptLanguage.text(ConfigMessages.UNSUPPORTED_TYPE));
    }

    return element;
  }

  private static Material materialFor(FieldEntry entry) {
    return switch (entry.descriptor().kind()) {
      case BOOLEAN ->
          Boolean.TRUE.equals(entry.value()) ? Material.LIME_DYE : Material.GRAY_DYE;
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
      return AdaptLanguage.text(ConfigMessages.CATEGORY_UI);
    }
    if (key.contains("sound") || key.contains("audio")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_AUDIO);
    }
    if (key.contains("lang") || key.contains("locale") || key.contains("translation")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_LOCALIZATION);
    }
    if (key.contains("sql") || key.contains("database") || key.contains("storage") || key.contains("mysql")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_STORAGE);
    }
    if (key.contains("xp") || key.contains("level") || key.contains("knowledge") || key.contains("power")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_PROGRESSION);
    }
    if (key.contains("world") || key.contains("biome") || key.contains("dimension") || key.contains("region")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_WORLD);
    }
    if (key.contains("thread") || key.contains("tick") || key.contains("async") || key.contains("performance") || key.contains("cache")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_PERFORMANCE);
    }
    if (key.contains("permission") || key.contains("blacklist") || key.contains("whitelist") || key.contains("security")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_ACCESS);
    }
    if (key.contains("debug") || key.contains("dev") || key.contains("test") || key.contains("verbose")) {
      return AdaptLanguage.text(ConfigMessages.CATEGORY_DEBUG);
    }
    return AdaptLanguage.text(ConfigMessages.CATEGORY_GENERAL);
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

    UIWindow w = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(w, tagForSection(safePath));
    w.setViewportHeight(plan.rows());

    if (entries.isEmpty()) {
      w.setElement(0, 0, new UIElement("cfg-empty")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.GRAY + AdaptLanguage.text(ConfigMessages.NO_SETTINGS)));
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
    if (AdaptConfig.get().isGuiBackButton() && !safePath.isBlank()) {
      String parent = parentPath(safePath);
      w.setElement(0, navRow, new UIElement("cfg-back")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.BACK))
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
    w.setTitle(C.GRAY + AdaptLanguage.text(ConfigMessages.CONFIGURE_PATH, untrusted("path", titlePath)));
    w.onClosed((closed) -> onGuiClosed(player, w, safePath));
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
            AdaptLanguage.text(ConfigMessages.OPEN_SETTINGS, trusted("count", nested))
        ));
      } else {
        generalValues++;
      }
    }

    if (generalValues > 0) {
      entries.add(new SectionIndexEntry(
          ROOT_CORE_GENERAL,
          AdaptLanguage.text(ConfigMessages.GENERAL_SETTINGS),
          Material.COMPARATOR,
          AdaptLanguage.text(ConfigMessages.OPEN_GLOBAL_OPTIONS, trusted("count", generalValues))
      ));
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(player, ROOT_CORE, page, AdaptLanguage.text(
        ConfigMessages.CONFIGURE_PATH,
        trusted("path", AdaptLanguage.text(ConfigMessages.CORE))
    ), entries);
  }

  private static void openCoreGeneral(Player player, int page) {
    openFieldEntries(player, ROOT_CORE_GENERAL, buildCoreGeneralEntries(), page);
  }

  private static void openRoot(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    entries.add(new SectionIndexEntry(
        ROOT_ADAPTATIONS_SKILLS,
        AdaptLanguage.text(ConfigMessages.ADAPTATIONS),
        Material.NETHER_STAR,
        AdaptLanguage.text(ConfigMessages.CONFIGURE_ADAPTATIONS)
    ));
    entries.add(new SectionIndexEntry(
        ROOT_CORE,
        AdaptLanguage.text(ConfigMessages.CORE),
        Material.COMPARATOR,
        AdaptLanguage.text(ConfigMessages.CONFIGURE_CORE)
    ));
    entries.add(new SectionIndexEntry(
        ROOT_SKILLS,
        AdaptLanguage.text(ConfigMessages.SKILLS),
        Material.ENCHANTED_BOOK,
        AdaptLanguage.text(ConfigMessages.CONFIGURE_SKILLS)
    ));
    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(player, "", page, AdaptLanguage.text(ConfigMessages.CONFIGURE_ADAPT), entries);
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
          ColorFormatter.stripColor(skill.getDisplayName()),
          skill.getIcon(),
          AdaptLanguage.text(ConfigMessages.CONFIGURE_TARGET, untrusted("target", skill.getName()))
      ));
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(player, ROOT_SKILLS, page, AdaptLanguage.text(
        ConfigMessages.CONFIGURE_PATH,
        trusted("path", AdaptLanguage.text(ConfigMessages.SKILLS))
    ), entries);
  }

  private static void openAdaptationSkillIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    entries.add(new SectionIndexEntry(
        ROOT_ADAPTATIONS_ALL,
        AdaptLanguage.text(ConfigMessages.ALL_ADAPTATIONS),
        Material.NETHER_STAR,
        AdaptLanguage.text(ConfigMessages.BROWSE_ALL_ADAPTATIONS)
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
          ColorFormatter.stripColor(skill.getDisplayName()),
          skill.getIcon(),
          AdaptLanguage.text(ConfigMessages.BROWSE_ADAPTATIONS, trusted("count", adaptationCount))
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
    openSectionIndex(player, ROOT_ADAPTATIONS_SKILLS, page, AdaptLanguage.text(
        ConfigMessages.CONFIGURE_PATH,
        trusted("path", AdaptLanguage.text(ConfigMessages.ADAPTATIONS))
    ), head);
  }

  private static void openAdaptationIndexForSkill(Player player, String skillName, int page) {
    Skill<?> skill = resolveSkill(skillName);
    if (skill == null) {
      Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(ConfigMessages.UNKNOWN_SKILL, untrusted("skill", skillName)));
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
          ColorFormatter.stripColor(adaptation.getDisplayName()),
          adaptation.getIcon(),
          AdaptLanguage.text(ConfigMessages.OPEN_TARGET, untrusted("target", adaptation.getName()))
      ));
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(player, ROOT_ADAPTATIONS_SKILLS + "." + skill.getName(), page, AdaptLanguage.text(
        ConfigMessages.CONFIGURE_PATH,
        untrusted("path", ColorFormatter.stripColor(skill.getDisplayName()))
    ), entries);
  }

  private static void openAdaptationIndex(Player player, int page) {
    List<SectionIndexEntry> entries = new ArrayList<>();
    for (Adaptation<?> adaptation : getLoadedAdaptations()) {
      if (adaptation == null || adaptation.getSkill() == null) {
        continue;
      }

      entries.add(new SectionIndexEntry(
          ROOT_ADAPTATIONS + "." + adaptation.getName(),
          ColorFormatter.stripColor(adaptation.getDisplayName()),
          adaptation.getIcon(),
          AdaptLanguage.text(ConfigMessages.SKILL_TARGET, untrusted("skill", adaptation.getSkill().getName()))
      ));
    }

    entries.sort(Comparator.comparing(e -> normalizeSortKey(e.displayName())));
    openSectionIndex(player, ROOT_ADAPTATIONS_ALL, page, AdaptLanguage.text(
        ConfigMessages.CONFIGURE_PATH,
        trusted("path", AdaptLanguage.text(ConfigMessages.ALL_ADAPTATIONS))
    ), entries);
  }

  private static void openSectionIndex(Player player, String sectionPath, int page, String title, List<SectionIndexEntry> entries) {
    String safePath = normalizePath(sectionPath);
    GuiLayout.PagePlan plan = configPagePlan(entries.size());
    int currentPage = GuiLayout.clampPage(page, plan.pageCount());
    int start = currentPage * plan.itemsPerPage();
    int end = Math.min(entries.size(), start + plan.itemsPerPage());

    UIWindow w = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(w, tagForSection(safePath));
    w.setViewportHeight(plan.rows());

    if (entries.isEmpty()) {
      w.setElement(0, 0, new UIElement("cfg-empty")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.GRAY + AdaptLanguage.text(ConfigMessages.NO_ENTRIES)));
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
              .addLore(C.DARK_GRAY + AdaptLanguage.text(ConfigMessages.PATH, untrusted("path", entry.path())))
              .setProgress(1D)
              .onLeftClick((e) -> navigateTo(player, entry.path(), 0));
          reveal.add(new GuiEffects.Placement(pos, row, element));
        }
      }
      GuiEffects.applyReveal(w, reveal);
    }

    int navRow = plan.rows() - 1;
    applyPageControls(w, player, safePath, navRow, currentPage, plan.pageCount(), entries.size(), start, end);
    if (AdaptConfig.get().isGuiBackButton() && !safePath.isBlank()) {
      w.setElement(0, navRow, new UIElement("cfg-back")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.BACK))
          .onLeftClick((e) -> navigateTo(player, parentPath(safePath), 0)));
    }
    addIndexOverview(w, navRow, safePath, entries.size(), currentPage, plan.pageCount(), title);

    w.setTitle(C.GRAY + title);
    w.onClosed((closed) -> onGuiClosed(player, w, safePath));
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
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.LEFT_CLICK_PREVIOUS))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_BACK, trusted("pages", PAGE_JUMP)))
          .onLeftClick((e) -> navigateTo(player, safePath, currentPage - 1))
          .onRightClick((e) -> navigateTo(player, safePath, jumpBack)));
      window.setElement(-3, navRow, new UIElement("cfg-first")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.FIRST))
          .onLeftClick((e) -> navigateTo(player, safePath, 0)));
    }

    if (currentPage < pageCount - 1) {
      window.setElement(4, navRow, new UIElement("cfg-next")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.LEFT_CLICK_NEXT))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_FORWARD, trusted("pages", PAGE_JUMP)))
          .onLeftClick((e) -> navigateTo(player, safePath, currentPage + 1))
          .onRightClick((e) -> navigateTo(player, safePath, jumpForward)));
      window.setElement(3, navRow, new UIElement("cfg-last")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.LAST))
          .onLeftClick((e) -> navigateTo(player, safePath, pageCount - 1)));
    }

    int from = totalEntries <= 0 ? 0 : (start + 1);
    int to = totalEntries <= 0 ? 0 : end;
    window.setElement(-1, navRow, new UIElement("cfg-page-info")
        .setMaterial(new MaterialBlock(Material.PAPER))
        .setName(C.AQUA + AdaptLanguage.text(
            GuiMessages.PAGE_OF,
            trusted("page", currentPage + 1),
            trusted("pages", pageCount)
        ))
        .addLore(C.GRAY + AdaptLanguage.text(
            GuiMessages.SHOWING_RANGE,
            trusted("from", from),
            trusted("to", to),
            trusted("total", totalEntries)
        ))
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

    String safePath = path == null || path.isBlank() ? AdaptLanguage.text(ConfigMessages.ROOT) : path;
    window.setElement(1, navRow, new UIElement("cfg-overview")
        .setMaterial(new MaterialBlock(Material.BOOK))
        .setName(C.AQUA + AdaptLanguage.text(ConfigMessages.OVERVIEW))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.PATH, untrusted("path", safePath)))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.SECTIONS_COUNT, trusted("count", sections)))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.EDITABLE_COUNT, trusted("count", editable)))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.ENTRIES_COUNT, trusted("count", entries.size())))
        .setProgress(1D));

    window.setElement(2, navRow, new UIElement("cfg-help")
        .setMaterial(new MaterialBlock(Material.KNOWLEDGE_BOOK))
        .setName(C.GRAY + AdaptLanguage.text(ConfigMessages.HELP))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.HELP_LEFT_CLICK))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.HELP_RIGHT_CLICK))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.HELP_ESCAPE))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(
            GuiMessages.PAGE_OF,
            trusted("page", currentPage + 1),
            trusted("pages", pageCount)
        ))
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
    String safePath = path == null || path.isBlank() ? AdaptLanguage.text(ConfigMessages.ROOT) : path;
    window.setElement(1, navRow, new UIElement("cfg-index-overview")
        .setMaterial(new MaterialBlock(Material.BOOK))
        .setName(C.AQUA + AdaptLanguage.text(ConfigMessages.DIRECTORY))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.PATH, untrusted("path", safePath)))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.ENTRIES_COUNT, trusted("count", totalEntries)))
        .addLore(C.GRAY + AdaptLanguage.text(
            ConfigMessages.PAGE,
            trusted("page", currentPage + 1),
            trusted("pages", pageCount)
        ))
        .addLore(C.DARK_GRAY + title)
        .setProgress(1D));

    window.setElement(2, navRow, new UIElement("cfg-index-help")
        .setMaterial(new MaterialBlock(Material.KNOWLEDGE_BOOK))
        .setName(C.GRAY + AdaptLanguage.text(ConfigMessages.NAVIGATION))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.NAV_OPEN_SECTION))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.NAV_JUMP_PAGES))
        .addLore(C.GRAY + AdaptLanguage.text(ConfigMessages.HELP_ESCAPE))
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
      } catch (NoSuchFieldException ex) {
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
    } catch (Throwable ex) {
      Adapt.verbose("Failed to instantiate config type " + normalized.getName() + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return null;
    }
  }

  private static boolean setFieldValue(Field field, Object target, Object value) {
    try {
      field.setAccessible(true);
      field.set(target, value);
      return true;
    } catch (Throwable ex) {
      Adapt.verbose("Failed to set field '" + field.getName() + "' on "
          + (target == null ? "null" : target.getClass().getName()) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return false;
    }
  }

  private static Object getFieldValue(Field field, Object target) {
    try {
      field.setAccessible(true);
      return field.get(target);
    } catch (Throwable ex) {
      Adapt.verbose("Failed to read field '" + field.getName() + "' on "
          + (target == null ? "null" : target.getClass().getName()) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return null;
    }
  }

  private static Object coerceValue(Object value, Class<?> targetType) {
    return ConfigGuiValueCodec.coerceValue(value, targetType);
  }

  private static Class<?> normalizeType(Class<?> type) {
    return ConfigGuiValueCodec.normalizeType(type);
  }

  private static boolean isNumericType(Class<?> type) {
    return ConfigGuiValueCodec.isNumericType(type);
  }

  private static boolean isSectionType(Class<?> type) {
    return ConfigGuiValueCodec.isSectionType(type);
  }

  private static Object cycleEnum(Class<?> enumType, Object current, int direction) {
    return ConfigGuiValueCodec.cycleEnum(enumType, current, direction);
  }

  private static Object parseEnumConstant(Class<?> enumType, String value) {
    return ConfigGuiValueCodec.parseEnumConstant(enumType, value);
  }

  private static String enumConstants(Class<?> enumType) {
    return ConfigGuiValueCodec.enumConstants(enumType);
  }

  private static String displayName(String key) {
    if (key == null || key.isBlank()) {
      return AdaptLanguage.text(ConfigMessages.UNNAMED);
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
      return AdaptLanguage.text(ConfigMessages.NULL);
    }

    if (value instanceof Map<?, ?> map) {
      return AdaptLanguage.text(ConfigMessages.MAP_SIZE, trusted("count", map.size()));
    }
    if (value instanceof Collection<?> collection) {
      return AdaptLanguage.text(ConfigMessages.LIST_SIZE, trusted("count", collection.size()));
    }
    if (value.getClass().isArray()) {
      return AdaptLanguage.text(ConfigMessages.ARRAY);
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
    AdaptLanguage.reload();
    AdaptLanguage.requestConfiguredLocale();

    if (AdaptConfig.get().isCustomModels()) {
      CustomModel.reloadFromDisk();
    } else {
      CustomModel.clear();
    }
    synchronizeAdvancementRuntime();
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
          Adapt.instance.getDataFile("adapt.toml"),
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
          Adapt.instance.getDataFile("skills", skill.getName() + ".toml"),
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
      if (adaptation == null || adaptation.getConfig() == null || !(adaptation instanceof SimpleAdaptation<?>)) {
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
          Adapt.instance.getDataFile("adaptations", adaptation.getName() + ".toml"),
          () -> Adapt.instance.getAdaptServer().getSkillRegistry().hotReloadAdaptationConfig(adaptation.getName()),
          () -> {
          }
      );
    }

    return new EditTarget(
        SOURCE_TAG_CORE,
        AdaptConfig.get(),
        normalized,
        Adapt.instance.getDataFile("adapt.toml"),
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

  private static void synchronizeAdvancementRuntime() {
    if (Adapt.instance != null && Adapt.instance.getAdaptServer() != null) {
      Adapt.instance.getAdaptServer().getSkillRegistry().synchronizeAdvancementRuntime();
    }
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
    skills.sort(Comparator.comparing(skill -> normalizeSortKey(ColorFormatter.stripColor(skill.getDisplayName()))));
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

    adaptations.sort(Comparator.comparing(adaptation -> normalizeSortKey(ColorFormatter.stripColor(adaptation.getDisplayName()))));
    return adaptations;
  }

  private static String normalizeSortKey(String value) {
    if (value == null) {
      return "";
    }

    String normalized = ColorFormatter.stripColor(value).toLowerCase(Locale.ROOT).trim();
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
    open(player, path, page);
  }

  private static void onGuiClosed(Player player, UIWindow window, String currentPath) {
    if (player == null) {
      return;
    }

    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window);
    if (GuiCloseSuppression.consume(player)) {
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
    J.runEntity(player, () -> {
      if (player.isOnline() && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
        open(player, parent, 0);
      }
    }, 1);
  }

  public record ParseResult(boolean success, Object value, String error) {
    public static ParseResult ok(Object value) {
      return new ParseResult(true, value, "");
    }

    public static ParseResult fail(String error) {
      return new ParseResult(false, null, error == null ? AdaptLanguage.text(ConfigMessages.INVALID_VALUE) : error);
    }
  }

}
