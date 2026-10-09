package io.github.skystrike.shared.sdf;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;

/**
 * Fast offline/runtime Signed Distance Field baker for static arena geometry.
 *
 * <p>Rasterises solid rectangles into a discrete grid and computes Euclidean distance via an
 * 8-point Signed Sequential Euclidean Distance Transform (8SSEDT) in O(N) linear time.
 */
public final class SdfBaker {

    private static final int INF = 1_000_000_000;

    private SdfBaker() {
    }

    /**
     * Bakes the static geometry of {@code map} into a {@link SdfField} using the default scale.
     */
    public static SdfField bake(ArenaMap map) {
        return bake(map, VisionConfig.SDF_TEXEL_SCALE);
    }

    /**
     * Bakes the static geometry of {@code map} into a {@link SdfField} using the given texel scale.
     *
     * @param map arena map containing solid rectangles
     * @param texelScale world units per texel (e.g. 2.0f)
     */
    public static SdfField bake(ArenaMap map, float texelScale) {
        int width = (int) Math.round(map.width() / texelScale);
        int height = (int) Math.round(map.height() / texelScale);
        int totalTexels = width * height;

        // 1. Rasterize solid rectangles into boolean bitmask
        boolean[] solid = new boolean[totalTexels];
        for (Rect rect : map.solids()) {
            int minX = Math.max(0, (int) Math.floor(rect.left() / texelScale));
            int maxX = Math.min(width - 1, (int) Math.ceil(rect.right() / texelScale));
            int minY = Math.max(0, (int) Math.floor(rect.bottom() / texelScale));
            int maxY = Math.min(height - 1, (int) Math.ceil(rect.top() / texelScale));

            for (int y = minY; y <= maxY; y++) {
                float wy = (y + 0.5f) * texelScale;
                int rowOffset = y * width;
                for (int x = minX; x <= maxX; x++) {
                    float wx = (x + 0.5f) * texelScale;
                    if (rect.contains(wx, wy)) {
                        solid[rowOffset + x] = true;
                    }
                }
            }
        }

        // 2. Compute 8SSEDT for outer distance (distance to nearest solid)
        float[] distOut = computeEdt(solid, width, height, true);

        // 3. Compute 8SSEDT for inner distance (distance to nearest empty space)
        float[] distIn = computeEdt(solid, width, height, false);

        // 4. Combine into signed distance and encode into 8-bit bytes
        byte[] encodedData = new byte[totalTexels];
        for (int i = 0; i < totalTexels; i++) {
            float signedDist = solid[i] ? -distIn[i] : distOut[i];
            float worldDist = signedDist * texelScale;

            // Clamp to [-127, 127] and bias by +128
            int clamped = Math.round(Math.max(-127f, Math.min(127f, worldDist)));
            encodedData[i] = (byte) (clamped + 128);
        }

        return new SdfField(width, height, texelScale, map.width(), map.height(), encodedData);
    }

    /**
     * Runs 8SSEDT distance transform.
     *
     * @param solid boolean grid
     * @param targetIsSolid if true, finds distance to solid texels; if false, distance to empty texels
     * @return array of Euclidean distances in texels
     */
    private static float[] computeEdt(boolean[] solid, int width, int height, boolean targetIsSolid) {
        int total = width * height;
        int[] nearestX = new int[total];
        int[] nearestY = new int[total];
        int[] distSq = new int[total];

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowOffset + x;
                boolean isFeature = targetIsSolid ? solid[idx] : !solid[idx];
                if (isFeature) {
                    distSq[idx] = 0;
                    nearestX[idx] = x;
                    nearestY[idx] = y;
                } else {
                    distSq[idx] = INF;
                    nearestX[idx] = -1;
                    nearestY[idx] = -1;
                }
            }
        }

        // Forward pass: top-left to bottom-right
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowOffset + x;

                // Check neighbors: (-1, 0), (0, -1), (-1, -1), (1, -1)
                checkNeighbor(x, y, idx, x - 1, y, width, height, nearestX, nearestY, distSq);
                checkNeighbor(x, y, idx, x, y - 1, width, height, nearestX, nearestY, distSq);
                checkNeighbor(x, y, idx, x - 1, y - 1, width, height, nearestX, nearestY, distSq);
                checkNeighbor(x, y, idx, x + 1, y - 1, width, height, nearestX, nearestY, distSq);
            }
        }

        // Backward pass: bottom-right to top-left
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = y * width;
            for (int x = width - 1; x >= 0; x--) {
                int idx = rowOffset + x;

                // Check neighbors: (1, 0), (0, 1), (1, 1), (-1, 1)
                checkNeighbor(x, y, idx, x + 1, y, width, height, nearestX, nearestY, distSq);
                checkNeighbor(x, y, idx, x, y + 1, width, height, nearestX, nearestY, distSq);
                checkNeighbor(x, y, idx, x + 1, y + 1, width, height, nearestX, nearestY, distSq);
                checkNeighbor(x, y, idx, x - 1, y + 1, width, height, nearestX, nearestY, distSq);
            }
        }

        float[] result = new float[total];
        for (int i = 0; i < total; i++) {
            result[i] = (float) Math.sqrt(distSq[i]);
        }
        return result;
    }

    private static void checkNeighbor(
            int curX,
            int curY,
            int curIdx,
            int nx,
            int ny,
            int width,
            int height,
            int[] nearestX,
            int[] nearestY,
            int[] distSq) {
        if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
            return;
        }
        int nIdx = ny * width + nx;
        int rx = nearestX[nIdx];
        if (rx == -1) {
            return;
        }
        int ry = nearestY[nIdx];
        int dx = curX - rx;
        int dy = curY - ry;
        int d = dx * dx + dy * dy;
        if (d < distSq[curIdx]) {
            distSq[curIdx] = d;
            nearestX[curIdx] = rx;
            nearestY[curIdx] = ry;
        }
    }
}
