package org.screamingsandals.bedwars.inventories;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.PurchaseType;
import org.screamingsandals.bedwars.api.events.*;
import org.screamingsandals.bedwars.commands.DumpCommand;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.bedwars.game.GameStore;
import org.screamingsandals.bedwars.api.game.ItemSpawnerType;
import org.screamingsandals.bedwars.api.upgrades.Upgrade;
import org.screamingsandals.bedwars.api.upgrades.UpgradeRegistry;
import org.screamingsandals.bedwars.api.upgrades.UpgradeStorage;
import org.screamingsandals.bedwars.game.CurrentTeam;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.GamePlayer;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.bedwars.special.listener.PermaItemListener;
import org.screamingsandals.bedwars.utils.Sounds;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.material.Item;
import org.screamingsandals.lib.material.builder.ItemFactory;
import org.screamingsandals.lib.player.PlayerWrapper;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.AdventureHelper;
import org.screamingsandals.lib.utils.ConfigurateUtils;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import org.screamingsandals.lib.utils.annotations.parameters.DataFolder;
import org.screamingsandals.simpleinventories.SimpleInventoriesCore;
import org.screamingsandals.simpleinventories.events.ItemRenderEvent;
import org.screamingsandals.simpleinventories.events.OnTradeEvent;
import org.screamingsandals.simpleinventories.events.PreClickEvent;
import org.screamingsandals.simpleinventories.inventory.Include;
import org.screamingsandals.simpleinventories.inventory.InventorySet;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

// TODO: remake methods in this class so there won't be too many bukkit api calls
@Service(dependsOn = {
        SimpleInventoriesCore.class,
        MainConfig.class
})
@RequiredArgsConstructor
public class ShopInventory implements Listener {
    private final Map<String, InventorySet> shopMap = new HashMap<>();
    @DataFolder
    private final Path dataFolder;
    private final MainConfig mainConfig;

    public static ShopInventory getInstance() {
        return ServiceManager.get(ShopInventory.class);
    }

    @OnPostEnable
    public void onEnable() {
        Main.getInstance().registerBedwarsListener(this);

        var shopFileName = "shop.yml";
        if (mainConfig.node("turnOnExperimentalGroovyShop").getBoolean()) {
            shopFileName = "shop.groovy";
        }
        var shopFile = dataFolder.resolve(shopFileName).toFile();
        if (!shopFile.exists()) {
            Main.getInstance().saveResource(shopFileName, false);
        }

        loadNewShop("default", null, true);
    }

    public void show(PlayerWrapper player, GameStore store) {
        try {
            var parent = true;
            String fileName = null;
            if (store != null) {
                parent = store.getUseParent();
                fileName = store.getShopFile();
            }
            if (fileName != null) {
                var file = normalizeShopFile(fileName);
                var name = (parent ? "+" : "-") + file.getAbsolutePath();
                if (!shopMap.containsKey(name)) {
                    loadNewShop(name, file, parent);
                }
                player.openInventory(shopMap.get(name));
            } else {
                player.openInventory(shopMap.get("default"));
            }
        } catch (Throwable ignored) {
            player.sendMessage("[BW] Your shop.yml/shop.groovy is invalid! Check it out or contact us on Discord.");
        }
    }

    public File normalizeShopFile(String name) {
        if (name.split("\\.").length > 1) {
            return dataFolder.resolve(name).toFile();
        }

        var fileg = dataFolder.resolve(name + ".groovy").toFile();
        if (fileg.exists()) {
            return fileg;
        }
        return dataFolder.resolve(name + ".yml").toFile();
    }

