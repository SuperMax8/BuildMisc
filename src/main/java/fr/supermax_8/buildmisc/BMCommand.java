package fr.supermax_8.buildmisc;

import io.papermc.paper.raytracing.RayTraceTarget;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
            /*case "optimizeyesimsure" -> {
                p.sendMessage("Starting build optimizer...");
                if (checkLoc(p, pos1) || checkLoc(p, pos2)) return true;
                new BlockZoneOptimiser(p.getLocation().clone(), pos1.clone(), pos2.clone());
            }*/
            case "underground" -> {
                p.sendMessage("Starting build optimizer...");
                if (checkLoc(p, pos1) || checkLoc(p, pos2)) return true;
                new BlockUndergroundBfsOptimiser(pos1.clone(), pos2.clone());
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
            case "testexpand" -> {
                Octant octant = new Octant(List.of(
                        new Vector(1, 0, 0),
                        new Vector(0, 0, 1),
                        new Vector(0, -1, 0)
                ), p.getLocation().clone());
                Collection<Location> expand = expand(octant);
                expand.forEach(l -> {
                    /*l.getBlock().setType(Material.DIAMOND_BLOCK);*/
                    p.sendBlockChange(l, Material.DIAMOND_BLOCK.createBlockData());
                });
                p.sendMessage(expand.size() + " ");
            }
        }
        return false;
    }

    public Collection<Location> expand(Octant octant) {
        List<Location> locs = new ArrayList<>();
        Set<BlockVector> visited = new HashSet<>();
        int i = 0;
        while (!octant.nodes.isEmpty() && (i < 1000000)) {
            Node pos = octant.nodes.poll();
            BlockVector key = new BlockVector(
                    pos.loc.getBlockX(),
                    pos.loc.getBlockY(),
                    pos.loc.getBlockZ()
            );

            if (!visited.add(key)) continue;

            locs.add(pos.loc);

            if (pos.loc.distanceSquared(octant.origin) >= octant.distance2) continue;
            for (Vector dir : octant.dirs) {
                Location loc = pos.loc.clone().add(dir);
                if (loc.getBlock().getType().isOccluding()) continue;
                octant.nodes.add(new Node(loc));
            }
            i++;
        }
        return locs;
    }

    public class Octant {

        List<Vector> dirs;
        private final Queue<Node> nodes = new ArrayDeque<>();
        double distance2 = 100 * 100;
        Location origin;

        public Octant(List<Vector> dirs, Location origin) {
            this.dirs = dirs;
            this.origin = origin;
            nodes.add(new Node(origin));
        }


    }

    public class Node {

        Location loc;

        public Node(Location loc) {
            this.loc = loc;
        }
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

}
