package fr.supermax_8.buildmisc;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.function.Function;

public class Int3DMarkSetBFS {

    private final Queue<int[]> bfsQueue = new ArrayDeque<>();

    private final Function<int[], ValidatorResponse> validator;
    private final AtomicInt3DMarkSet set;
    private final int maxItrPerTick;

    private final int sizeX, sizeY, sizeZ;

    private int totalIteration = 0;
    private int[] goal = null;

    public Int3DMarkSetBFS(AtomicInt3DMarkSet set, Collection<int[]> startingPoints, Function<int[], ValidatorResponse> validator, int maxItrPerTick) {
        this.set = set;
        this.validator = validator;
        this.maxItrPerTick = maxItrPerTick;
        sizeX = set.getSizeX();
        sizeY = set.getSizeY();
        sizeZ = set.getSizeZ();
        bfsQueue.addAll(startingPoints);
    }

    public boolean tick() {
        int iterations = 0;

        while (!bfsQueue.isEmpty() && (maxItrPerTick == -1 || iterations < maxItrPerTick)) {
            int[] pos = bfsQueue.poll();
            iterations++;
            totalIteration++;

            int x = pos[0];
            int y = pos[1];
            int z = pos[2];

            // 6 neighbors
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] dir : dirs) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                // Bounds check
                if (nx < 0 || nx >= sizeX || ny < 0 || ny >= sizeY || nz < 0 || nz >= sizeZ) continue;

                // Already visited ?
                if (set.get(nx, ny, nz)) continue;

                // Optional: check if solid block

                int[] cd = {nx, ny, nz};
                ValidatorResponse response = validator.apply(cd);
                switch (response) {
                    case NON_VALIDATED -> {
                        continue;
                    }
                    case GOAL -> {
                        goal = cd;
                        return true;
                    }
                }

                // Mark and enqueue
                set.set(nx, ny, nz);
                bfsQueue.add(new int[]{nx, ny, nz});
            }
        }

        return bfsQueue.isEmpty();
    }

    public int getTotalIteration() {
        return totalIteration;
    }

    public int[] getGoal() {
        return goal;
    }

    public enum ValidatorResponse {
        VALIDATED,
        NON_VALIDATED,
        GOAL
    }

}
