package art.arcane.adapt.content.matter;

import art.arcane.adapt.content.golem.node.NodeCodec;
import art.arcane.adapt.content.golem.node.NodeFaces;
import art.arcane.adapt.engine.data.AdaptMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The mantle slice {@code adapt:node} of golemancy nodes, written with the built-in node codec. */
public final class NodeFacesMatter extends AdaptMatter<NodeFaces> {
  public NodeFacesMatter(int width, int height, int depth) {
    super(width, height, depth, NodeFaces.class);
  }

  public NodeFacesMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(NodeFaces value, DataOutputStream out) throws IOException {
    NodeCodec.INSTANCE.write(value, out);
  }

  @Override
  public NodeFaces readNode(DataInputStream in) throws IOException {
    return NodeCodec.INSTANCE.read(in);
  }
}
