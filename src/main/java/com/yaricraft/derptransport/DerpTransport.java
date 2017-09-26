package com.yaricraft.derptransport;

import br.net.fabiozumbi12.RedProtect.Bukkit.API.RedProtectAPI;
import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class DerpTransport extends JavaPlugin implements Listener, CommandExecutor, Runnable {

    public static void log(String message) { log(message, Level.INFO); }
    public static void log(String message, Level level) { Bukkit.getLogger().log(level != null ? level : Level.INFO, "[DerpTransport] " + message); }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("derptransport").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, this, 20 * 5, 20 * 2);
        log("Loaded DerpTransport");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender || sender.isOp())) return false;

        if (args.length != 3) {
            sender.sendMessage("Correct usage is /dt region minrange maxrange");
            return true;
        }

        WorldGuardPlugin wg;
        World world;
        String regionName = args[0];
        int min, max;

        try {
            wg = getWorldGuard();
        } catch (NullPointerException e) {
            sender.sendMessage("WorldGuard not loaded.");
            return true;
        }

        try {
            min = Integer.valueOf(args[1]);
            max = Integer.valueOf(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Each range must be a number.");
            return true;
        }

        if (min < 10) min = 10;
        if (max < 10) max = 10;
        if (max < min) max = min;

        if (sender instanceof Player) {
            world = ((Player) sender).getWorld();
        } else {
            world = (Bukkit.getWorld("world"));
        }

        ProtectedRegion region = wg.getRegionManager(world).getRegions().get(regionName);

        if (region == null) {
            sender.sendMessage("Region '" + regionName + "' does not exist.");
            return true;
        }

        this.getConfig().set("randomregions." + regionName + ".min", min);
        this.getConfig().set("randomregions." + regionName + ".max", max);

        this.saveConfig();

        sender.sendMessage("Teleporter successfully setup on region '" + regionName + ".");

        return true;
    }

    private WorldGuardPlugin getWorldGuard() throws NullPointerException {
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");

        return plugin != null && plugin instanceof WorldGuardPlugin ? (WorldGuardPlugin) plugin : null;
    }

    private RedProtect getRedProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("RedProtect");

        return plugin != null && plugin instanceof RedProtect ? (RedProtect) plugin : null;
    }

    // Task to check location of players.
    @Override
    public void run() {
        Plugin plugin = this;
        WorldGuardPlugin wg = getWorldGuard();
        List<ProtectedRegion> effectiveregions = new ArrayList<>();

        if (wg == null) return;

        Bukkit.getWorlds().forEach(world -> {
            Map<String, ProtectedRegion> allregions = wg.getRegionManager(world).getRegions();
            allregions.forEach((regionName, region) -> {
                if (plugin.getConfig().getConfigurationSection("randomregions").contains(regionName)) effectiveregions.add(region);
            });

            world.getPlayers().forEach(player -> {
                effectiveregions.forEach(region -> {
                    if (region.contains(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                        // We did it!!!
                        RandomTransport(player, region, plugin.getConfig().getInt("randomregions." + region.getId() + ".min"), plugin.getConfig().getInt("randomregions." + region.getId() + ".max"), 1);
                    }
                });
            });
        });
    }

    // Transport the player.
    private void RandomTransport(Player player, ProtectedRegion region, int min, int max, int attempt) {
        if (attempt > 10) return;

        Location playerLoc = player.getLocation();

        int radius = ThreadLocalRandom.current().nextInt(min, max + 1);
        int angle = ThreadLocalRandom.current().nextInt(0, 360);
        int x = (int)(radius * Math.cos(angle));
        int y = (int)(radius * Math.sin(angle));
        Block block = player.getWorld().getHighestBlockAt(playerLoc.getBlockX() + x, playerLoc.getBlockY() + y);
        Location newLoc = block.getLocation().add(0, 2, 0);

        RedProtect redProtect = getRedProtect();
        WorldGuardPlugin worldGuardPlugin = getWorldGuard();

        if (redProtect != null) {
            if (RedProtectAPI.getRegion(newLoc) != null) RandomTransport(player, region, min, max + 20, ++attempt);
            return;
        }

        if (worldGuardPlugin != null) {
            ApplicableRegionSet set = worldGuardPlugin.getRegionManager(player.getWorld()).getApplicableRegions(newLoc);
            if (set != null && set.size() == 0) RandomTransport(player, region, min, max + 20, ++attempt);
            return;
        }

        player.teleport(newLoc);
    }
}
