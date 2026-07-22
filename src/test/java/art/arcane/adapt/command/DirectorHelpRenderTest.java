package art.arcane.adapt.command;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.director.DirectorSystem;
import art.arcane.adapt.util.director.handlers.AdaptationListHandler;
import art.arcane.adapt.util.director.handlers.AdaptationProviderHandler;
import art.arcane.adapt.util.director.handlers.AdaptationSkillListHandler;
import art.arcane.adapt.util.director.handlers.BlockVectorHandler;
import art.arcane.adapt.util.director.handlers.BooleanHandler;
import art.arcane.adapt.util.director.handlers.ByteHandler;
import art.arcane.adapt.util.director.handlers.DoubleHandler;
import art.arcane.adapt.util.director.handlers.FloatHandler;
import art.arcane.adapt.util.director.handlers.IntegerHandler;
import art.arcane.adapt.util.director.handlers.LongHandler;
import art.arcane.adapt.util.director.handlers.OptionalWorldHandler;
import art.arcane.adapt.util.director.handlers.ParticleHandler;
import art.arcane.adapt.util.director.handlers.PlayerHandler;
import art.arcane.adapt.util.director.handlers.ShortHandler;
import art.arcane.adapt.util.director.handlers.SkillProviderHandler;
import art.arcane.adapt.util.director.handlers.SoundHandler;
import art.arcane.adapt.util.director.handlers.StringHandler;
import art.arcane.adapt.util.director.handlers.VectorHandler;
import art.arcane.adapt.util.director.handlers.WorldHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu.DirectorHelpPage;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectorHelpRenderTest extends AdaptTestBase {

  private static final List<String> FORBIDDEN_PLAIN_TOKENS = List.of(
      "show_text", "hover:", "click:", "run_command", "suggest_command", "gradient", "font:"
  );

  private static final List<String> FORBIDDEN_HOVER_TOKENS = List.of(
      "\\<", "\\>", "show_text", "hover:", "click:", "run_command", "suggest_command"
  );

  private static final Pattern HOVER_ATTR = Pattern.compile("show_text:'((?:[^'\\\\]|\\\\.)*)'");

  private static final List<String> ADVENTURE_MARKER_RESOURCES = List.of(
      "net/kyori/adventure/util/Services.class",
      "net/kyori/adventure/key/Key.class",
      "net/kyori/adventure/nbt/BinaryTag.class",
      "net/kyori/adventure/text/minimessage/MiniMessage.class",
      "net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer.class",
      "net/kyori/option/Option.class"
  );

  private static final class IsolatedAdventure implements AutoCloseable {
    private final URLClassLoader loader;
    private final Object miniMessage;
    private final Method deserialize;
    private final Object plainSerializer;
    private final Method serializePlain;

    private IsolatedAdventure() throws Exception {
      this.loader = new URLClassLoader(resolveAdventureJars(), ClassLoader.getPlatformClassLoader());
      Class<?> miniMessageClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage", true, this.loader);
      this.miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
      Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component", true, this.loader);
      Class<?> componentSerializerClass = Class.forName("net.kyori.adventure.text.serializer.ComponentSerializer", true, this.loader);
      this.deserialize = componentSerializerClass.getMethod("deserialize", Object.class);
      Class<?> plainClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer", true, this.loader);
      this.plainSerializer = plainClass.getMethod("plainText").invoke(null);
      this.serializePlain = componentSerializerClass.getMethod("serialize", componentClass);
    }

    private static URL[] resolveAdventureJars() throws IOException {
      ClassLoader testLoader = DirectorHelpRenderTest.class.getClassLoader();
      LinkedHashSet<URL> jars = new LinkedHashSet<>();
      for (String marker : ADVENTURE_MARKER_RESOURCES) {
        Enumeration<URL> resources = testLoader.getResources(marker);
        while (resources.hasMoreElements()) {
          URL resource = resources.nextElement();
          String text = resource.toString();
          if (!text.startsWith("jar:") || !text.contains("/net.kyori/")) {
            continue;
          }
          String jarLocation = text.substring("jar:".length(), text.indexOf("!/"));
          jars.add(URI.create(jarLocation).toURL());
        }
      }
      if (jars.isEmpty()) {
        throw new IllegalStateException("No net.kyori adventure jars found on the test classpath");
      }
      return jars.toArray(new URL[0]);
    }

    private Object parse(String miniMessageInput) throws Throwable {
      try {
        return this.deserialize.invoke(this.miniMessage, miniMessageInput);
      } catch (InvocationTargetException wrapped) {
        throw wrapped.getCause();
      }
    }

    private String plain(Object component) throws Throwable {
      try {
        return (String) this.serializePlain.invoke(this.plainSerializer, component);
      } catch (InvocationTargetException wrapped) {
        throw wrapped.getCause();
      }
    }

    @Override
    public void close() throws IOException {
      this.loader.close();
    }
  }

  @Test
  public void adaptHelp_everyNodeLine_isCleanSingleLineMiniMessage() throws Exception {
    StringBuilder notes = new StringBuilder();
    KList<DirectorParameterHandler<?>> handlers = resolveHandlers(notes);

    DirectorRuntimeEngine engine = DirectorEngineFactory.create(
        new CommandAdapt(),
        DirectorEngineOptions.builder()
            .contexts(new DirectorContextRegistry())
            .dispatcher((DirectorExecutionMode mode, Runnable runnable) -> runnable.run())
            .legacyHandlers(handlers)
            .build()
    );

    List<String> rootOrder = new ArrayList<>();
    for (DirectorRuntimeNode child : engine.getRoot().getChildren()) {
      rootOrder.add(child.getDescriptor().getName());
    }
    System.out.println("HELP-RENDER ROOT ORDER: " + rootOrder);
    System.out.println("HELP-RENDER SETUP: " + notes);

    List<DirectorRuntimeNode> groups = new ArrayList<>();
    collectGroups(engine.getRoot(), groups);
    List<String> brokenReports = new ArrayList<>();
    int renderedLineCount = 0;

    try (IsolatedAdventure adventure = new IsolatedAdventure()) {
      for (DirectorRuntimeNode group : groups) {
        if (group.getChildren().isEmpty()) {
          continue;
        }
        DirectorHelpPage page = new DirectorHelpPage(group, List.copyOf(group.getChildren()), 0, 1);
        List<String> lines = DirectorMiniMenu.render(
            page,
            DirectorMiniMenu.Theme.adaptRed(),
            AdaptLanguage.directorResolver()
        );
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
          String rawLine = lines.get(lineIndex);
          String label = group.path() + " line " + lineIndex;
          String failure = validateLine(adventure, rawLine);
          renderedLineCount++;
          if (failure == null) {
            System.out.println("HELP-RENDER [OK]     " + label);
          } else {
            String report = label + " | failure: " + failure;
            brokenReports.add(report);
            System.out.println("HELP-RENDER [BROKEN] " + report);
          }
        }
      }
    }

    System.out.println("HELP-RENDER SUMMARY: " + renderedLineCount + " lines, " + brokenReports.size() + " broken");

    assertThat(brokenReports)
        .as("Every /adapt help line must deserialize to clean single-line MiniMessage. Broken lines:\n"
            + String.join("\n", brokenReports))
        .isEmpty();
  }

  private static void collectGroups(DirectorRuntimeNode node, List<DirectorRuntimeNode> out) {
    out.add(node);
    for (DirectorRuntimeNode child : node.getChildren()) {
      collectGroups(child, out);
    }
  }

  private static String validateLine(IsolatedAdventure adventure, String rawLine) {
    Object component;
    try {
      component = adventure.parse(rawLine);
    } catch (Throwable error) {
      return "MiniMessage.deserialize threw " + error.getClass().getName() + ": " + error.getMessage();
    }

    String plainText;
    try {
      plainText = adventure.plain(component);
    } catch (Throwable error) {
      return "PlainTextComponentSerializer.serialize threw " + error.getClass().getName() + ": " + error.getMessage();
    }

    String violation = firstViolation(plainText);
    if (violation != null) {
      return violation + " | plain-excerpt: " + excerptAround(plainText);
    }

    for (String hoverBody : extractHoverBodies(rawLine)) {
      String hoverPlain;
      try {
        hoverPlain = adventure.plain(adventure.parse(hoverBody));
      } catch (Throwable error) {
        return "hover body failed to parse: " + error.getClass().getName() + ": " + error.getMessage();
      }
      String hoverViolation = hoverViolation(hoverPlain);
      if (hoverViolation != null) {
        return hoverViolation + " | plain-excerpt: " + excerptAround(hoverPlain);
      }
    }
    return null;
  }

  private static KList<DirectorParameterHandler<?>> resolveHandlers(StringBuilder notes) {
    try {
      KList<DirectorParameterHandler<?>> system = DirectorSystem.handlers;
      if (system != null && system.isNotEmpty()) {
        notes.append("handlers=DirectorSystem.handlers size=").append(system.size());
        return system;
      }
      notes.append("DirectorSystem.handlers initialized but empty off-server; using manually instantiated production handler set");
    } catch (Throwable error) {
      notes.append("DirectorSystem.handlers static init failed off-server (")
          .append(error.getClass().getSimpleName())
          .append("); using manually instantiated production handler set");
    }

    KList<DirectorParameterHandler<?>> manual = new KList<>();
    manual.add(new AdaptationListHandler());
    manual.add(new AdaptationProviderHandler());
    manual.add(new AdaptationSkillListHandler());
    manual.add(new BlockVectorHandler());
    manual.add(new BooleanHandler());
    manual.add(new ByteHandler());
    manual.add(new DoubleHandler());
    manual.add(new FloatHandler());
    manual.add(new IntegerHandler());
    manual.add(new LongHandler());
    manual.add(new OptionalWorldHandler());
    manual.add(new ParticleHandler());
    manual.add(new PlayerHandler());
    manual.add(new ShortHandler());
    manual.add(new SkillProviderHandler());
    manual.add(new SoundHandler());
    manual.add(new StringHandler());
    manual.add(new VectorHandler());
    manual.add(new WorldHandler());
    return manual;
  }

  private static List<String> extractHoverBodies(String rawLine) {
    List<String> bodies = new ArrayList<>();
    Matcher matcher = HOVER_ATTR.matcher(rawLine);
    while (matcher.find()) {
      bodies.add(unescapeAttr(matcher.group(1)));
    }
    return bodies;
  }

  private static String unescapeAttr(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' && i + 1 < value.length() && (value.charAt(i + 1) == '\\' || value.charAt(i + 1) == '\'')) {
        out.append(value.charAt(i + 1));
        i++;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String hoverViolation(String hoverPlain) {
    for (String token : FORBIDDEN_HOVER_TOKENS) {
      if (hoverPlain.contains(token)) {
        return "hover plain text contains forbidden token \"" + token + "\" (escaping artifact or markup leak inside the hover body)";
      }
    }

    return null;
  }

  private static String firstViolation(String plainText) {
    if (plainText.contains("\n")) {
      return "visible plain text contains a newline (hover body leaked out of the hover attribute)";
    }

    for (String token : FORBIDDEN_PLAIN_TOKENS) {
      if (plainText.contains(token)) {
        return "visible plain text contains forbidden token \"" + token + "\" (markup leaked as literal text)";
      }
    }

    return null;
  }

  private static String excerptAround(String plainText) {
    int index = plainText.indexOf('\n');
    for (String token : FORBIDDEN_PLAIN_TOKENS) {
      int candidate = plainText.indexOf(token);
      if (candidate >= 0 && (index < 0 || candidate < index)) {
        index = candidate;
      }
    }

    int start = Math.max(0, index < 0 ? 0 : index - 40);
    int end = Math.min(plainText.length(), start + 220);
    String excerpt = plainText.substring(start, end).replace("\n", "\\n");
    return "\"" + excerpt + "\"";
  }

}
