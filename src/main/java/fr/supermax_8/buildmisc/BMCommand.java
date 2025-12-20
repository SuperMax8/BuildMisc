package fr.supermax_8.buildmisc;

import io.papermc.paper.raytracing.RayTraceTarget;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
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

}
