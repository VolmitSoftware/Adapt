package art.arcane.adapt.client.protocol;

import art.arcane.adapt.api.notification.HudSlot;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.InsightBond;
import art.arcane.adapt.client.view.InsightEffect;
import art.arcane.adapt.client.view.InsightKind;
import art.arcane.adapt.client.view.InsightMark;
import art.arcane.adapt.client.view.InsightStat;
import art.arcane.adapt.client.view.InsightStatKind;
import art.arcane.adapt.client.view.InsightView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceGroupView;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Binary form of every frame: one opcode byte, then the fields in declaration order. Unsigned varints carry counts,
 * levels and ids; fixed big-endian ints carry values that may be negative; strings are UTF-8 with a varint length;
 * maps are written as key-sorted entry lists. Every count and length is bounded by {@link FrameLimits}.
 */
public final class FrameCodec {
  public static final int S2C_ACCEPT = 1;
  public static final int S2C_CATALOG = 2;
  public static final int S2C_PLAYER = 3;
  public static final int S2C_PLAYER_UPDATE = 4;
  public static final int S2C_OPEN_ROUTE = 5;
  public static final int S2C_PANEL_OPEN = 6;
  public static final int S2C_PANEL_UPDATE = 7;
  public static final int S2C_PANEL_CLOSE = 8;
  public static final int S2C_HUD_PUBLISH = 9;
  public static final int S2C_HUD_CLEAR = 10;
  public static final int S2C_HUD_STATUS = 11;
  public static final int S2C_XP_EARNED = 12;
  public static final int S2C_TOAST = 13;
  public static final int S2C_CUSTOM_ITEMS = 14;
  public static final int S2C_FEEDBACK = 15;
  public static final int S2C_HUD_CLEAR_ALL = 16;
  public static final int S2C_INSIGHT_TARGET = 28;
  public static final int S2C_INSIGHT_VITALS = 29;
  public static final int S2C_INSIGHT_MARKS = 30;
  public static final int S2C_INSIGHT_CLEAR = 31;
  public static final int C2S_HELLO = 32;
  public static final int C2S_LEARN = 33;
  public static final int C2S_UNLEARN = 34;
  public static final int C2S_SET_PREFERENCE = 35;
  public static final int C2S_RESET_PREFERENCE = 36;
  public static final int C2S_OPEN = 37;
  public static final int C2S_PANEL_CLICK = 38;
  public static final int C2S_PANEL_INPUT = 39;
  public static final int C2S_PANEL_CLOSED = 40;
  public static final int C2S_JUMP_KEY = 41;
  private static final int ROUTE_SKILLS = 0;
  private static final int ROUTE_SKILL = 1;
  private static final int ROUTE_ADAPTATION = 2;
  private static final int ID = FrameLimits.MAX_ID_BYTES;
  private static final int TEXT = FrameLimits.MAX_TEXT_BYTES;

  private FrameCodec() {
  }

  public static byte[] encodeClient(ClientFrame frame) throws ClientProtocolException {
    Objects.requireNonNull(frame, "frame");
    FrameWriter out = new FrameWriter(64);
    try {
      switch (frame) {
        case ClientFrame.Hello hello -> {
          out.u8(C2S_HELLO);
          out.varInt(hello.version());
          out.varInt(hello.capabilityMask());
        }
        case ClientFrame.Learn learn -> {
          out.u8(C2S_LEARN);
          out.string(learn.adaptationId(), ID);
          out.varInt(learn.level());
          out.varLong(learn.catalogRevision());
        }
        case ClientFrame.Unlearn unlearn -> {
          out.u8(C2S_UNLEARN);
          out.string(unlearn.adaptationId(), ID);
          out.varInt(unlearn.level());
          out.varLong(unlearn.catalogRevision());
        }
        case ClientFrame.SetPreference set -> {
          out.u8(C2S_SET_PREFERENCE);
          out.enumValue(set.owner());
          out.string(set.ownerId(), ID);
          out.string(set.preferenceId(), ID);
          out.string(set.value(), ID);
        }
        case ClientFrame.ResetPreference reset -> {
          out.u8(C2S_RESET_PREFERENCE);
          out.enumValue(reset.owner());
          out.string(reset.ownerId(), ID);
        }
        case ClientFrame.Open open -> {
          out.u8(C2S_OPEN);
          writeRoute(out, open.route());
        }
        case ClientFrame.PanelClick click -> {
          out.u8(C2S_PANEL_CLICK);
          out.varInt(click.sessionId());
          out.varInt(click.revision());
          out.varInt(click.slot());
          out.enumValue(click.click());
        }
        case ClientFrame.PanelInput input -> {
          out.u8(C2S_PANEL_INPUT);
          out.varInt(input.sessionId());
          out.varInt(input.revision());
          out.varInt(input.slot());
          out.string(input.text(), FrameLimits.MAX_INPUT_BYTES);
        }
        case ClientFrame.PanelClosed closed -> {
          out.u8(C2S_PANEL_CLOSED);
          out.varInt(closed.sessionId());
        }
        case ClientFrame.JumpKey jump -> {
          out.u8(C2S_JUMP_KEY);
          out.bool(jump.down());
        }
      }
    } catch (IllegalArgumentException exception) {
      throw new ClientProtocolException("cannot encode client frame: " + exception.getMessage(), exception);
    }
    return finish(out, FrameLimits.MAX_C2S_BYTES, "client");
  }

