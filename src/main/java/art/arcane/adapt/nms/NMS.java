package art.arcane.adapt.nms;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class NMS {

    public static String serializeStack(ItemStack is) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out)){
            oos.writeObject(is);
            return Base64.getUrlEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemStack deserializeStack(String s) {
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.getUrlDecoder().decode(s));
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(in)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