    public void onGeneratingItem(ItemRenderEvent event) {
        var itemInfo = event.getItem();
        var item = itemInfo.getStack();
        var player = event.getPlayer().as(Player.class);
        var game = Main.getPlayerGameProfile(player).getGame();
        var prices = itemInfo.getOriginal().getPrices();
        if (!prices.isEmpty()) {
            // TODO: multi-price feature
            var priceObject = prices.get(0);
            var price = priceObject.getAmount();
            var type = Main.getSpawnerType(priceObject.getCurrency().toLowerCase());
            if (type == null) {
                return;
            }

            var enabled = itemInfo.getFirstPropertyByName("generateLore")
                    .map(property -> property.getPropertyData().getBoolean())
                    .orElseGet(() -> mainConfig.node("lore", "generate-automatically").getBoolean(true));

            if (enabled) {
                var loreText = itemInfo.getFirstPropertyByName("generatedLoreText")
                        .map(property -> property.getPropertyData().childrenList().stream().map(ConfigurationNode::getString))
                        .orElseGet(() -> mainConfig.node("lore", "text").childrenList().stream().map(ConfigurationNode::getString))
                        .filter(Objects::nonNull)
                        .map(s -> s.replaceAll("%price%", Integer.toString(price))
                                .replaceAll("%resource%", type.getItemName())
                                .replaceAll("%amount%", Integer.toString(item.getAmount())))
                        .map(AdventureHelper::toComponent)
                        .collect(Collectors.toList());

                item.getLore().addAll(loreText);
            }
        }
        final var preScanEvent = new BedwarsPrePropertyScanEvent(event);
        Bukkit.getServer().getPluginManager().callEvent(preScanEvent);
        if (preScanEvent.isCancelled()) return;

        itemInfo.getProperties().forEach(property -> {
            if (property.hasName()) {
                var converted = ConfigurateUtils.raw(property.getPropertyData());
                if (!(converted instanceof Map)) {
                    converted = DumpCommand.nullValuesAllowingMap("value", converted);
                }

                //noinspection unchecked
                var applyEvent = new BedwarsApplyPropertyToDisplayedItem(game,
                        player, item.as(ItemStack.class), property.getPropertyName(), (Map<String, Object>) converted);
                Bukkit.getServer().getPluginManager().callEvent(applyEvent);

                event.setStack(ItemFactory.build(applyEvent.getStack()).orElse(item));
            }
        });

        final var postScanEvent = new BedwarsPostPropertyScanEvent(event);
        Bukkit.getServer().getPluginManager().callEvent(postScanEvent);
    }

    public void onPreAction(PreClickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        var player = event.getPlayer().as(Player.class);
        if (!Main.isPlayerInGame(player)) {
            event.setCancelled(true);
        }

        if (Main.getPlayerGameProfile(player).isSpectator) {
            event.setCancelled(true);
        }
    }