  public static ClientFrame decodeClient(byte[] frame) throws ClientProtocolException {
    Objects.requireNonNull(frame, "frame");
    checkSize(frame.length, FrameLimits.MAX_C2S_BYTES, "client");
    FrameReader in = new FrameReader(frame);
    try {
      int opcode = in.u8();
      ClientFrame decoded = switch (opcode) {
        case C2S_HELLO -> new ClientFrame.Hello(in.varInt(), in.varInt());
        case C2S_LEARN -> new ClientFrame.Learn(in.string(ID), in.varInt(), in.varLong());
        case C2S_UNLEARN -> new ClientFrame.Unlearn(in.string(ID), in.varInt(), in.varLong());
        case C2S_SET_PREFERENCE -> new ClientFrame.SetPreference(in.enumValue(PreferenceOwner.class), in.string(ID),
            in.string(ID), in.string(ID));
        case C2S_RESET_PREFERENCE -> new ClientFrame.ResetPreference(in.enumValue(PreferenceOwner.class), in.string(ID));
        case C2S_OPEN -> new ClientFrame.Open(readRoute(in));
        case C2S_PANEL_CLICK -> new ClientFrame.PanelClick(in.varInt(), in.varInt(), in.varInt(),
            in.enumValue(ClickKind.class));
        case C2S_PANEL_INPUT -> new ClientFrame.PanelInput(in.varInt(), in.varInt(), in.varInt(),
            in.string(FrameLimits.MAX_INPUT_BYTES));
        case C2S_PANEL_CLOSED -> new ClientFrame.PanelClosed(in.varInt());
        case C2S_JUMP_KEY -> new ClientFrame.JumpKey(in.bool());
        default -> throw new IllegalArgumentException("unknown client opcode " + opcode);
      };
      in.expectEnd();
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new ClientProtocolException("malformed client frame: " + exception.getMessage(), exception);
    }
  }

