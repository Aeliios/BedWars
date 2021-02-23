package org.screamingsandals.bedwars.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.TeamColor;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.listener.Player116ListenerUtils;
import org.spongepowered.configurate.ConfigurationNode;
import pronze.lib.scoreboards.Scoreboard;
import pronze.lib.scoreboards.ScoreboardManager;

import java.util.*;
import java.util.stream.Collectors;

public class ScreamingScoreboard {

    public static final String GAME_OBJECTIVE = "bedwars_game";
    public static final String LOBBY_OBJECTIVE = "bedwars_lobby";
    private final Game game;
    private final Map<UUID, Scoreboard> scoreboardMap = new HashMap<>();

    public ScreamingScoreboard(Game game) {
        this.game = game;
        game.getConnectedPlayers().forEach(this::createBoard);
    }

    private void createBoard(Player player) {
        Debug.info("Creating board for player: " + player.getName());

        final var scoreboardOptional = ScoreboardManager.getInstance()
                .fromCache(player.getUniqueId());
        scoreboardOptional.ifPresent(Scoreboard::destroy);

        final var scoreboard = Scoreboard.builder()
                .animate(false)
                .player(player)
                .title(MainConfig.getInstance().node("lobby-scoreboard", "title").getString("§eBEDWARS"))
                .displayObjective(LOBBY_OBJECTIVE)
                .updateInterval(20L)
                .placeholderHook(hook -> parseInternalPlaceholders(game.formatLobbyScoreboardString(hook.getLine())))
                .updateCallback(board -> {
                    board.setLines(process(board));
                    return true;
                })
                .build();
        scoreboardMap.put(player.getUniqueId(), scoreboard);
    }

    private List<String> process(Scoreboard board) {
        final var holder = board.getHolder();

        //lobby stages
        if (game.getStatus() == GameStatus.WAITING) {
            var rows = MainConfig.getInstance().node("lobby-scoreboard", "content")
                    .childrenList().stream().map(ConfigurationNode::getString).collect(Collectors.toList());
            if (rows.isEmpty()) {
                return List.of();
            }
            rows = rows.stream()
                    .map(game::formatLobbyScoreboardString).collect(Collectors.toList());
            board.setLines(rows);
        }

        if (game.getStatus() == GameStatus.RUNNING) {
            if (!GAME_OBJECTIVE.equals(holder.getObjectiveName())) {
                holder.registerObjective(GAME_OBJECTIVE);
            }
            holder.setTitle(game.formatScoreboardTitle());

            final var teamStatus = new ArrayList<String>();
            //lets process team status first
            game.getRunningTeams().forEach(team -> {
                String formattedScore = formatScoreboardTeam(team, !team.isTargetBlockExists(), team.isTargetBlockExists() && "RESPAWN_ANCHOR".equals(team.getTargetBlock().getBlock().getType().name()) && Player116ListenerUtils.isAnchorEmpty(team.getTargetBlock().getBlock()));
                teamStatus.add(formattedScore);
            });

            final var finalContent = new ArrayList<String>();
            List<String> content = MainConfig.getInstance().node("experimental", "new-scoreboard-system", "content")
                    .childrenList().stream().map(ConfigurationNode::getString).collect(Collectors.toList());

            content.forEach(line -> {
                if (line.contains("%team_status%")) {
                    finalContent.addAll(teamStatus);
                    return;
                }
                finalContent.add(line);
            });
            return finalContent;
        }

        game.getRunningTeams().forEach(team -> {
            if (!holder.hasTeamEntry(team.getName())) {
                holder.addTeam(team.getName(), TeamColor.fromApiColor(team.getColor()).chatColor);
            }
            final var scoreboardTeam = holder.getTeamOrRegister(team.getName());

            new HashSet<>(scoreboardTeam.getEntries())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Bukkit::getPlayerExact)
                    .filter(Objects::nonNull)
                    .forEach(teamPlayer -> {
                        if (!team.getConnectedPlayers().contains(teamPlayer)) {
                            scoreboardTeam.removeEntry(teamPlayer.getName());
                        }
                    });

            team.getConnectedPlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(playerName -> !scoreboardTeam.hasEntry(playerName))
                    .forEach(scoreboardTeam::addEntry);
        });

        return List.of();
    }

    private String parseInternalPlaceholders(String toParse) {
        return toParse.replace("%time%", game.getFormattedTimeLeft());
    }

    private String formatScoreboardTeam(RunningTeam team, boolean destroy, boolean empty) {
        if (team == null) {
            return "";
        }

        return MainConfig.getInstance().node("experimental", "new-scoreboard-system", "teamTitle").getString("")
                .replace("%team_size%", String.valueOf(
                        team.getConnectedPlayers().size()))
                .replace("%color%", TeamColor.fromApiColor(team.getColor())
                        .chatColor.toString()).replace("%team%", team.getName())
                .replace("%bed%", destroy ? Game.bedLostString() : (empty ? Game.anchorEmptyString() : Game.bedExistString()));
    }

    public void destroy() {
        scoreboardMap.values().forEach(Scoreboard::destroy);
    }

    public void addPlayer(Player player) {
        createBoard(player);
    }

    public void removePlayer(Player player) {
        if (!scoreboardMap.containsKey(player.getUniqueId())) {
            return;
        }
        final var scoreboard = scoreboardMap.get(player.getUniqueId());
        if (scoreboard != null) {
            scoreboard.destroy();
        }
        scoreboardMap.remove(player.getUniqueId());
    }
}
