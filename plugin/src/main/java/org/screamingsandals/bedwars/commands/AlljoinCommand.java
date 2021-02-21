package org.screamingsandals.bedwars.commands;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import org.bukkit.Bukkit;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.game.GameManager;
import org.screamingsandals.lib.sender.CommandSenderWrapper;

import java.util.Optional;

import static org.screamingsandals.bedwars.lib.lang.I.i18n;

public class AlljoinCommand extends BaseCommand {
    public AlljoinCommand(CommandManager<CommandSenderWrapper> manager) {
        super(manager, "alljoin", BedWarsPermission.ALL_JOIN_PERMISSION, true);
    }

    @Override
    protected void construct(Command.Builder<CommandSenderWrapper> commandSenderWrapperBuilder) {
        manager.command(
                commandSenderWrapperBuilder
                        .argument(manager
                                .argumentBuilder(String.class, "game")
                                .withSuggestionsProvider((c, s) -> GameManager.getInstance().getGameNames())
                                .asOptional()
                        )
                        .handler(commandContext -> {
                            Optional<String> gameName = commandContext.getOptional("game");

                            var sender = commandContext.getSender();
                            var game = gameName
                                    .flatMap(GameManager.getInstance()::getGame)
                                    .or(GameManager.getInstance()::getGameWithHighestPlayers);

                            if (game.isEmpty()) {
                                sender.sendMessage(i18n("no_arena_found"));
                                return;
                            }

                            // TODO - Use Wrapper in the code (needed changes in BW)
                            Bukkit.getOnlinePlayers().forEach(player -> {
                                if (player.hasPermission("bw.disable.joinall")) {
                                    return;
                                }

                                if (Main.isPlayerInGame(player)) {
                                    Main.getPlayerGameProfile(player).getGame().leaveFromGame(player);
                                }
                                game.get().joinToGame(player);
                            });
                        })
        );
    }
}