  public static byte[] encodeServer(ServerFrame frame) throws ClientProtocolException {
    Objects.requireNonNull(frame, "frame");
    FrameWriter out = new FrameWriter(256);
    try {
      switch (frame) {
        case ServerFrame.Accept accept -> {
          out.u8(S2C_ACCEPT);
          out.varInt(accept.version());
          out.string(accept.server().platform(), ID);
          out.string(accept.server().minecraftVersion(), ID);
          out.varInt(accept.server().capabilityMask());
        }
        case ServerFrame.Catalog catalog -> {
          out.u8(S2C_CATALOG);
          writeCatalog(out, catalog.catalog());
        }
        case ServerFrame.Player player -> {
          out.u8(S2C_PLAYER);
          writePlayer(out, player.player());
        }
        case ServerFrame.PlayerUpdate update -> {
          out.u8(S2C_PLAYER_UPDATE);
          writeDelta(out, update.delta());
        }
        case ServerFrame.OpenRoute open -> {
          out.u8(S2C_OPEN_ROUTE);
          writeRoute(out, open.route());
        }
        case ServerFrame.PanelOpen open -> {
          out.u8(S2C_PANEL_OPEN);
          writePanel(out, open.panel());
        }
        case ServerFrame.PanelUpdate update -> {
          out.u8(S2C_PANEL_UPDATE);
          writePanel(out, update.panel());
        }
        case ServerFrame.PanelClose close -> {
          out.u8(S2C_PANEL_CLOSE);
          out.varInt(close.sessionId());
        }
        case ServerFrame.HudPublish publish -> {
          out.u8(S2C_HUD_PUBLISH);
          out.string(publish.purpose(), ID);
          out.i32(publish.priority());
          out.varLong(publish.ttlMillis());
          out.list(publish.slots(), FrameLimits.MAX_HUD_SLOTS, FrameWriter::enumValue);
          out.string(publish.text(), TEXT);
        }
        case ServerFrame.HudClear clear -> {
          out.u8(S2C_HUD_CLEAR);
          out.string(clear.purpose(), ID);
        }
        case ServerFrame.HudStatus status -> {
          out.u8(S2C_HUD_STATUS);
          out.string(status.purpose(), ID);
          out.enumValue(status.glyph());
          out.string(status.skillId(), ID);
          writeIcon(out, status.icon());
          out.string(status.label(), TEXT);
          out.varLong(status.startedAtMillis());
          out.varLong(status.durationMillis());
          out.varInt(status.stacks());
          out.varInt(status.maxStacks());
          out.bool(status.lit());
        }
        case ServerFrame.HudClearAll ignored -> out.u8(S2C_HUD_CLEAR_ALL);
        case ServerFrame.XpEarned xp -> {
          out.u8(S2C_XP_EARNED);
          out.string(xp.skillId(), ID);
          out.f64(xp.amount());
          out.f64(xp.totalXp());
          out.varInt(xp.level());
          out.f64(xp.progress());
        }
        case ServerFrame.Toast toast -> {
          out.u8(S2C_TOAST);
          out.enumValue(toast.kind());
          writeIcon(out, toast.icon());
          out.string(toast.title(), TEXT);
          out.string(toast.body(), TEXT);
        }
        case ServerFrame.CustomItems items -> {
          out.u8(S2C_CUSTOM_ITEMS);
          out.list(items.items(), FrameLimits.MAX_ITEMS, FrameCodec::writeItem);
        }
        case ServerFrame.Feedback feedback -> {
          out.u8(S2C_FEEDBACK);
          out.enumValue(feedback.kind());
          out.string(feedback.text(), TEXT);
        }
        case ServerFrame.InsightTarget target -> {
          out.u8(S2C_INSIGHT_TARGET);
          writeInsight(out, target.view());
        }
        case ServerFrame.InsightVitals vitals -> {
          out.u8(S2C_INSIGHT_VITALS);
          out.i32(vitals.entityId());
          out.f64(vitals.health());
          out.f64(vitals.absorption());
        }
        case ServerFrame.InsightMarks marks -> {
          out.u8(S2C_INSIGHT_MARKS);
          out.list(marks.marks(), FrameLimits.MAX_INSIGHT_MARKS, FrameCodec::writeMark);
        }
        case ServerFrame.InsightClear ignored -> out.u8(S2C_INSIGHT_CLEAR);
      }
    } catch (IllegalArgumentException exception) {
      throw new ClientProtocolException("cannot encode server frame: " + exception.getMessage(), exception);
    }
    return finish(out, FrameLimits.MAX_S2C_BYTES, "server");
  }

  public static ServerFrame decodeServer(byte[] frame) throws ClientProtocolException {
    Objects.requireNonNull(frame, "frame");
    checkSize(frame.length, FrameLimits.MAX_S2C_BYTES, "server");
    FrameReader in = new FrameReader(frame);
    try {
      int opcode = in.u8();
      ServerFrame decoded = switch (opcode) {
        case S2C_ACCEPT -> new ServerFrame.Accept(in.varInt(), new ServerInfo(in.string(ID), in.string(ID), in.varInt()));
        case S2C_CATALOG -> new ServerFrame.Catalog(readCatalog(in));
        case S2C_PLAYER -> new ServerFrame.Player(readPlayer(in));
        case S2C_PLAYER_UPDATE -> new ServerFrame.PlayerUpdate(readDelta(in));
        case S2C_OPEN_ROUTE -> new ServerFrame.OpenRoute(readRoute(in));
        case S2C_PANEL_OPEN -> new ServerFrame.PanelOpen(readPanel(in));
        case S2C_PANEL_UPDATE -> new ServerFrame.PanelUpdate(readPanel(in));
        case S2C_PANEL_CLOSE -> new ServerFrame.PanelClose(in.varInt());
        case S2C_HUD_PUBLISH -> new ServerFrame.HudPublish(in.string(ID), in.i32(), in.varLong(),
            in.list(FrameLimits.MAX_HUD_SLOTS, (FrameReader reader) -> reader.enumValue(HudSlot.class)), in.string(TEXT));
        case S2C_HUD_CLEAR -> new ServerFrame.HudClear(in.string(ID));
        case S2C_HUD_STATUS -> new ServerFrame.HudStatus(in.string(ID), in.enumValue(HudGlyph.class), in.string(ID),
            readIcon(in), in.string(TEXT), in.varLong(), in.varLong(), in.varInt(), in.varInt(), in.bool());
        case S2C_HUD_CLEAR_ALL -> new ServerFrame.HudClearAll();
        case S2C_XP_EARNED -> new ServerFrame.XpEarned(in.string(ID), in.f64(), in.f64(), in.varInt(), in.f64());
        case S2C_TOAST -> new ServerFrame.Toast(in.enumValue(ToastKind.class), readIcon(in), in.string(TEXT),
            in.string(TEXT));
        case S2C_CUSTOM_ITEMS -> new ServerFrame.CustomItems(in.list(FrameLimits.MAX_ITEMS, FrameCodec::readItem));
        case S2C_FEEDBACK -> new ServerFrame.Feedback(in.enumValue(FeedbackKind.class), in.string(TEXT));
        case S2C_INSIGHT_TARGET -> new ServerFrame.InsightTarget(readInsight(in));
        case S2C_INSIGHT_VITALS -> new ServerFrame.InsightVitals(in.i32(), in.f64(), in.f64());
        case S2C_INSIGHT_MARKS -> new ServerFrame.InsightMarks(in.list(FrameLimits.MAX_INSIGHT_MARKS, FrameCodec::readMark));
        case S2C_INSIGHT_CLEAR -> new ServerFrame.InsightClear();
        default -> throw new IllegalArgumentException("unknown server opcode " + opcode);
      };
      in.expectEnd();
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new ClientProtocolException("malformed server frame: " + exception.getMessage(), exception);
    }
  }

