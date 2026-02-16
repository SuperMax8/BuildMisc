package fr.supermax_8.buildmisc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

public class BlockUndergroundBfsOptimiser {

    private final int MAX_ITERATION_PER_TICK = 1_300_000;

    private final Location loc1, loc2;
    private final int sizeX, sizeY, sizeZ;
    private final int minX, minY, minZ;
    private AtomicInt3DMarkSet visionArea, occludingBlocks, toKeep;
    private final Location baseLoc;
    private BlockZoneOptimiser.State state = BlockZoneOptimiser.State.FLOOD_FILL;
    private AtomicInt3DMarkSet.MarkedIterator iterator;
    private CuboidBlockIterator blockIterator;
    private int totalDeleted = 0;
    private double maxDistance;

    public BlockUndergroundBfsOptimiser(Location baseLoc, Location loc1, Location loc2) {
        this.baseLoc = baseLoc.clone();
        this.loc1 = loc1;
        this.loc2 = loc2;
        minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());

        sizeX = Math.abs(loc1.getBlockX() - loc2.getBlockX()) + 1;
        sizeY = Math.abs(loc1.getBlockY() - loc2.getBlockY()) + 1;
        sizeZ = Math.abs(loc1.getBlockZ() - loc2.getBlockZ()) + 1;

        visionArea = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);
        occludingBlocks = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);
        toKeep = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);

        maxDistance = loc1.distance(loc2) + 5;

        Int3DMarkSetBFS bfs = new Int3DMarkSetBFS(visionArea, List.of(toLocal(baseLoc)), iii -> {
            int[] worldPos = toWorld(iii[0], iii[1], iii[2]);
            boolean successful = !isSolidBlock(worldPos[0], worldPos[1], worldPos[2]);
            if (successful) {
                Bukkit.getOnlinePlayers().forEach(p -> {
                    p.sendBlockChange(new Location(baseLoc.getWorld(), worldPos[0], worldPos[1], worldPos[2]), Material.DIAMOND_BLOCK.createBlockData());
                });
            }
            return successful ? Int3DMarkSetBFS.ValidatorResponse.VALIDATED : Int3DMarkSetBFS.ValidatorResponse.NON_VALIDATED;
        }, MAX_ITERATION_PER_TICK);

        Bukkit.getScheduler().runTaskTimer(BuildMisc.instance, t -> {
            World w = baseLoc.getWorld();
            switch (state) {
                case FLOOD_FILL -> {
                    if (bfs.tick()) {
                        blockIterator = new CuboidBlockIterator(loc1, loc2);
                        state = BlockZoneOptimiser.State.COMPUTE_OCCLUDING;
                    }
                    iterator = visionArea.iterator();
                }
                case COMPUTE_OCCLUDING -> {
                    if (!blockIterator.hasNext()) {
                        System.out.println("End occluding");
                        Bukkit.getScheduler().runTaskAsynchronously(BuildMisc.instance, () -> {
                            computeVisible();
                            startDeleteBlockTicking();
                        });
                        t.cancel();
                        return;
                    }
                    for (Block b : blockIterator.next(MAX_ITERATION_PER_TICK)) {
                        if (b.isEmpty()) continue;
                        int[] localC = toLocal(b.getLocation());

                        if (b.getType().isOccluding() && b.getType() != Material.BARRIER) {
                            Bukkit.getOnlinePlayers().forEach(p -> {
                                p.sendBlockChange(b.getLocation(), Material.EMERALD_BLOCK.createBlockData());
                            });
                            occludingBlocks.set(localC[0], localC[1], localC[2]);
                        }
                    }
                }
            }
        }, 0, 1);
    }

    private void startDeleteBlockTicking() {
        blockIterator = new CuboidBlockIterator(loc1, loc2);
        Bukkit.getScheduler().runTaskTimer(BuildMisc.instance, t -> {
            if (!blockIterator.hasNext()) {
                System.out.println("End optimizing, total deletion: " + totalDeleted);
                t.cancel();
                return;
            }
            for (Block b : blockIterator.next(MAX_ITERATION_PER_TICK)) {
                if (b.isEmpty()) continue;
                int[] localC = toLocal(b.getLocation());

                if (!toKeep.get(localC[0], localC[1], localC[2])) {
                    b.setType(Material.AIR);
                    totalDeleted++;
                }
            }
        }, 0, 0);
    }

    private boolean isInBounds(int worldX, int worldY, int worldZ) {
        return worldX >= minX && worldX < minX + sizeX
                && worldY >= minY && worldY < minY + sizeY
                && worldZ >= minZ && worldZ < minZ + sizeZ;
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
        COMPUTE_OCCLUDING,
        COMPUTE_VISIBLE,
        DELETE_BLOCKS
    }

}