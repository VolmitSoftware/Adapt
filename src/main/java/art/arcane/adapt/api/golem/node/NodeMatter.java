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
  private static final String NO_CHANNEL = "";

  public NodeMatter(int width, int height, int depth) {
    super(width, height, depth, NodeFaces.class);
  }

  public NodeMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(NodeFaces faces, DataOutputStream out) throws IOException {
    out.writeByte(faces.size());
    for (NodeRecord record : faces.records()) {
      byte[] bytes = encode(record);
      out.writeUTF(record.face().name());
      out.writeInt(bytes.length);
      out.write(bytes);
    }
  }

  @Override
  public NodeFaces readNode(DataInputStream in) throws IOException {
    int count = in.readUnsignedByte();
    NodeFaces faces = NodeFaces.EMPTY;
    for (int index = 0; index < count; index++) {
      String faceName = in.readUTF();
      byte[] bytes = new byte[in.readInt()];
      in.readFully(bytes);
      try {
        faces = faces.with(decode(BlockFace.valueOf(faceName), bytes));
      } catch (IOException | RuntimeException corrupt) {
        Adapt.warn("Skipping unreadable Golemancy node on face " + faceName + "; the rest of the block's nodes are kept.", corrupt);
      }
    }
    return faces;
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
        byte[] encoded = new byte[in.readInt()];
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
