package fr.supermax_8.buildmisc;

import io.papermc.paper.raytracing.RayTraceTarget;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockZoneOptimiser {

    private final int MAX_FLOODFILL = 300 * 300 * 300;
    private final int MAX_ITERATION_PER_TICK = 1_300_000;
    private final int MAX_ITERATION_OPTIMISE_PER_TICK = 5_000;

    private final int sizeX, sizeY, sizeZ;
    private final int minX, minY, minZ;
    private AtomicInt3DMarkSet visionArea, visited, nonVisibleArea;
    private final Location baseLoc;
    private State state = State.FLOOD_FILL;

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
        nonVisibleArea = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);

        Int3DMarkSetBFS visionBfs = new Int3DMarkSetBFS(visionArea, List.of(toLocal(baseLoc)), iii -> {
            int[] worldPos = toWorld(iii[0], iii[1], iii[2]);
            return !isSolidBlock(worldPos[0], worldPos[1], worldPos[2]);
        }, MAX_ITERATION_PER_TICK);
        Int3DMarkSetBFS optimiseBfs = new Int3DMarkSetBFS(visited, List.of(toLocal(loc1)), iii -> {
            //int[] worldPos = toWorld(iii[0], iii[1], iii[2]);
            //return !isSolidBlock(worldPos[0], worldPos[1], worldPos[2]);
            int[] toCheckWorld = toWorld(iii[0], iii[1], iii[2]);
            Vector v = new Vector(toCheckWorld[0], toCheckWorld[1], toCheckWorld[2]);
            Location l = new Location(baseLoc.getWorld(), toCheckWorld[0], toCheckWorld[1], toCheckWorld[2]);

            if (l.getBlock().isSolid())
                visionArea.forEachMarked((x, y, z) -> {
                    int[] worldC = toWorld(x, y, z);
                    Location location = new Location(baseLoc.getWorld(), worldC[0], worldC[1], worldC[2]);
                    Vector direction = new Vector(worldC[0], worldC[1], worldC[2]).subtract(v);
                    RayTraceResult result = baseLoc.getWorld().rayTrace(b -> {
                        b.entityFilter(e -> false)
                                .start(l)
                                .direction(direction)
                                .maxDistance(l.distance(location) + 5)
                                .targets(RayTraceTarget.BLOCK)
                                .blockFilter(p -> {
                                    Material mat = p.getType();
                                    return mat != Material.BARRIER;
                                })
                                .fluidCollisionMode(FluidCollisionMode.NEVER);
                    });
                    if (result.getHitBlock().getLocation().distance(l) > 1) {
                        nonVisibleArea.set(iii[0], iii[1], iii[2]);
                        return true;
                    }
                    return false;
                });
            return !visionArea.get(iii[0], iii[1], iii[2]);
        }, MAX_ITERATION_OPTIMISE_PER_TICK);
        Bukkit.getScheduler().runTaskTimer(BuildMisc.instance, t -> {
            switch (state) {
                case FLOOD_FILL -> {
                    if (visionBfs.tick()) state = State.OPTIMISE;
                }
                case OPTIMISE -> {
                    System.out.println("Ticking optimise... " + optimiseBfs.getTotalItr());
                    if (optimiseBfs.tick()) {
                        AtomicInteger ii = new AtomicInteger();
                        nonVisibleArea.forEachMarked((x, y, z) -> {
                            ii.incrementAndGet();
                            return false;
                        });
                        System.out.println("VisibleBlocks " + ii.get());
                        t.cancel();
                    }
                }
            }
        }, 0, 1);
    }

    private boolean isSolidBlock(int worldX, int worldY, int worldZ) {
        return baseLoc.getWorld().getBlockAt(worldX, worldY, worldZ).getType().isSolid();
    }

    // Map world block -> local
    public int[] toLocal(Location loc) {
        int lx = loc.getBlockX() - minX;
        int ly = loc.getBlockY() - minY;
        int lz = loc.getBlockZ() - minZ;
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
        OPTIMISE
    }

}