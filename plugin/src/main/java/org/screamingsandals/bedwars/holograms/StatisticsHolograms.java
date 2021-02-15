package org.screamingsandals.bedwars.holograms;

import static org.screamingsandals.bedwars.lib.lang.I.i18n;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.statistics.PlayerStatistic;
import org.screamingsandals.bedwars.commands.old.BaseCommand;
import org.screamingsandals.bedwars.lib.nms.holograms.Hologram;
import org.screamingsandals.bedwars.lib.nms.holograms.TouchHandler;
import org.screamingsandals.bedwars.utils.PreparedLocation;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public class StatisticsHolograms implements TouchHandler {

    private ArrayList<PreparedLocation> hologramLocations = null;
    private Map<UUID, List<Hologram>> holograms = null;

	public void addHologramLocation(Location eyeLocation) {
        this.hologramLocations.add(new PreparedLocation(eyeLocation.subtract(0, 3, 0)));
        this.updateHologramDatabase();
	}

    @SuppressWarnings("unchecked")
    public void loadHolograms() {
        if (!Main.isHologramsEnabled()) {
            return;
        }

        if (this.holograms != null && this.hologramLocations != null) {
            // first unload all holograms
            this.unloadHolograms();
        }

        this.holograms = new HashMap<>();
        this.hologramLocations = new ArrayList<>();

        File file = new File(Main.getInstance().getDataFolder(), "database/holodb.yml");

        var loader = YamlConfigurationLoader.builder()
                .file(file)
                .build();
        if (file.exists()) {
            try {
                var config = loader.load();
                var locations = config.node("locations").getList(PreparedLocation.class);
                this.hologramLocations.addAll(locations);
            } catch (ConfigurateException e) {
                e.printStackTrace();
            }
        }

        if (this.hologramLocations.size() == 0) {
            return;
        }

        this.updateHolograms();
    }

	public void unloadHolograms() {
        if (Main.isHologramsEnabled()) {
        	for (List<Hologram> holos : holograms.values()) {
        		for (Hologram holo : holos) {
        			holo.destroy();
        		}
        	}
        }
	}

	public void updateHolograms(Player player) {
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () ->
            this.hologramLocations.forEach(holoLocation ->
                    holoLocation.asOptional(Location.class).ifPresent(location ->
                        this.updatePlayerHologram(player, location)
                    )
            )
        );
	}

	public void updateHolograms(Player player, long delay) {
        Main.getInstance().getServer().getScheduler().runTaskLater(
                Main.getInstance(),
                () -> this.updateHolograms(player),
                delay
        );
	}

	public void updateHolograms() {
        for (final Player player : Bukkit.getServer().getOnlinePlayers()) {
            Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () ->
                this.hologramLocations.forEach(holoLocation ->
                        holoLocation.asOptional(Location.class).ifPresent(location ->
                                this.updatePlayerHologram(player, location)
                        )
                )
            );
        }
	}

    private Hologram getHologramByLocation(List<Hologram> holograms, Location holoLocation) {
        for (Hologram holo : holograms) {
            if (holo.getLocation().getX() == holoLocation.getX() && holo.getLocation().getY() == holoLocation.getY()
                    && holo.getLocation().getZ() == holoLocation.getZ()) {
                return holo;
            }
        }

        return null;
    }
	
	public void updatePlayerHologram(Player player, Location holoLocation) {
        List<Hologram> holograms;
        if (!this.holograms.containsKey(player.getUniqueId())) {
            this.holograms.put(player.getUniqueId(), new ArrayList<>());
        }

        holograms = this.holograms.get(player.getUniqueId());
        Hologram holo = this.getHologramByLocation(holograms, holoLocation);
        if (holo == null && player.getWorld() == holoLocation.getWorld()) {
            holograms.add(this.createPlayerStatisticHologram(player, holoLocation));
        } else if (holo != null) {
            if (holo.getLocation().getWorld() == player.getWorld()) {
                this.updatePlayerStatisticHologram(player, holo);
            } else {
                holograms.remove(holo);
                holo.destroy();
            }
        }
	}

    private Hologram createPlayerStatisticHologram(Player player, Location holoLocation) {
        final Hologram holo = Main.getHologramManager().spawnHologramTouchable(player, holoLocation);
        holo.addHandler(this);

        String headline = Main.getConfigurator().node("holograms", "headline").getString("Your §eBEDWARS§f stats");
        if (!headline.trim().isEmpty()) {
            holo.addLine(headline);
        }

        this.updatePlayerStatisticHologram(player, holo);
        return holo;
    }

    private void updateHologramDatabase() {
        try {
            // update hologram-database file
            File file = new File(Main.getInstance().getDataFolder(), "database/holodb.yml");
            var loader = YamlConfigurationLoader.builder()
                    .file(file)
                    .build();

            var node = loader.createNode();

            for (var location : hologramLocations) {
                node.node("locations").appendListNode().set(location);
            }
            loader.save(node);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private PreparedLocation getHologramLocationByLocation(Location holoLocation) {
        return hologramLocations.stream()
                .filter(loc -> loc.getX() == holoLocation.getX()
                                && loc.getY() == holoLocation.getY()
                                && loc.getZ() == holoLocation.getZ()
                )
                .findFirst().orElse(null);
    }

	@Override
	public void handle(Player player, Hologram holo) {
        if (!player.hasMetadata("bw-remove-holo") || (!player.isOp() && !BaseCommand.hasPermission(player, BaseCommand.ADMIN_PERMISSION, false))) {
            return;
        }

        player.removeMetadata("bw-remove-holo", Main.getInstance());
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> {
            // remove all player holograms on this location
            for (Entry<UUID, List<Hologram>> entry : holograms.entrySet()) {
                Iterator<Hologram> iterator = entry.getValue().iterator();
                while (iterator.hasNext()) {
                    Hologram hologram = iterator.next();
                    if (hologram.getLocation().getX() == holo.getLocation().getX() && hologram.getLocation().getY() == holo.getLocation().getY()
                            && hologram.getLocation().getZ() == holo.getLocation().getZ()) {
                        hologram.destroy();
                        iterator.remove();
                    }
                }
            }

            var holoLocation = getHologramLocationByLocation(holo.getLocation());
            if (holoLocation != null) {
                hologramLocations.remove(holoLocation);
                updateHologramDatabase();
            }
            player.sendMessage(i18n("holo_removed"));
        });
	}

    private void updatePlayerStatisticHologram(Player player, final Hologram holo) {
        PlayerStatistic statistic = Main.getPlayerStatisticsManager().getStatistic(player);
        
        List<String> lines = new ArrayList<>();

        lines.add(i18n("statistics_kills", false).replace("%kills%",
                Integer.toString(statistic.getKills())));
        lines.add(i18n("statistics_deaths", false).replace("%deaths%",
                Integer.toString(statistic.getDeaths())));
        lines.add(i18n("statistics_kd", false).replace("%kd%",
                Double.toString(statistic.getKD())));
        lines.add(i18n("statistics_wins", false).replace("%wins%",
                Integer.toString(statistic.getWins())));
        lines.add(i18n("statistics_loses", false).replace("%loses%",
                Integer.toString(statistic.getLoses())));
        lines.add(i18n("statistics_games", false).replace("%games%",
                Integer.toString(statistic.getGames())));
        lines.add(i18n("statistics_beds", false).replace("%beds%",
                Integer.toString(statistic.getDestroyedBeds())));
        lines.add(i18n("statistics_score", false).replace("%score%",
                Integer.toString(statistic.getScore())));
        
        int size = holo.length();
        int increment = 0;
        if (size == 1 || size > lines.size()) {
        	increment = 1;
        }

        for (int i = 0; i < lines.size(); i++) {
        	holo.setLine(i + increment, lines.get(i));
        }
    }

}
