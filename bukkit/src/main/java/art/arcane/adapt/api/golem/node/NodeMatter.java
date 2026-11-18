package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.data.AdaptMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The mantle slice of node records; the value format is {@link NodeCodec}. */
public class NodeMatter extends AdaptMatter<NodeFaces> {
  public NodeMatter(int width, int height, int depth) {
    super(width, height, depth, NodeFaces.class);
  }

  public NodeMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(NodeFaces faces, DataOutputStream out) throws IOException {
    NodeCodec.INSTANCE.write(faces, out);
  }

  @Override
  public NodeFaces readNode(DataInputStream in) throws IOException {
    return NodeCodec.INSTANCE.read(in);
  }
}
