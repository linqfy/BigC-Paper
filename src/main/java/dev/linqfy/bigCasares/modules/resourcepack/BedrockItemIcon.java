package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.JsonObject;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A small, deterministic orthographic icon renderer; never edits the embedded model texture. */
final class BedrockItemIcon {
    private record Face(Vector3d[] corners, int color) { }
    private static final int SIZE = 64;
    private static final String[] SIDES = {"west", "east", "down", "up", "north", "south"};
    private static final int[][] CORNERS = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

    static void render(JsonObject model, Path texturePath, Path output) throws IOException {
        var texture = ImageIO.read(texturePath.toFile());
        var faces = new ArrayList<Face>();
        Matrix4d camera = new Matrix4d().rotationY(Math.toRadians(-24));
        for (var value : model.getAsJsonArray("elements")) {
            var element = value.getAsJsonObject();
            var from = element.getAsJsonArray("from");
            var to = element.getAsJsonArray("to");
            Matrix4d transform = new Matrix4d(camera);
            if (element.has("rotation")) {
                var r = element.getAsJsonObject("rotation");
                var p = r.getAsJsonArray("origin");
                double x = p.get(0).getAsDouble(), y = p.get(1).getAsDouble(), z = p.get(2).getAsDouble();
                transform.translate(x, y, z).rotateZYX(Math.toRadians(r.get("z").getAsDouble()),
                        Math.toRadians(r.get("y").getAsDouble()), Math.toRadians(r.get("x").getAsDouble())).translate(-x, -y, -z);
            }
            Vector3d[] vertices = new Vector3d[8];
            for (int i = 0; i < 8; i++) {
                vertices[i] = transform.transformPosition(new Vector3d(
                        ((i & 4) == 0 ? from : to).get(0).getAsDouble(),
                        ((i & 2) == 0 ? from : to).get(1).getAsDouble(),
                        ((i & 1) == 0 ? from : to).get(2).getAsDouble()));
            }
            for (int side = 0; side < SIDES.length; side++) {
                var face = element.getAsJsonObject("faces").getAsJsonObject(SIDES[side]);
                if (face == null) continue;
                var uv = face.getAsJsonArray("uv");
                int u = (int) ((uv.get(0).getAsDouble() + uv.get(2).getAsDouble()) / 32 * texture.getWidth());
                int v = (int) ((uv.get(1).getAsDouble() + uv.get(3).getAsDouble()) / 32 * texture.getHeight());
                int color = texture.getRGB(Math.clamp(u, 0, texture.getWidth() - 1), Math.clamp(v, 0, texture.getHeight() - 1));
                if ((color >>> 24) == 0) continue;
                Vector3d[] corners = Arrays.stream(CORNERS[side]).mapToObj(i -> new Vector3d(vertices[i])).toArray(Vector3d[]::new);
                faces.add(new Face(corners, color));
            }
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        if (!faces.isEmpty()) draw(faces, image);
        ImageIO.write(image, "png", output.toFile());
    }

    private static void draw(List<Face> faces, BufferedImage image) {
        var points = faces.stream().flatMap(face -> Arrays.stream(face.corners())).toList();
        double minX = points.stream().mapToDouble(p -> p.x).min().orElse(0), maxX = points.stream().mapToDouble(p -> p.x).max().orElse(1);
        double minY = points.stream().mapToDouble(p -> p.y).min().orElse(0), maxY = points.stream().mapToDouble(p -> p.y).max().orElse(1);
        double scale = (SIZE - 4) / Math.max(maxX - minX, maxY - minY);
        double[] depth = new double[SIZE * SIZE];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (Face face : faces) {
            Vector3d[] v = Arrays.stream(face.corners()).map(p -> new Vector3d(
                    (p.x - (minX + maxX) / 2) * scale + SIZE / 2.0,
                    -(p.y - (minY + maxY) / 2) * scale + SIZE / 2.0, p.z)).toArray(Vector3d[]::new);
            triangle(v[0], v[1], v[2], face.color(), image, depth);
            triangle(v[0], v[2], v[3], face.color(), image, depth);
        }
    }

    private static void triangle(Vector3d a, Vector3d b, Vector3d c, int color, BufferedImage image, double[] depth) {
        double determinant = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
        if (Math.abs(determinant) < 1e-9) return;
        for (int y = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y)))); y <= Math.min(SIZE - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y)))); y++) {
            for (int x = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x)))); x <= Math.min(SIZE - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x)))); x++) {
                double u = ((b.y - c.y) * (x + .5 - c.x) + (c.x - b.x) * (y + .5 - c.y)) / determinant;
                double v = ((c.y - a.y) * (x + .5 - c.x) + (a.x - c.x) * (y + .5 - c.y)) / determinant;
                double w = 1 - u - v;
                double z = u * a.z + v * b.z + w * c.z;
                if (u >= 0 && v >= 0 && w >= 0 && z > depth[y * SIZE + x]) {
                    image.setRGB(x, y, color);
                    depth[y * SIZE + x] = z;
                }
            }
        }
    }
}