  private static byte[] finish(FrameWriter out, int maxBytes, String side) throws ClientProtocolException {
    checkSize(out.size(), maxBytes, side);
    return out.toByteArray();
  }

  private static void checkSize(int size, int maxBytes, String side) throws ClientProtocolException {
    if (size > maxBytes) {
      throw new ClientProtocolException(side + " frame of " + size + " bytes exceeds " + maxBytes);
    }
  }

  private static void writeRoute(FrameWriter out, Route route) {
    switch (route) {
      case Route.Skills ignored -> out.u8(ROUTE_SKILLS);
      case Route.Skill skill -> {
        out.u8(ROUTE_SKILL);
        out.string(skill.skillId(), ID);
      }
      case Route.Adaptation adaptation -> {
        out.u8(ROUTE_ADAPTATION);
        out.string(adaptation.adaptationId(), ID);
      }
    }
  }

  private static Route readRoute(FrameReader in) {
    int kind = in.u8();
    return switch (kind) {
      case ROUTE_SKILLS -> new Route.Skills();
      case ROUTE_SKILL -> new Route.Skill(in.string(ID));
      case ROUTE_ADAPTATION -> new Route.Adaptation(in.string(ID));
      default -> throw new IllegalArgumentException("unknown route " + kind);
    };
  }

  private static void writeIcon(FrameWriter out, IconRef icon) {
    out.string(icon.itemId(), ID);
    out.i32(icon.customModelData());
    out.optionalString(icon.itemModelId(), ID);
    out.optionalString(icon.spriteId(), ID);
  }

  private static IconRef readIcon(FrameReader in) {
    return new IconRef(in.string(ID), in.i32(), in.optionalString(ID), in.optionalString(ID));
  }

  private static void writeText(FrameWriter out, String value) {
    out.string(value, TEXT);
  }

  private static String readText(FrameReader in) {
    return in.string(TEXT);
  }

  private static void writeId(FrameWriter out, String value) {
    out.string(value, ID);
  }

  private static String readId(FrameReader in) {
    return in.string(ID);
  }

  private static void writeLines(FrameWriter out, List<String> lines) {
    out.list(lines, FrameLimits.MAX_LINES, FrameCodec::writeText);
  }

  private static List<String> readLines(FrameReader in) {
    return in.list(FrameLimits.MAX_LINES, FrameCodec::readText);
  }

  private static void writeCatalog(FrameWriter out, CatalogView catalog) {
    out.varLong(catalog.revision());
    out.string(catalog.locale(), ID);
    out.list(catalog.skills(), FrameLimits.MAX_SKILLS, FrameCodec::writeSkill);
  }

  private static CatalogView readCatalog(FrameReader in) {
    return new CatalogView(in.varLong(), in.string(ID), in.list(FrameLimits.MAX_SKILLS, FrameCodec::readSkill));
  }

  private static void writeSkill(FrameWriter out, SkillView skill) {
    out.string(skill.id(), ID);
    out.string(skill.name(), TEXT);
    out.string(skill.description(), TEXT);
    writeIcon(out, skill.icon());
    out.i32(skill.color());
    out.bool(skill.enabled());
    out.list(skill.rowGroups(), FrameLimits.MAX_ROW_GROUPS,
        (FrameWriter writer, List<String> row) -> writer.list(row, FrameLimits.MAX_ADAPTATIONS, FrameCodec::writeId));
    out.list(skill.preferences(), FrameLimits.MAX_PREFERENCES, FrameCodec::writePreference);
    out.list(skill.adaptations(), FrameLimits.MAX_ADAPTATIONS, FrameCodec::writeAdaptation);
  }