    public void onShopTransaction(OnTradeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getItem().getFirstPropertyByName("upgrade").isPresent()) {
            handleUpgrade(event);
        } else {
            handleBuy(event);
        }
    }

    @EventHandler
    public void onApplyPropertyToBoughtItem(BedwarsApplyPropertyToItem event) {
        if (event.getPropertyName().equalsIgnoreCase("applycolorbyteam")
                || event.getPropertyName().equalsIgnoreCase("transform::applycolorbyteam")) {
            Player player = event.getPlayer();
            CurrentTeam team = (CurrentTeam) event.getGame().getTeamOfPlayer(player);

            if (mainConfig.node("automatic-coloring-in-shop").getBoolean()) {
                event.setStack(Main.applyColor(team.teamInfo.color, event.getStack()));
            }
        }
    }

    @SneakyThrows
    private void loadDefault(InventorySet inventorySet) {
        inventorySet.getMainSubInventory().dropContents();
        inventorySet.getMainSubInventory().getWaitingQueue().add(Include.of(Path.of(ShopInventory.class.getResource("/shop.yml").toURI())));
        inventorySet.getMainSubInventory().process();
    }

    private void loadNewShop(String name, File file, boolean useParent) {
        var inventorySet = SimpleInventoriesCore.builder()
                .genericShop(true)
                .genericShopPriceTypeRequired(true)
                .animationsEnabled(true)
                .categoryOptions(localOptionsBuilder ->
                    localOptionsBuilder
                            .backItem(mainConfig.readDefinedItem("shopback", "BARRIER"), itemBuilder ->
                                itemBuilder.name(Message.of(LangKeys.IN_GAME_SHOP_SHOP_BACK).asComponent())
                            )
                            .pageBackItem(mainConfig.readDefinedItem("pageback", "ARROW"), itemBuilder ->
                                itemBuilder.name(Message.of(LangKeys.IN_GAME_SHOP_PAGE_BACK).asComponent())
                            )
                            .pageForwardItem(mainConfig.readDefinedItem("pageforward", "BARRIER"), itemBuilder ->
                                itemBuilder.name(Message.of(LangKeys.IN_GAME_SHOP_PAGE_FORWARD).asComponent())
                            )
                            .cosmeticItem(mainConfig.readDefinedItem("shopcosmetic", "AIR"))
                            .rows(mainConfig.node("shop", "rows").getInt(4))
                            .renderActualRows(mainConfig.node("shop", "render-actual-rows").getInt(6))
                            .renderOffset(mainConfig.node("shop", "render-offset").getInt(9))
                            .renderHeaderStart(mainConfig.node("shop", "render-header-start").getInt(0))
                            .renderFooterStart(mainConfig.node("shop", "render-footer-start").getInt(45))
                            .itemsOnRow(mainConfig.node("shop", "items-on-row").getInt(9))
                            .showPageNumber(mainConfig.node("shop", "show-page-numbers").getBoolean(true))
                            .inventoryType(mainConfig.node("shop", "inventory-type").getString("CHEST"))
                            .prefix(Message.of(LangKeys.IN_GAME_SHOP_NAME).asComponent())
                )

                // old shop format compatibility
                .variableToProperty("upgrade", "upgrade")
                .variableToProperty("generate-lore", "generateLore")
                .variableToProperty("generated-lore-text", "generatedLoreText")
                .variableToProperty("currency-changer", "currencyChanger")

                .render(this::onGeneratingItem)
                .preClick(this::onPreAction)
                .buy(this::onShopTransaction)
                .define("team", (key, player, playerItemInfo, arguments) -> {
                    GamePlayer gPlayer = Main.getPlayerGameProfile(player.as(Player.class));
                    CurrentTeam team = gPlayer.getGame().getPlayerTeam(gPlayer);
                    if (arguments.length > 0) {
                        String fa = arguments[0];
                        switch (fa) {
                            case "color":
                                return team.teamInfo.color.name();
                            case "chatcolor":
                                return team.teamInfo.color.chatColor.toString();
                            case "maxplayers":
                                return Integer.toString(team.teamInfo.maxPlayers);
                            case "players":
                                return Integer.toString(team.players.size());
                            case "hasBed":
                                return Boolean.toString(team.isBed);
                        }
                    }
                    return team.getName();
                })
                .define("spawner", (key, player, playerItemInfo, arguments) -> {
                    GamePlayer gPlayer = Main.getPlayerGameProfile(player.as(Player.class));
                    Game game = gPlayer.getGame();
                    if (arguments.length > 2) {
                        String upgradeBy = arguments[0];
                        String upgrade = arguments[1];
                        UpgradeStorage upgradeStorage = UpgradeRegistry.getUpgrade("spawner");
                        if (upgradeStorage == null) {
                            return null;
                        }
                        List<Upgrade> upgrades = null;
                        switch (upgradeBy) {
                            case "name":
                                upgrades = upgradeStorage.findItemSpawnerUpgrades(game, upgrade);
                                break;
                            case "team":
                                upgrades = upgradeStorage.findItemSpawnerUpgrades(game, game.getPlayerTeam(gPlayer));
                                break;
                        }

                        if (upgrades != null && !upgrades.isEmpty()) {
                            String what = "level";
                            if (arguments.length > 3) {
                                what = arguments[2];
                            }
                            double heighest = Double.MIN_VALUE;
                            switch (what) {
                                case "level":
                                    for (Upgrade upgrad : upgrades) {
                                        if (upgrad.getLevel() > heighest) {
                                            heighest = upgrad.getLevel();
                                        }
                                    }
                                    return String.valueOf(heighest);
                                case "initial":
                                    for (Upgrade upgrad : upgrades) {
                                        if (upgrad.getInitialLevel() > heighest) {
                                            heighest = upgrad.getInitialLevel();
                                        }
                                    }
                                    return String.valueOf(heighest);
                            }
                        }
                    }
                    return "";
                })
                .call(categoryBuilder -> {
                    final var includeEvent = new BedwarsStoreIncludeEvent(categoryBuilder, name, file, useParent);
                    Bukkit.getServer().getPluginManager().callEvent(includeEvent);
                    if (includeEvent.isCancelled()) {
                        return;
                    }
                    if (useParent) {
                        var shopFileName = "shop.yml";
                        if (mainConfig.node("turnOnExperimentalGroovyShop").getBoolean(false)) {
                            shopFileName = "shop.groovy";
                        }
                        categoryBuilder.include(shopFileName);
                    }

                    if (file != null) {
                        categoryBuilder.include(Include.of(file));
                    }

                })
                .getInventorySet();

        try {
            inventorySet.getMainSubInventory().process();
        } catch (Exception ex) {
            Debug.warn("Wrong shop.yml/shop.groovy configuration!", true);
            Debug.warn("Check validity of your YAML/Groovy!", true);
            ex.printStackTrace();
            loadDefault(inventorySet);
        }

        shopMap.put(name, inventorySet);
    }

    private static String getNameOrCustomNameOfItem(Item item) {
        try {
            if (item.getDisplayName() != null) {
                return AdventureHelper.toLegacy(item.getDisplayName());
            }
            if (item.getLocalizedName() != null) {
                return AdventureHelper.toLegacy(item.getLocalizedName());
            }
        } catch (Throwable ignored) {
        }

        var normalItemName = item.getMaterial().getPlatformName().replace("_", " ").toLowerCase();
        var sArray = normalItemName.split(" ");
        var stringBuilder = new StringBuilder();

        for (var s : sArray) {
            stringBuilder.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(" ");
        }
        return stringBuilder.toString().trim();
    }

    private void handleBuy(OnTradeEvent event) {
        var player = event.getPlayer().as(Player.class);
        var game = Main.getPlayerGameProfile(player).getGame();
        var clickType = event.getClickType();
        var itemInfo = event.getItem();

        // TODO: multi-price feature
        var price = event.getPrices().get(0);
        ItemSpawnerType type = Main.getSpawnerType(price.getCurrency().toLowerCase());

        var newItem = event.getStack();

        var amount = newItem.getAmount();
        var priceAmount = price.getAmount();
        int inInventory = 0;

        var currencyChanger = itemInfo.getFirstPropertyByName("currencyChanger");
        if (currencyChanger.isPresent()) {
            var changeItemToName = currencyChanger.get().getPropertyData().getString();
            ItemSpawnerType changeItemType;
            if (changeItemToName == null) {
                return;
            }

            changeItemType = Main.getSpawnerType(changeItemToName.toLowerCase());
            if (changeItemType == null) {
                return;
            }

            newItem = ItemFactory.build(changeItemType.getStack()).orElse(newItem);
        }

        var originalMaxStackSize = newItem.getMaterial().as(Material.class).getMaxStackSize();
        if (clickType.isShiftClick() && originalMaxStackSize > 1) {
            double priceOfOne = (double) priceAmount / amount;
            double maxStackSize;
            int finalStackSize;

            for (ItemStack itemStack : player.getInventory().getStorageContents()) {
                if (itemStack != null && itemStack.isSimilar(type.getStack())) {
                    inInventory = inInventory + itemStack.getAmount();
                }
            }
            if (mainConfig.node("sell-max-64-per-click-in-shop").getBoolean()) {
                maxStackSize = Math.min(inInventory / priceOfOne, originalMaxStackSize);
            } else {
                maxStackSize = inInventory / priceOfOne;
            }

            finalStackSize = (int) maxStackSize;
            if (finalStackSize > amount) {
                priceAmount = (int) (priceOfOne * finalStackSize);
                newItem.setAmount(finalStackSize);
                amount = finalStackSize;
            }
        }

        var materialItem = ItemFactory.build(type.getStack(priceAmount)).orElseThrow();
        if (event.hasPlayerInInventory(materialItem)) {
            final var prePurchaseEvent = new BedwarsStorePrePurchaseEvent(event, PurchaseType.NORMAL_ITEM, materialItem, type, newItem);
            Bukkit.getServer().getPluginManager().callEvent(prePurchaseEvent);
            if (prePurchaseEvent.isCancelled()) {
                return;
            }
            Map<String, Object> permaItemPropertyData = new HashMap<>();
            for (var property : itemInfo.getProperties()) {
                var converted = ConfigurateUtils.raw(property.getPropertyData());
                if (!(converted instanceof Map)) {
                    converted = DumpCommand.nullValuesAllowingMap("value", converted);
                }
                //noinspection unchecked
                var propertyData = (Map<String, Object>) converted;
                if (property.hasName()) {
                    var applyEvent = new BedwarsApplyPropertyToBoughtItem(game, player,
                            newItem.as(ItemStack.class), property.getPropertyName(), propertyData);
                    Bukkit.getServer().getPluginManager().callEvent(applyEvent);

                    newItem = ItemFactory.build(applyEvent.getStack()).orElse(newItem);
                }
                // Checks if the player is buying a permanent item. Setting name to empty string to prevent other listeners from erroring out.
                else if (propertyData.get(PermaItemListener.getPermItemPropKey()) != null) {
                    permaItemPropertyData = propertyData;
                }
            }

            if (!permaItemPropertyData.isEmpty()) {
                BedwarsApplyPropertyToBoughtItem applyEvent = new BedwarsApplyPropertyToBoughtItem(game, player,
                        newItem.as(ItemStack.class), "", permaItemPropertyData);
                Bukkit.getServer().getPluginManager().callEvent(applyEvent);
            }

            event.sellStack(materialItem);
            List<Item> notFit = event.buyStack(newItem);
            if (!notFit.isEmpty()) {
                notFit.forEach(stack -> player.getLocation().getWorld().dropItem(player.getLocation(), stack.as(ItemStack.class)));
            }

            if (!mainConfig.node("removePurchaseMessages").getBoolean()) {
                Message.of(LangKeys.IN_GAME_SHOP_BUY_SUCCESS)
                        .prefixOrDefault(game.getCustomPrefixComponent())
                        .placeholder("item", AdventureHelper.toComponent(amount + "x " + getNameOrCustomNameOfItem(newItem)))
                        .placeholder("material", AdventureHelper.toComponent(priceAmount + " " + type.getItemName()))
                        .send(event.getPlayer());
            }
            Sounds.playSound(player, player.getLocation(),
                    mainConfig.node("sounds", "item_buy").getString(), Sounds.ENTITY_ITEM_PICKUP, 1, 1);

            final var postPurchaseEvent = new BedwarsStorePostPurchaseEvent(event, PurchaseType.NORMAL_ITEM);
            Bukkit.getServer().getPluginManager().callEvent(postPurchaseEvent);
        } else {
            final var purchaseFailedEvent = new BedwarsPurchaseFailedEvent(event, PurchaseType.NORMAL_ITEM);
            Bukkit.getServer().getPluginManager().callEvent(purchaseFailedEvent);
            if (purchaseFailedEvent.isCancelled()) return;

            if (!mainConfig.node("removePurchaseMessages").getBoolean()) {
                Message.of(LangKeys.IN_GAME_SHOP_BUY_FAILED)
                        .prefixOrDefault(game.getCustomPrefixComponent())
                        .placeholder("item", AdventureHelper.toComponent(amount + "x " + getNameOrCustomNameOfItem(newItem)))
                        .placeholder("material", AdventureHelper.toComponent(priceAmount + " " + type.getItemName()))
                        .send(event.getPlayer());
            }
        }
    }

    private void handleUpgrade(OnTradeEvent event) {
        var player = event.getPlayer().as(Player.class);
        var game = Main.getPlayerGameProfile(player).getGame();
        var itemInfo = event.getItem();

        // TODO: multi-price feature
        var price = event.getPrices().get(0);
        ItemSpawnerType type = Main.getSpawnerType(price.getCurrency().toLowerCase());

        var priceAmount = price.getAmount();

        var upgrade = itemInfo.getFirstPropertyByName("upgrade").orElseThrow();
        var itemName = upgrade.getPropertyData().node("shop-name").getString("UPGRADE");
        var entities = upgrade.getPropertyData().node("entities").childrenList();

        boolean sendToAll = false;
        boolean isUpgrade = true;
        var materialItem = ItemFactory.build(type.getStack(priceAmount)).orElseThrow();

        if (event.hasPlayerInInventory(materialItem)) {
            final var upgradePurchasedEvent  = new BedwarsStorePrePurchaseEvent(event, PurchaseType.UPGRADES, materialItem, type, null);
            Bukkit.getServer().getPluginManager().callEvent(upgradePurchasedEvent);
            if (upgradePurchasedEvent.isCancelled()) return;

            event.sellStack(materialItem);
            for (var entity : entities) {
                var configuredType = entity.node("type").getString();
                if (configuredType == null) {
                    return;
                }

                var upgradeStorage = UpgradeRegistry.getUpgrade(configuredType);
                if (upgradeStorage != null) {

                    // TODO: Learn SimpleGuiFormat upgrades pre-parsing and automatic renaming old
                    // variables
                    var team = game.getTeamOfPlayer(player);
                    double addLevels = entity.node("add-levels").getDouble(entity.node("levels").getDouble(0));
                    /* You shouldn't use it in entities */
                    itemName = entity.node("shop-name").getString(itemName);
                    sendToAll = entity.node("notify-team").getBoolean();

                    List<Upgrade> upgrades = new ArrayList<>();

                    var spawnerNameNode = entity.node("spawner-name");
                    var spawnerTypeNode = entity.node("spawner-type");
                    var teamUpgradeNode = entity.node("team-upgrade");
                    var customNameNode = entity.node("customName");

                    if (!spawnerNameNode.empty()) {
                        String customName = spawnerNameNode.getString();
                        upgrades = upgradeStorage.findItemSpawnerUpgrades(game, customName);
                    } else if (!spawnerTypeNode.empty()) {
                        String mapSpawnerType = spawnerTypeNode.getString();
                        ItemSpawnerType spawnerType = Main.getSpawnerType(mapSpawnerType);

                        upgrades = upgradeStorage.findItemSpawnerUpgrades(game, team, spawnerType);
                    } else if (!teamUpgradeNode.empty()) {
                        boolean upgradeAllSpawnersInTeam = teamUpgradeNode.getBoolean();

                        if (upgradeAllSpawnersInTeam) {
                            upgrades = upgradeStorage.findItemSpawnerUpgrades(game, team);
                        }

                    } else if (!customNameNode.empty()) { // Old configuration
                        String customName = customNameNode.getString();
                        upgrades = upgradeStorage.findItemSpawnerUpgrades(game, customName);
                    } else {
                        isUpgrade = false;
                        Debug.warn("[BedWars]> Upgrade configuration is invalid.");
                    }

                    if (isUpgrade) {
                        BedwarsUpgradeBoughtEvent bedwarsUpgradeBoughtEvent = new BedwarsUpgradeBoughtEvent(game,
                                upgradeStorage, upgrades, player, addLevels);
                        Bukkit.getPluginManager().callEvent(bedwarsUpgradeBoughtEvent);

                        if (bedwarsUpgradeBoughtEvent.isCancelled()) {
                            continue;
                        }

                        if (upgrades.isEmpty()) {
                            continue;
                        }

                        for (var anUpgrade : upgrades) {
                            BedwarsUpgradeImprovedEvent improvedEvent = new BedwarsUpgradeImprovedEvent(game,
                                    upgradeStorage, anUpgrade, anUpgrade.getLevel(), anUpgrade.getLevel() + addLevels);
                            Bukkit.getPluginManager().callEvent(improvedEvent);
                        }
                    }
                }

                if (sendToAll) {
                    for (Player player1 : game.getTeamOfPlayer(player).getConnectedPlayers()) {
                        if (!mainConfig.node("removePurchaseMessages").getBoolean()) {
                            Message.of(LangKeys.IN_GAME_SHOP_BUY_SUCCESS)
                                    .prefixOrDefault(game.getCustomPrefixComponent())
                                    .placeholder("item", AdventureHelper.toComponent(itemName))
                                    .placeholder("material", AdventureHelper.toComponent(priceAmount + " " + type.getItemName()))
                                    .send(event.getPlayer());
                        }
                        Sounds.playSound(player1, player1.getLocation(),
                                mainConfig.node("sounds", "upgrade_buy").getString(),
                                Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    }
                } else {
                    if (!mainConfig.node("removePurchaseMessages").getBoolean()) {
                        Message.of(LangKeys.IN_GAME_SHOP_BUY_SUCCESS)
                                .prefixOrDefault(game.getCustomPrefixComponent())
                                .placeholder("item", AdventureHelper.toComponent(itemName))
                                .placeholder("material", AdventureHelper.toComponent(priceAmount + " " + type.getItemName()))
                                .send(event.getPlayer());
                    }
                    Sounds.playSound(player, player.getLocation(),
                            mainConfig.node("sounds", "upgrade_buy").getString(),
                            Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
            }
            final var postPurchaseEvent = new BedwarsStorePostPurchaseEvent(event, PurchaseType.UPGRADES);
            Bukkit.getServer().getPluginManager().callEvent(postPurchaseEvent);
        } else {
            final var purchaseFailedEvent = new BedwarsPurchaseFailedEvent(event, PurchaseType.UPGRADES);
            Bukkit.getServer().getPluginManager().callEvent(purchaseFailedEvent);
            if (purchaseFailedEvent.isCancelled()) return;
            if (!mainConfig.node("removePurchaseMessages").getBoolean()) {
                Message.of(LangKeys.IN_GAME_SHOP_BUY_FAILED)
                        .prefixOrDefault(game.getCustomPrefixComponent())
                        .placeholder("item", AdventureHelper.toComponent(itemName))
                        .placeholder("material", AdventureHelper.toComponent(priceAmount + " " + type.getItemName()))
                        .send(event.getPlayer());
            }
        }
    }
}
