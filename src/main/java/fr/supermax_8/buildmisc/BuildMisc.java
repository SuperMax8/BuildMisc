package fr.supermax_8.buildmisc;

import org.bukkit.plugin.java.JavaPlugin;

public final class BuildMisc extends JavaPlugin {

    public static BuildMisc instance;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
        getCommand("bm").setExecutor(new BMCommand());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

}
