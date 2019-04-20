package misat11.bw.game;

import static misat11.lib.lang.I18n.i18n;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import misat11.bw.api.Game;
import misat11.bw.api.RunningTeam;
import misat11.bw.api.TeamColor;

public class CurrentTeam implements RunningTeam {
	public final Team teamInfo;
	public final List<GamePlayer> players = new ArrayList<>();
	private org.bukkit.scoreboard.Team scoreboardTeam;
	private Inventory chestInventory = Bukkit.createInventory(null, InventoryType.ENDER_CHEST, i18n("team_chest"));
	private List<Block> chests = new ArrayList<Block>();
	private Game game;

	public boolean isBed = true;

	public CurrentTeam(Team team, Game game) {
		this.teamInfo = team;
		this.game = game;
	}

	public boolean isDead() {
		return players.isEmpty();
	}

	public boolean isAlive() {
		return !players.isEmpty();
	}

	public org.bukkit.scoreboard.Team getScoreboardTeam() {
		return scoreboardTeam;
	}

	public void setScoreboardTeam(org.bukkit.scoreboard.Team scoreboardTeam) {
		this.scoreboardTeam = scoreboardTeam;
	}

	@Override
	public String getName() {
		return teamInfo.name;
	}

	@Override
	public TeamColor getColor() {
		return teamInfo.color.toApiColor();
	}

	@Override
	public Location getTeamSpawn() {
		return teamInfo.spawn;
	}

	@Override
	public Location getTargetBlock() {
		return teamInfo.bed;
	}

	@Override
	public int getMaxPlayers() {
		return teamInfo.maxPlayers;
	}

	@Override
	public int countConnectedPlayers() {
		return players.size();
	}

	@Override
	public List<Player> getConnectedPlayers() {
		List<Player> playerList = new ArrayList<Player>();
		for (GamePlayer gamePlayer : players) {
			playerList.add(gamePlayer.player);
		}
		return playerList;
	}

	@Override
	public boolean isPlayerInTeam(Player player) {
		for (GamePlayer gamePlayer : players) {
			if (gamePlayer.player.equals(player)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isTargetBlockExists() {
		return isBed;
	}

	@Override
	public void addTeamChest(Location location) {
		addTeamChest(location.getBlock());
	}

	@Override
	public void addTeamChest(Block block) {
		if (!chests.contains(block)) {
			chests.add(block);
		}
	}

	@Override
	public void removeTeamChest(Location location) {
		removeTeamChest(location.getBlock());
	}

	@Override
	public void removeTeamChest(Block block) {
		if (chests.contains(block)) {
			chests.remove(block);
		}
	}

	@Override
	public boolean isTeamChestRegistered(Location location) {
		return isTeamChestRegistered(location.getBlock());
	}

	@Override
	public boolean isTeamChestRegistered(Block block) {
		return chests.contains(block);
	}

	@Override
	public Inventory getTeamChestInventory() {
		return chestInventory;
	}

	@Override
	public Game getGame() {
		return game;
	}

	@Override
	public int countTeamChests() {
		return chests.size();
	}
}