  private static SkillView readSkill(FrameReader in) {
    return new SkillView(
        in.string(ID),
        in.string(TEXT),
        in.string(TEXT),
        readIcon(in),
        in.i32(),
        in.bool(),
        in.list(FrameLimits.MAX_ROW_GROUPS, (FrameReader reader) -> reader.list(FrameLimits.MAX_ADAPTATIONS, FrameCodec::readId)),
        in.list(FrameLimits.MAX_PREFERENCES, FrameCodec::readPreference),
        in.list(FrameLimits.MAX_ADAPTATIONS, FrameCodec::readAdaptation)
    );
  }

  private static void writeAdaptation(FrameWriter out, AdaptationView adaptation) {
    out.string(adaptation.id(), ID);
    out.string(adaptation.skillId(), ID);
    out.string(adaptation.name(), TEXT);
    out.string(adaptation.description(), TEXT);
    writeIcon(out, adaptation.icon());
    out.bool(adaptation.enabled());
    out.varInt(adaptation.maxLevel());
    out.bool(adaptation.permanent());
    out.list(adaptation.knowledgeCosts(), FrameLimits.MAX_LEVELS, FrameWriter::varInt);
    out.list(adaptation.refunds(), FrameLimits.MAX_LEVELS, FrameWriter::varInt);
    out.list(adaptation.powerCosts(), FrameLimits.MAX_LEVELS, FrameWriter::varInt);
    out.list(adaptation.requirements(), FrameLimits.MAX_REQUIREMENTS, (FrameWriter writer, RequirementView requirement) -> {
      writer.string(requirement.adaptationId(), ID);
      writer.varInt(requirement.level());
    });
    out.list(adaptation.statLines(), FrameLimits.MAX_LEVELS, FrameCodec::writeLines);
    out.list(adaptation.preferences(), FrameLimits.MAX_PREFERENCES, FrameCodec::writePreference);
    out.list(adaptation.preferenceGroups(), FrameLimits.MAX_PREFERENCE_GROUPS, FrameCodec::writePreferenceGroup);
  }

  private static AdaptationView readAdaptation(FrameReader in) {
    return new AdaptationView(
        in.string(ID),
        in.string(ID),
        in.string(TEXT),
        in.string(TEXT),
        readIcon(in),
        in.bool(),
        in.varInt(),
        in.bool(),
        in.list(FrameLimits.MAX_LEVELS, FrameReader::varInt),
        in.list(FrameLimits.MAX_LEVELS, FrameReader::varInt),
        in.list(FrameLimits.MAX_LEVELS, FrameReader::varInt),
        in.list(FrameLimits.MAX_REQUIREMENTS, (FrameReader reader) -> new RequirementView(reader.string(ID), reader.varInt())),
        in.list(FrameLimits.MAX_LEVELS, FrameCodec::readLines),
        in.list(FrameLimits.MAX_PREFERENCES, FrameCodec::readPreference),
        in.list(FrameLimits.MAX_PREFERENCE_GROUPS, FrameCodec::readPreferenceGroup)
    );
  }

  private static void writePreference(FrameWriter out, PreferenceView preference) {
    out.string(preference.id(), ID);
    out.string(preference.label(), TEXT);
    out.string(preference.defaultValue(), ID);
    out.list(preference.choices(), FrameLimits.MAX_CHOICES, (FrameWriter writer, PreferenceChoiceView choice) -> {
      writer.string(choice.value(), ID);
      writer.string(choice.label(), TEXT);
      writeIcon(writer, choice.icon());
      writer.varInt(choice.minimumLevel());
    });
    out.bool(preference.playerEditable());
  }

  private static PreferenceView readPreference(FrameReader in) {
    return new PreferenceView(
        in.string(ID),
        in.string(TEXT),
        in.string(ID),
        in.list(FrameLimits.MAX_CHOICES, (FrameReader reader) -> new PreferenceChoiceView(reader.string(ID),
            reader.string(TEXT), readIcon(reader), reader.varInt())),
        in.bool()
    );
  }

  private static void writePreferenceGroup(FrameWriter out, PreferenceGroupView group) {
    out.string(group.id(), ID);
    out.string(group.label(), TEXT);
    writeIcon(out, group.icon());
    out.list(group.entries(), FrameLimits.MAX_GROUP_ENTRIES, (FrameWriter writer, PreferenceGroupView.Entry entry) -> {
      writer.string(entry.preferenceId(), ID);
      writeIcon(writer, entry.icon());
    });
  }

