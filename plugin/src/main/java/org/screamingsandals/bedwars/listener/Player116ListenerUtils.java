package org.screamingsandals.bedwars.listener;

import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.game.CurrentTeam;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.Team;
import org.screamingsandals.bedwars.utils.Sounds;

public class Player116ListenerUtils {
    public static boolean processAnchorDeath(Game game, CurrentTeam team, boolean isBed) {
        RespawnAnchor anchor = (RespawnAnchor) team.getTeamInfo().bed.getBlock().getBlockData();
        int charges = anchor.getCharges();
        if (charges <= 0) {
            isBed = false;
        } else {
            anchor.setCharges(charges - 1);
            team.getTeamInfo().bed.getBlock().setBlockData(anchor);
            if (anchor.getCharges() == 0) {
                Sounds.playSound(team.getTeamInfo().bed, Main.getConfigurator().config.getString("target-block.respawn-anchor.sound.deplete"), Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1, 1);
                game.updateScoreboard();
            } else {
                Sounds.playSound(team.getTeamInfo().bed, Main.getConfigurator().config.getString("target-block.respawn-anchor.sound.used"), Sounds.BLOCK_GLASS_BREAK, 1, 1);
            }
        }
        return isBed;
    }

    public static boolean anchorCharge(PlayerInteractEvent event, Game game, ItemStack stack) {
        boolean anchorFilled = false;
        RespawnAnchor anchor = (RespawnAnchor) event.getClickedBlock().getBlockData();
        int charges = anchor.getCharges();
        charges++;
        if (charges <= anchor.getMaximumCharges()) {
            anchorFilled = true;
            anchor.setCharges(charges);
            event.getClickedBlock().setBlockData(anchor);
            stack.setAmount(stack.getAmount() - 1);
            Sounds.playSound(event.getClickedBlock().getLocation(), Main.getConfigurator().config.getString("target-block.respawn-anchor.sound.charge"), Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 1, 1);
            game.updateScoreboard();
        }
        return anchorFilled;
    }

    public static boolean isAnchorEmpty(Block anchor) {
        return ((RespawnAnchor) anchor.getBlockData()).getCharges() == 0;
    }
}
