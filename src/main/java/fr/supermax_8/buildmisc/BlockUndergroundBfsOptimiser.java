package fr.supermax_8.buildmisc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;

public class BlockUndergroundBfsOptimiser {

    private static final int MAX_BLOCKS_PER_TICK = 80_000;

    private final Location loc1, loc2;
    private CuboidBlockIterator iterator;

    private final Set<BlockPos> toDelete = new HashSet<>();
    private int totalDeleted = 0;

    private enum State {
        COLLECT,
        DELETE
    }

    private State state = State.COLLECT;

    public BlockUndergroundBfsOptimiser(Location loc1, Location loc2) {
        this.loc1 = loc1;
        this.loc2 = loc2;
        this.iterator = new CuboidBlockIterator(loc1, loc2);
        start();
    }

    private void start() {
        Bukkit.getScheduler().runTaskTimer(BuildMisc.instance, task -> {

            switch (state) {

                case COLLECT -> {
                    if (!iterator.hasNext()) {
                        state = State.DELETE;
                        iterator = new CuboidBlockIterator(loc1, loc2);
                        return;
                    }

                    for (Block b : iterator.next(MAX_BLOCKS_PER_TICK)) {
                        if (shouldDelete(b)) {
                            toDelete.add(BlockPos.of(b));
                        }
                    }
                }

                case DELETE -> {
                    if (!iterator.hasNext()) {
                        Bukkit.getLogger().info(
                                "[Optimiser] Done. Deleted blocks: " + totalDeleted
                        );
                        task.cancel();
                        return;
                    }

                    for (Block b : iterator.next(MAX_BLOCKS_PER_TICK)) {
                        if (toDelete.contains(BlockPos.of(b))) {
                            b.setType(Material.AIR, false);
                            totalDeleted++;
                        }
                    }
                }
            }

        }, 0, 1);
    }

    /* ============================= */
    /* ===== CORE CONDITION ======== */
    /* ============================= */

    private boolean shouldDelete(Block b) {

        if (!isSolidOpaque(b)) return false;

        World w = b.getWorld();
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();

        return isSolidOpaque(w.getBlockAt(x + 1, y, z))
                && isSolidOpaque(w.getBlockAt(x - 1, y, z))
                && isSolidOpaque(w.getBlockAt(x, y + 1, z))
                && isSolidOpaque(w.getBlockAt(x, y - 1, z))
                && isSolidOpaque(w.getBlockAt(x, y, z + 1))
                && isSolidOpaque(w.getBlockAt(x, y, z - 1));
    }

    private boolean isSolidOpaque(Block b) {
        Material m = b.getType();
        return m.isSolid() && m.isOccluding() && m != Material.BARRIER;
    }

    /* ============================= */
    /* ===== BLOCK POSITION ======== */
    /* ============================= */

    private record BlockPos(int x, int y, int z) {

        static BlockPos of(Block b) {
            return new BlockPos(b.getX(), b.getY(), b.getZ());
        }
    }
}