  private static PreferenceGroupView readPreferenceGroup(FrameReader in) {
    return new PreferenceGroupView(in.string(ID), in.string(TEXT), readIcon(in),
        in.list(FrameLimits.MAX_GROUP_ENTRIES, (FrameReader reader) -> new PreferenceGroupView.Entry(reader.string(ID),
            readIcon(reader))));
  }

  private static void writeHeader(FrameWriter out, PlayerHeader header) {
    out.varInt(header.masterLevel());
    out.f64(header.masterProgress());
    out.varInt(header.powerUsed());
    out.varInt(header.powerMax());
    out.i64(header.wisdom());
    out.bool(header.effectsEnabled());
  }

  private static PlayerHeader readHeader(FrameReader in) {
    return new PlayerHeader(in.varInt(), in.f64(), in.varInt(), in.varInt(), in.i64(), in.bool());
  }

  private static void writeSkillLine(FrameWriter out, SkillLineView line) {
    out.string(line.skillId(), ID);
    out.f64(line.xp());
    out.varInt(line.level());
    out.f64(line.progress());
    out.f64(line.xpToNext());
    out.i64(line.knowledge());
  }

  private static SkillLineView readSkillLine(FrameReader in) {
    return new SkillLineView(in.string(ID), in.f64(), in.varInt(), in.f64(), in.f64(), in.i64());
  }

  private static void writeLevels(FrameWriter out, Map<String, Integer> levels) {
    out.list(List.copyOf(new TreeMap<>(levels).entrySet()), FrameLimits.MAX_PLAYER_ENTRIES,
        (FrameWriter writer, Map.Entry<String, Integer> entry) -> {
          writer.string(entry.getKey(), ID);
          writer.varInt(entry.getValue());
        });
  }

  private static Map<String, Integer> readLevels(FrameReader in) {
    int count = in.varInt();
    if (count > FrameLimits.MAX_PLAYER_ENTRIES) {
      throw new IllegalArgumentException("level map of " + count + " exceeds " + FrameLimits.MAX_PLAYER_ENTRIES);
    }
    Map<String, Integer> levels = new HashMap<>();
    for (int index = 0; index < count; index++) {
      String id = in.string(ID);
      if (levels.put(id, in.varInt()) != null) {
        throw new IllegalArgumentException("duplicate level for " + id);
      }
    }
    return levels;
  }

  private static void writePreferenceKey(FrameWriter out, PreferenceKey key) {
    out.enumValue(key.owner());
    out.string(key.ownerId(), ID);
    out.string(key.preferenceId(), ID);
  }

  private static PreferenceKey readPreferenceKey(FrameReader in) {
    return new PreferenceKey(in.enumValue(PreferenceOwner.class), in.string(ID), in.string(ID));
  }

  private static void writePreferenceValue(FrameWriter out, PreferenceValue value) {
    writePreferenceKey(out, value.key());
    out.string(value.value(), ID);
  }

  private static PreferenceValue readPreferenceValue(FrameReader in) {
    return new PreferenceValue(readPreferenceKey(in), in.string(ID));
  }

  private static void writePlayer(FrameWriter out, PlayerView player) {
    writeHeader(out, player.header());
    out.list(player.skills(), FrameLimits.MAX_SKILLS, FrameCodec::writeSkillLine);
    writeLevels(out, player.adaptationLevels());
    out.list(player.preferences(), FrameLimits.MAX_PLAYER_ENTRIES, FrameCodec::writePreferenceValue);
    writeAccess(out, player.access());
  }

  private static PlayerView readPlayer(FrameReader in) {
    return new PlayerView(readHeader(in), in.list(FrameLimits.MAX_SKILLS, FrameCodec::readSkillLine), readLevels(in),
        in.list(FrameLimits.MAX_PLAYER_ENTRIES, FrameCodec::readPreferenceValue), readAccess(in));
  }

  private static void writeAccess(FrameWriter out, PlayerAccess access) {
    out.bool(access.debug());
    out.bool(access.refunds());
    out.bool(access.preferences());
    out.list(access.skills(), FrameLimits.MAX_SKILLS, FrameCodec::writeId);
    out.list(List.copyOf(new TreeMap<>(access.adaptationRows()).entrySet()), FrameLimits.MAX_SKILLS,
        (FrameWriter writer, Map.Entry<String, List<List<String>>> entry) -> {
          writer.string(entry.getKey(), ID);
          writer.list(entry.getValue(), FrameLimits.MAX_ADAPTATIONS,
              (FrameWriter rowWriter, List<String> row) -> rowWriter.list(row, FrameLimits.MAX_ADAPTATIONS,
                  FrameCodec::writeId));
        });
  }

