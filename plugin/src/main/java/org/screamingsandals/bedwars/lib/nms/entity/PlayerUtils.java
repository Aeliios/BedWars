package org.screamingsandals.bedwars.lib.nms.entity;

import static org.screamingsandals.bedwars.lib.nms.utils.ClassStorage.*;
import static org.screamingsandals.bedwars.lib.nms.utils.ClassStorage.NMS.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.bedwars.Main;

public class PlayerUtils {
	public static void respawn(Plugin instance, Player player, long delay) {
		new BukkitRunnable() {

			@Override
			public void run() {
				try {
					player.spigot().respawn();
				} catch (Throwable t) {
					try {
						Object selectedObj = findEnumConstant(EnumClientCommand, "PERFORM_RESPAWN");
						Object packet = PacketPlayInClientCommand.getDeclaredConstructor(EnumClientCommand)
							.newInstance(selectedObj);
						Object connection = getPlayerConnection(player);
						getMethod(connection, "a,func_147342_a", PacketPlayInClientCommand).invoke(packet);
					} catch (Throwable ignored) {
						t.printStackTrace();
					}
				}
			}
		}.runTaskLater(instance, delay);
	}

	public static void fakeExp(Player player, float percentage, int levels) {
		try {
			Object packet = PacketPlayOutExperience.getConstructor(float.class, int.class, int.class)
				.newInstance(percentage, player.getTotalExperience(), levels);
			sendPacket(player, packet);
		} catch (Throwable t) {
		}
	}

	public static boolean teleportPlayer(Player player, Location location) {
		try {
			return player.teleportAsync(location).isDone();
		} catch (Throwable t) {
			player.teleport(location);
			return true;
		}
	}

	public static boolean teleportPlayer(Player player, Location location, Runnable runnable) {
		try {
			return player.teleportAsync(location).thenRun(runnable).isDone();
		} catch (Throwable t) {
			player.teleport(location);
			Bukkit.getScheduler().runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), runnable, 2); // player.teleport is synchronized, we don't have to wait
			return true;
		}
	}
}
