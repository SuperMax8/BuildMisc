package fr.supermax_8.buildmisc;

import io.papermc.paper.raytracing.RayTraceTarget;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class BMCommand implements CommandExecutor {

    static Location pos1, pos2;

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        if (!(commandSender instanceof Player p)) return false;
        if (!p.hasPermission("admin")) return false;
        if (!p.isOp()) return false;
        switch (args[0]) {
            case "pos1" -> {
                pos1 = p.getLocation();
                if (checkLoc(p, pos1)) return false;
                p.sendMessage("Pos1 set");
            }
            case "pos2" -> {
                pos2 = p.getLocation();
                if (checkLoc(p, pos2)) return false;
                p.sendMessage("Pos2 set");
            }
            case "optimizeyesimsure" -> {
                p.sendMessage("Starting build optimizer...");
                if (checkLoc(p, pos1) || checkLoc(p, pos2)) return true;
                new BlockZoneOptimiser(p.getLocation().clone(), pos1.clone(), pos2.clone());
            }
            case "test" -> {
                Location baseLoc = p.getLocation();
                RayTraceResult result = baseLoc.getWorld().rayTrace(b -> {
                    b.entityFilter(e -> false)
                            .start(p.getEyeLocation())
                            .direction(p.getLocation().getDirection())
                            .maxDistance(100)
                            .targets(RayTraceTarget.BLOCK)
                            .blockFilter(pp -> {
                                Material mat = pp.getType();
                                p.sendMessage("mat: " + mat.name());
                                return mat != Material.BARRIER;
                            }).fluidCollisionMode(FluidCollisionMode.NEVER);
                });
                p.sendMessage(" " + result.toString());
                p.sendBlockChange(result.getHitBlock().getLocation(), Material.DIAMOND_BLOCK.createBlockData());
            }
            case "test2" -> {
                Location baseLoc = p.getLocation();
                rayTraceVisibleBlocks(baseLoc.getWorld(), baseLoc, p.getLocation().getDirection());
            }
        }
        return false;
    }

    private boolean checkLoc(Player p, Location loc) {
        if (!loc.getWorld().getName().contains("test")) {
            pos1 = null;
            pos2 = null;
            String msg = "BMOPTI Cancelled pos1 pos2 because not in a world with a name containing test";
            System.out.println(msg);
            p.sendMessage("§c" + msg);
            return true;
        }
        return false;
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
        int maxSteps = (int) 40;

        while (steps++ < maxSteps) {

            Material mat = world.getBlockAt(ix, iy, iz).getType();

            if (mat.isOccluding()) {
                world.getBlockAt(ix, iy, iz).setType(Material.DIAMOND_BLOCK);
                break;
            }

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

}