  private static PlayerAccess readAccess(FrameReader in) {
    boolean debug = in.bool();
    boolean refunds = in.bool();
    boolean preferences = in.bool();
    List<String> skills = in.list(FrameLimits.MAX_SKILLS, FrameCodec::readId);
    int count = in.varInt();
    if (count > FrameLimits.MAX_SKILLS) {
      throw new IllegalArgumentException("access map of " + count + " exceeds " + FrameLimits.MAX_SKILLS);
    }
    Map<String, List<List<String>>> rows = new HashMap<>();
    for (int index = 0; index < count; index++) {
      String skillId = in.string(ID);
      List<List<String>> skillRows = in.list(FrameLimits.MAX_ADAPTATIONS,
          (FrameReader reader) -> reader.list(FrameLimits.MAX_ADAPTATIONS, FrameCodec::readId));
      if (rows.put(skillId, skillRows) != null) {
        throw new IllegalArgumentException("duplicate access rows for " + skillId);
      }
    }
    return new PlayerAccess(debug, refunds, preferences, skills, rows);
  }

  private static void writeDelta(FrameWriter out, PlayerDelta delta) {
    out.bool(delta.header() != null);
    if (delta.header() != null) {
      writeHeader(out, delta.header());
    }
    out.list(delta.skills(), FrameLimits.MAX_SKILLS, FrameCodec::writeSkillLine);
    writeLevels(out, delta.adaptationLevels());
    out.list(delta.preferences(), FrameLimits.MAX_PLAYER_ENTRIES, FrameCodec::writePreferenceValue);
    out.list(delta.clearedPreferences(), FrameLimits.MAX_PLAYER_ENTRIES, FrameCodec::writePreferenceKey);
    out.bool(delta.access() != null);
    if (delta.access() != null) {
      writeAccess(out, delta.access());
    }
  }

  private static PlayerDelta readDelta(FrameReader in) {
    PlayerHeader header = in.bool() ? readHeader(in) : null;
    return new PlayerDelta(header, in.list(FrameLimits.MAX_SKILLS, FrameCodec::readSkillLine), readLevels(in),
        in.list(FrameLimits.MAX_PLAYER_ENTRIES, FrameCodec::readPreferenceValue),
        in.list(FrameLimits.MAX_PLAYER_ENTRIES, FrameCodec::readPreferenceKey), in.bool() ? readAccess(in) : null);
  }

  private static void writePanel(FrameWriter out, PanelView panel) {
    out.varInt(panel.sessionId());
    out.varInt(panel.revision());
    out.string(panel.title(), TEXT);
    out.u8(panel.rows());
    out.enumValue(panel.layout());
    out.bool(panel.paging() != null);
    if (panel.paging() != null) {
      out.varInt(panel.paging().page());
      out.varInt(panel.paging().pages());
      out.varInt(panel.paging().total());
    }
    out.list(panel.breadcrumb(), FrameLimits.MAX_BREADCRUMB, FrameCodec::writeText);
    out.list(panel.tiles(), FrameLimits.MAX_TILES, FrameCodec::writeTile);
  }

  private static PanelView readPanel(FrameReader in) {
    int sessionId = in.varInt();
    int revision = in.varInt();
    String title = in.string(TEXT);
    int rows = in.u8();
    PanelLayout layout = in.enumValue(PanelLayout.class);
    PanelPaging paging = in.bool() ? new PanelPaging(in.varInt(), in.varInt(), in.varInt()) : null;
    List<String> breadcrumb = in.list(FrameLimits.MAX_BREADCRUMB, FrameCodec::readText);
    return new PanelView(sessionId, revision, title, rows, layout, paging, breadcrumb,
        in.list(FrameLimits.MAX_TILES, FrameCodec::readTile));
  }

  private static void writeTile(FrameWriter out, PanelTile tile) {
    out.varInt(tile.slot());
    writeIcon(out, tile.icon());
    out.varInt(tile.amount());
    out.string(tile.name(), TEXT);
    writeLines(out, tile.lore());
    out.bool(tile.glint());
    out.bool(tile.hint() != null);
    if (tile.hint() != null) {
      writeHint(out, tile.hint());
    }
    out.enumValue(tile.role());
    out.string(tile.section(), TEXT);
    out.string(tile.value(), TEXT);
    out.bool(tile.description() != null);
    if (tile.description() != null) {
      writeLines(out, tile.description());
    }
    out.list(tile.actions(), FrameLimits.MAX_TILE_ACTIONS, (FrameWriter writer, TileAction action) -> {
      writer.enumValue(action.click());
      writer.string(action.label(), TEXT);
    });
  }

