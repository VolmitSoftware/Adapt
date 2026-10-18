package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.nms.NMS;
import art.arcane.spatial.matter.slices.RawMatter;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NodeMatter extends RawMatter<NodeFaces> {
  public static final int MAX_FACES = 6;
  public static final int MAX_RECORD_BYTES = 8 * 1024 * 1024;
  private static final String NO_CHANNEL = "";

  public NodeMatter(int width, int height, int depth) {
    super(width, height, depth, NodeFaces.class);
  }

  public NodeMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(NodeFaces faces, DataOutputStream out) throws IOException {
    Map<BlockFace, byte[]> encoded = new LinkedHashMap<>(faces.size() * 2);
    for (NodeRecord record : faces.records()) {
      byte[] bytes = encode(record);
      if (bytes.length > MAX_RECORD_BYTES) {
        Adapt.error("Not saving Golemancy node " + record + ": it encodes to " + bytes.length + " bytes, more than the " + MAX_RECORD_BYTES + " byte limit.");
        continue;
      }
      encoded.put(record.face(), bytes);
    }
    out.writeByte(encoded.size());
    for (Map.Entry<BlockFace, byte[]> entry : encoded.entrySet()) {
      out.writeUTF(entry.getKey().name());
      out.writeInt(entry.getValue().length);
      out.write(entry.getValue());
    }
  }

  @Override
  public NodeFaces readNode(DataInputStream in) throws IOException {
    int count = in.readUnsignedByte();
    if (count > MAX_FACES) {
      throw rejected("Golemancy node block claims " + count + " faces; a block has at most " + MAX_FACES + ".");
    }
    NodeFaces faces = NodeFaces.EMPTY;
    for (int index = 0; index < count; index++) {
      String faceName = in.readUTF();
      int length = in.readInt();
      if (length < 0 || length > MAX_RECORD_BYTES) {
        throw rejected("Golemancy node on face " + faceName + " claims " + length + " bytes; records hold 0-" + MAX_RECORD_BYTES + ".");
      }
      byte[] bytes = new byte[length];
      in.readFully(bytes);
      try {
        faces = faces.with(decode(BlockFace.valueOf(faceName), bytes));
      } catch (IOException | RuntimeException corrupt) {
        Adapt.warn("Skipping unreadable Golemancy node on face " + faceName + "; the rest of the block's nodes are kept.", corrupt);
      }
    }
    return faces;
  }

  private static IOException rejected(String message) {
    Adapt.error(message);
    return new IOException(message);
  }

  private static byte[] encode(NodeRecord record) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(record.owner().getMostSignificantBits());
      out.writeLong(record.owner().getLeastSignificantBits());
      out.writeUTF(record.type().id());
      out.writeUTF(record.channel() == null ? NO_CHANNEL : record.channel().name());
      out.writeByte(record.priority());
      out.writeUTF(record.redstone().id());
      out.writeByte(record.radiusX());
      out.writeByte(record.radiusY());
      out.writeByte(record.radiusZ());
      out.writeBoolean(record.whitelist());
      out.writeByte(record.filterItems().size());
      for (ItemStack item : record.filterItems()) {
        byte[] encoded = NMS.serializeStack(item).getBytes(StandardCharsets.US_ASCII);
        out.writeInt(encoded.length);
        out.write(encoded);
      }
      out.writeByte(record.filterTags().size());
      for (String tag : record.filterTags()) {
        out.writeUTF(tag);
      }
      out.writeShort(record.data().size());
      for (Map.Entry<String, String> entry : record.data().entrySet()) {
        out.writeUTF(entry.getKey());
        out.writeUTF(entry.getValue());
      }
    }
    return bytes.toByteArray();
  }

  private static NodeRecord decode(BlockFace face, byte[] bytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      UUID owner = new UUID(in.readLong(), in.readLong());
      String typeId = in.readUTF();
      NodeType type = NodeType.fromId(typeId).orElseThrow(() -> new IOException("Unknown node type " + typeId));
      String channel = in.readUTF();
      int priority = in.readUnsignedByte();
      String redstoneId = in.readUTF();
      RedstoneMode redstone = RedstoneMode.fromId(redstoneId).orElseThrow(() -> new IOException("Unknown redstone mode " + redstoneId));
      int radiusX = in.readUnsignedByte();
      int radiusY = in.readUnsignedByte();
      int radiusZ = in.readUnsignedByte();
      boolean whitelist = in.readBoolean();
      int itemCount = in.readUnsignedByte();
      List<ItemStack> items = new ArrayList<>(itemCount);
      for (int index = 0; index < itemCount; index++) {
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
          throw new IOException("Filter item claims " + length + " bytes with " + in.available() + " left in the record");
        }
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        items.add(NMS.deserializeStack(new String(encoded, StandardCharsets.US_ASCII)));
      }
      int tagCount = in.readUnsignedByte();
      List<String> tags = new ArrayList<>(tagCount);
      for (int index = 0; index < tagCount; index++) {
        tags.add(in.readUTF());
      }
      int dataCount = in.readUnsignedShort();
      Map<String, String> data = new LinkedHashMap<>(dataCount * 2);
      for (int index = 0; index < dataCount; index++) {
        data.put(in.readUTF(), in.readUTF());
      }
      return NodeRecord.builder(owner, type, face)
          .channel(channel.isEmpty() ? null : DyeColor.valueOf(channel))
          .priority(priority)
          .redstone(redstone)
          .radii(radiusX, radiusY, radiusZ)
          .whitelist(whitelist)
          .filterItems(items)
          .filterTags(tags)
          .data(data)
          .build();
    }
  }
}
