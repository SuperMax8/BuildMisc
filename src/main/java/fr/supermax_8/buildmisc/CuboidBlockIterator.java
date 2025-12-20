package fr.supermax_8.buildmisc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CuboidBlockIterator implements Iterator<Block> {

    private final World world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    private int x, y, z;
    private boolean hasNext = true;

    public CuboidBlockIterator(Location a, Location b) {
        if (!a.getWorld().equals(b.getWorld()))
            throw new IllegalArgumentException("Locations must be in the same world");

        this.world = a.getWorld();

        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());

        this.maxX = Math.max(a.getBlockX(), b.getBlockX());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        this.x = minX;
        this.y = minY;
        this.z = minZ;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public Block next() {
        if (!hasNext) throw new NoSuchElementException();

        Block block = world.getBlockAt(x, y, z);
        advance();
        return block;
    }

    private void advance() {
        x++;
        if (x > maxX) {
            x = minX;
            z++;
            if (z > maxZ) {
                z = minZ;
                y++;
                if (y > maxY) {
                    hasNext = false;
                }
            }
        }
    }

    /** Optional: iterate next N blocks at once */
    public Block[] next(int n) {
        if (!hasNext) throw new NoSuchElementException();

        int remaining = n;
        Block[] blocks = new Block[Math.min(n, (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1))];
        int idx = 0;

        while (remaining-- > 0 && hasNext) {
            blocks[idx++] = next();
        }

        if (idx < blocks.length) {
            Block[] result = new Block[idx];
            System.arraycopy(blocks, 0, result, 0, idx);
            return result;
        }

        return blocks;
    }

}