  private static PanelTile readTile(FrameReader in) {
    int slot = in.varInt();
    IconRef icon = readIcon(in);
    int amount = in.varInt();
    String name = in.string(TEXT);
    List<String> lore = readLines(in);
    boolean glint = in.bool();
    TileHint hint = in.bool() ? readHint(in) : null;
    TileRole role = in.enumValue(TileRole.class);
    String section = in.string(TEXT);
    String value = in.string(TEXT);
    List<String> description = in.bool() ? readLines(in) : null;
    List<TileAction> actions = in.list(FrameLimits.MAX_TILE_ACTIONS,
        (FrameReader reader) -> new TileAction(reader.enumValue(ClickKind.class), reader.string(TEXT)));
    return new PanelTile(slot, icon, amount, name, lore, glint, hint, role, section, value, description, actions);
  }

  private static void writeHint(FrameWriter out, TileHint hint) {
    out.enumValue(hint.kind());
    out.string(hint.value(), TEXT);
    out.bool(hint.typed());
    out.list(hint.options(), FrameLimits.MAX_CHOICES, FrameCodec::writeText);
    writeOptionalNumber(out, hint.min());
    writeOptionalNumber(out, hint.max());
    writeOptionalNumber(out, hint.step());
  }

  private static TileHint readHint(FrameReader in) {
    HintKind kind = in.enumValue(HintKind.class);
    String value = in.string(TEXT);
    boolean typed = in.bool();
    List<String> options = in.list(FrameLimits.MAX_CHOICES, FrameCodec::readText);
    return new TileHint(kind, value, typed, options, readOptionalNumber(in), readOptionalNumber(in), readOptionalNumber(in));
  }

  private static void writeOptionalNumber(FrameWriter out, Double value) {
    out.bool(value != null);
    if (value != null) {
      out.f64(value);
    }
  }

  private static Double readOptionalNumber(FrameReader in) {
    return in.bool() ? in.f64() : null;
  }

  private static void writeItem(FrameWriter out, ItemDescriptor item) {
    out.string(item.id(), ID);
    out.string(item.baseItemId(), ID);
    out.string(item.name(), TEXT);
    writeLines(out, item.lore());
    out.optionalString(item.itemModelId(), ID);
    out.i32(item.customModelData());
    out.bytes(item.components(), FrameLimits.MAX_ITEM_PAYLOAD_BYTES);
  }

  private static ItemDescriptor readItem(FrameReader in) {
    return new ItemDescriptor(in.string(ID), in.string(ID), in.string(TEXT), readLines(in), in.optionalString(ID),
        in.i32(), in.bytes(FrameLimits.MAX_ITEM_PAYLOAD_BYTES));
  }

  private static void writeInsight(FrameWriter out, InsightView view) {
    out.i32(view.entityId());
    out.string(view.typeId(), ID);
    out.string(view.name(), TEXT);
    out.enumValue(view.kind());
    out.f64(view.health());
    out.f64(view.maxHealth());
    out.f64(view.absorption());
    out.f64(view.armor());
    out.enumValue(view.bond());
    out.string(view.ownerName(), ID);
    out.bool(view.stableHand());
    out.list(view.stats(), FrameLimits.MAX_INSIGHT_STATS, (FrameWriter writer, InsightStat stat) -> {
      writer.enumValue(stat.kind());
      writer.f64(stat.value());
    });
    out.list(view.effects(), FrameLimits.MAX_INSIGHT_EFFECTS, (FrameWriter writer, InsightEffect effect) -> {
      writer.string(effect.effectId(), ID);
      writer.varInt(effect.amplifier());
      writer.i32(effect.durationTicks());
      writer.bool(effect.harmful());
    });
  }

  private static InsightView readInsight(FrameReader in) {
    return new InsightView(in.i32(), in.string(ID), in.string(TEXT), in.enumValue(InsightKind.class), in.f64(),
        in.f64(), in.f64(), in.f64(), in.enumValue(InsightBond.class), in.string(ID), in.bool(),
        in.list(FrameLimits.MAX_INSIGHT_STATS,
            (FrameReader reader) -> new InsightStat(reader.enumValue(InsightStatKind.class), reader.f64())),
        in.list(FrameLimits.MAX_INSIGHT_EFFECTS,
            (FrameReader reader) -> new InsightEffect(reader.string(ID), reader.varInt(), reader.i32(), reader.bool())));
  }

  private static void writeMark(FrameWriter out, InsightMark mark) {
    out.i32(mark.entityId());
    out.enumValue(mark.kind());
    out.f64(mark.health());
  }

  private static InsightMark readMark(FrameReader in) {
    return new InsightMark(in.i32(), in.enumValue(InsightKind.class), in.f64());
  }
}
