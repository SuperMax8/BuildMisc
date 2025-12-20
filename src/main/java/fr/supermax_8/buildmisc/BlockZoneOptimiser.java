package fr.supermax_8.buildmisc;

import io.papermc.paper.raytracing.RayTraceTarget;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockZoneOptimiser {

    private static final Vector[] SIX_DIRECTIONS = {
            new Vector(1, 0, 0), // +X
            new Vector(-1, 0, 0), // -X
            new Vector(0, 1, 0), // +Y
            new Vector(0, -1, 0), // -Y
            new Vector(0, 0, 1), // +Z
            new Vector(0, 0, -1)  // -Z
    };

    private static final List<Vector> DIRECTIONS_26 = generate26Directions();

    private static final List<Vector> DIRECTIONS_QUALITY = generate256Directions();

    private final int MAX_FLOODFILL = 300 * 300 * 300;
    private final int MAX_ITERATION_PER_TICK = 1_300_000;
    private final int MAX_ITERATION_OPTIMISE_PER_TICK = 5_000;

    private final int sizeX, sizeY, sizeZ;
    private final int minX, minY, minZ;
    private AtomicInt3DMarkSet visionArea, visited, visibleArea;
    private final Location baseLoc;
    private State state = State.FLOOD_FILL;
    private AtomicInt3DMarkSet.MarkedIterator iterator;
    private CuboidBlockIterator blockIterator;
    private int totalDeleted = 0;
    private double maxDistance;

    public BlockZoneOptimiser(Location baseLoc, Location loc1, Location loc2) {
        this.baseLoc = baseLoc.clone();
        minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());

        sizeX = Math.abs(loc1.getBlockX() - loc2.getBlockX()) + 1;
        sizeY = Math.abs(loc1.getBlockY() - loc2.getBlockY()) + 1;
        sizeZ = Math.abs(loc1.getBlockZ() - loc2.getBlockZ()) + 1;

        visionArea = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);
        visited = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);
        visibleArea = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);

        maxDistance = loc1.distance(loc2) + 5;

        Int3DMarkSetBFS visionBfs = new Int3DMarkSetBFS(visionArea, List.of(toLocal(baseLoc)), iii -> {
            int[] worldPos = toWorld(iii[0], iii[1], iii[2]);
            return !isSolidBlock(worldPos[0], worldPos[1], worldPos[2]);
        }, MAX_ITERATION_PER_TICK);
        Bukkit.getScheduler().runTaskTimer(BuildMisc.instance, t -> {
            World w = baseLoc.getWorld();
            switch (state) {
                case FLOOD_FILL -> {
                    if (visionBfs.tick()) state = State.COMPUTE_VISIBLE;
                    iterator = visionArea.iterator();
                }
                case COMPUTE_VISIBLE -> {
                    long t1 = System.currentTimeMillis();
                    boolean done = iterator.step(2_500, (x, y, z) -> {
                        int[] worldC = toWorld(x, y, z);
                        Location loc = new Location(w, worldC[0], worldC[1], worldC[2]);
                        for (Vector vec : DIRECTIONS_QUALITY) {
                            rayTraceVisibleBlocks(w, loc, vec);
                        }
                        return false;
                    });
                    long t2 = System.currentTimeMillis();
                    System.out.println("computeOpti tick duration: " + (t2 - t1));

                    if (done) {
                        AtomicInteger ii = new AtomicInteger();
                        visibleArea.forEachMarked((x, y, z) -> {
                            ii.incrementAndGet();
                            return false;
                        });
                        System.out.println("VisibleBlocks " + ii.get());
                        blockIterator = new CuboidBlockIterator(loc1, loc2);
                        state = State.DELETE_BLOCKS;
                    }
                }
                case DELETE_BLOCKS -> {
                    if (!blockIterator.hasNext()) {
                        System.out.println("End optimizing, total deletion: " + totalDeleted);
                        t.cancel();
                        return;
                    }
                    for (Block b : blockIterator.next(MAX_ITERATION_PER_TICK)) {
                        if (b.isEmpty()) continue;
                        int[] localC = toLocal(b.getLocation());
                        if (!visibleArea.get(localC[0], localC[1], localC[2])) {
                            b.setType(Material.AIR);
                            totalDeleted++;
                        }
                    }
                }
            }
        }, 0, 1);
    }

    public static List<Vector> generate26Directions() {
        List<Vector> dirs = new ArrayList<>(26);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    dirs.add(new Vector(x, y, z).normalize());
                }
            }
        }

        return dirs;
    }

    public static List<Vector> generate256Directions() {
        int quality = 256 * 2;
        List<Vector> dirs = new ArrayList<>(quality);

        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0)); // ≈ 2.399963

        for (int i = 0; i < quality; i++) {
            double y = 1.0 - (2.0 * i) / (quality - 1); // -1 à 1
            double radius = Math.sqrt(1 - y * y);

            double theta = goldenAngle * i;

            double x = Math.cos(theta) * radius;
            double z = Math.sin(theta) * radius;

            dirs.add(new Vector(x, y, z));
        }

        return dirs;
    }

    private boolean isInBounds(int worldX, int worldY, int worldZ) {
        return worldX >= minX && worldX < minX + sizeX
                && worldY >= minY && worldY < minY + sizeY
                && worldZ >= minZ && worldZ < minZ + sizeZ;
    }

    private void rayTraceVisibleBlocks(World world, Location start, Vector direction) {
        double x0 = start.getX();
        double y0 = start.getY();
        double z0 = start.getZ();

        int ix = (int) Math.floor(x0);
        int iy = (int) Math.floor(y0);
        int iz = (int) Math.floor(z0);

        // Determine step directions
        int stepX = direction.getX() > 0 ? 1 : (direction.getX() < 0 ? -1 : 0);
        int stepY = direction.getY() > 0 ? 1 : (direction.getY() < 0 ? -1 : 0);
        int stepZ = direction.getZ() > 0 ? 1 : (direction.getZ() < 0 ? -1 : 0);

        double tMaxX = stepX != 0 ? ((stepX > 0 ? (ix + 1) - x0 : x0 - ix) / Math.abs(direction.getX())) : Double.POSITIVE_INFINITY;
        double tMaxY = stepY != 0 ? ((stepY > 0 ? (iy + 1) - y0 : y0 - iy) / Math.abs(direction.getY())) : Double.POSITIVE_INFINITY;
        double tMaxZ = stepZ != 0 ? ((stepZ > 0 ? (iz + 1) - z0 : z0 - iz) / Math.abs(direction.getZ())) : Double.POSITIVE_INFINITY;

        double tDeltaX = stepX != 0 ? 1.0 / Math.abs(direction.getX()) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? 1.0 / Math.abs(direction.getY()) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? 1.0 / Math.abs(direction.getZ()) : Double.POSITIVE_INFINITY;

        int steps = 0;
        int maxSteps = (int) maxDistance;

        while (steps++ < maxSteps) {
            if (!isInBounds(ix, iy, iz)) break;

            Material mat = world.getBlockAt(ix, iy, iz).getType();
            if (!mat.isAir()) {
                int[] local = toLocal(ix, iy, iz);
                visibleArea.set(local[0], local[1], local[2]);
            }

            if (mat.isOccluding()) break;

            // Advance to next voxel
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    ix += stepX;
                    tMaxX += tDeltaX;
                } else {
                    iz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    iy += stepY;
                    tMaxY += tDeltaY;
                } else {
                    iz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }
    }


    private boolean isSolidBlock(int worldX, int worldY, int worldZ) {
        return baseLoc.getWorld().getBlockAt(worldX, worldY, worldZ).getType().isSolid();
    }

    // Map world block -> local
    public int[] toLocal(Location loc) {
        return toLocal(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int[] toLocal(int x, int y, int z) {
        int lx = x - minX;
        int ly = y - minY;
        int lz = z - minZ;
        return new int[]{lx, ly, lz};
    }

    // Map local -> world block
    public int[] toWorld(int lx, int ly, int lz) {
        int wx = lx + minX;
        int wy = ly + minY;
        int wz = lz + minZ;
        return new int[]{wx, wy, wz};
    }

    enum State {
        FLOOD_FILL,
        COMPUTE_VISIBLE,
        DELETE_BLOCKS
    }

}