package io.github.skystrike.shared.sdf;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * CPU representation of the Signed Distance Field for the arena geometry.
 *
 * <p>Stores a signed distance value per texel encoded in an 8-bit unsigned byte:
 * <ul>
 *   <li>Value {@code 128} represents the exact surface boundary (distance = 0.0 units).
 *   <li>Values {@code > 128} represent empty space / clearance (distance &gt; 0.0 units).
 *   <li>Values {@code < 128} represent solid interior (distance &lt; 0.0 units).
 * </ul>
 *
 * <p>Supports bilinear sampling, central-difference surface gradients, and CPU sphere-tracing.
 */
public final class SdfField {

    public static final int MAGIC = 0x53534446; // "SSDF"
    public static final int VERSION = 1;

    private final int width;
    private final int height;
    private final float texelScale;
    private final float worldWidth;
    private final float worldHeight;
    private final byte[] data;

    public SdfField(int width, int height, float texelScale, float worldWidth, float worldHeight, byte[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive: " + width + "x" + height);
        }
        if (data.length != width * height) {
            throw new IllegalArgumentException(
                    "data length " + data.length + " does not match width * height " + (width * height));
        }
        this.width = width;
        this.height = height;
        this.texelScale = texelScale;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.data = data;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float texelScale() {
        return texelScale;
    }

    public float worldWidth() {
        return worldWidth;
    }

    public float worldHeight() {
        return worldHeight;
    }

    public byte[] rawData() {
        return data;
    }

    /**
     * Decodes the signed distance in world units at texel coordinates {@code (tx, ty)}.
     * Clamps coordinates outside the field bounds to solid boundary (-127).
     */
    public float getDistance(int tx, int ty) {
        if (tx < 0 || tx >= width || ty < 0 || ty >= height) {
            return -127.0f;
        }
        int unsignedByte = data[ty * width + tx] & 0xFF;
        return (float) (unsignedByte - 128);
    }

    /**
     * Samples the signed distance at arbitrary continuous world coordinates with bilinear filtering.
     *
     * @return signed distance in world units (positive in empty space, negative inside solids).
     */
    public float sample(float worldX, float worldY) {
        if (worldX < 0f || worldX > worldWidth || worldY < 0f || worldY > worldHeight) {
            return -127.0f;
        }

        // Texel coordinates (centered on texels: continuous coordinate = world / scale - 0.5)
        float u = (worldX / texelScale) - 0.5f;
        float v = (worldY / texelScale) - 0.5f;

        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        float fx = u - x0;
        float fy = v - y0;

        float d00 = getDistance(x0, y0);
        float d10 = getDistance(x1, y0);
        float d01 = getDistance(x0, y1);
        float d11 = getDistance(x1, y1);

        float d0 = d00 * (1f - fx) + d10 * fx;
        float d1 = d01 * (1f - fx) + d11 * fx;

        return d0 * (1f - fy) + d1 * fy;
    }

    /**
     * Computes the normalized surface normal / gradient at world coordinates using central differences.
     *
     * @param out must be a float array of at least length 2. {@code out[0] = nx, out[1] = ny}.
     */
    public void gradient(float worldX, float worldY, float[] out) {
        float eps = texelScale;
        float dx = sample(worldX + eps, worldY) - sample(worldX - eps, worldY);
        float dy = sample(worldX, worldY + eps) - sample(worldX, worldY - eps);

        float len = (float) Math.hypot(dx, dy);
        if (len > 0.0001f) {
            out[0] = dx / len;
            out[1] = dy / len;
        } else {
            out[0] = 0f;
            out[1] = 1f;
        }
    }

    /**
     * Sphere-traces from start to target, computing penumbra soft shadow factor in {@code [0, 1]}.
     */
    public float raymarch(float startX, float startY, float endX, float endY, float k, int maxSteps) {
        float dirX = endX - startX;
        float dirY = endY - startY;
        float totalDist = (float) Math.hypot(dirX, dirY);
        if (totalDist < 1.0f) {
            return 1.0f;
        }

        float rayDirX = dirX / totalDist;
        float rayDirY = dirY / totalDist;
        float res = 1.0f;
        float t = 4.0f;

        for (int i = 0; i < maxSteps; i++) {
            if (t >= totalDist) {
                break;
            }
            float px = startX + rayDirX * t;
            float py = startY + rayDirY * t;
            float dist = sample(px, py);
            if (dist < 0.5f) {
                return 0.0f;
            }
            res = Math.min(res, (k * dist) / t);
            t += Math.max(dist * 0.85f, 2.0f);
        }

        return Math.max(0.0f, Math.min(1.0f, res));
    }

    /**
     * Checks if a line of sight exists by raymarching through the distance field.
     */
    public boolean hasLineOfSight(float x0, float y0, float x1, float y1, int maxSteps) {
        return raymarch(x0, y0, x1, y1, 16.0f, maxSteps) > 0.05f;
    }

    /** Writes this distance field to an output stream in the standard binary format. */
    public void write(OutputStream out) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(out));
        dataOut.writeInt(MAGIC);
        dataOut.writeInt(VERSION);
        dataOut.writeInt(width);
        dataOut.writeInt(height);
        dataOut.writeFloat(texelScale);
        dataOut.writeFloat(worldWidth);
        dataOut.writeFloat(worldHeight);
        dataOut.write(data);
        dataOut.flush();
    }

    /** Reads a distance field from a binary input stream. */
    public static SdfField read(InputStream in) throws IOException {
        DataInputStream dataIn = new DataInputStream(new BufferedInputStream(in));
        int magic = dataIn.readInt();
        if (magic != MAGIC) {
            throw new IOException("invalid SDF file magic: 0x" + Integer.toHexString(magic));
        }
        int version = dataIn.readInt();
        if (version != VERSION) {
            throw new IOException("unsupported SDF version: " + version);
        }
        int width = dataIn.readInt();
        int height = dataIn.readInt();
        float texelScale = dataIn.readFloat();
        float worldWidth = dataIn.readFloat();
        float worldHeight = dataIn.readFloat();

        byte[] data = new byte[width * height];
        dataIn.readFully(data);

        return new SdfField(width, height, texelScale, worldWidth, worldHeight, data);
    }
}
