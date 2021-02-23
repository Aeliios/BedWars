package org.screamingsandals.bedwars.special.listener;

import org.screamingsandals.bedwars.api.APIUtils;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.events.BedwarsApplyPropertyToBoughtItem;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerBuildBlock;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.screamingsandals.bedwars.config.MainConfig;

import static org.screamingsandals.bedwars.lib.lang.I.i18nc;

public class TeamChestListener implements Listener {

    public static final String TEAM_CHEST_PREFIX = "Module:TeamChest:";

    @EventHandler
    public void onMagnetShoesRegistered(BedwarsApplyPropertyToBoughtItem event) {
        if (event.getPropertyName().equalsIgnoreCase("teamchest")) {
            ItemStack stack = event.getStack();

            APIUtils.hashIntoInvisibleString(stack, TEAM_CHEST_PREFIX);
        }
    }

    @EventHandler
    public void onTeamChestBuilt(BedwarsPlayerBuildBlock event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        RunningTeam team = event.getTeam();

        if (block.getType() != Material.ENDER_CHEST) {
            return;
        }

        String unhidden = APIUtils.unhashFromInvisibleStringStartsWith(event.getItemInHand(), TEAM_CHEST_PREFIX);

        if (unhidden != null || MainConfig.getInstance().node("specials", "teamchest", "turn-all-enderchests-to-teamchests").getBoolean()) {
            team.addTeamChest(block);
            String message = i18nc("team_chest_placed", event.getGame().getCustomPrefix());
            for (Player pl : team.getConnectedPlayers()) {
                pl.sendMessage(message);
            }
        }
    }

}
