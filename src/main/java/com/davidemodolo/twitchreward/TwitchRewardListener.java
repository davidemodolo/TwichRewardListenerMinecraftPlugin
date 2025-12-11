package com.davidemodolo.twitchreward;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitchRewardListener extends JavaPlugin {

    private TwitchClient twitchClient;
    private String botUsername;
    private Pattern messagePattern;
    private Map<String, Chest> chests;

    @Override
    public void onEnable() {
        // 1. Save Default Config
        saveDefaultConfig();
        
        // 2. Load config
        loadConfigValues();

        String token = getConfig().getString("twitch_oauth_token");
        String channelName = getConfig().getString("channel_name");

        if (token == null || token.equals("oauth:YOUR_TOKEN_HERE")) {
            getLogger().severe("Please set your Twitch OAuth Token in config.yml!");
            return;
        }

        // 3. Initialize Twitch Client asynchronously
        new Thread(() -> {
            try {
                OAuth2Credential credential = new OAuth2Credential("twitch", token);

                twitchClient = TwitchClientBuilder.builder()
                        .withEnableChat(true)
                        .withChatAccount(credential)
                        .build();

                twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, this::onChatMessage);

                twitchClient.getChat().joinChannel(channelName);
                getLogger().info("Connected to Twitch Chat: " + channelName);

            } catch (Exception e) {
                getLogger().severe("Failed to connect to Twitch: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onDisable() {
        if (twitchClient != null) {
            twitchClient.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("twitchreward")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("twitchreward.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                
                reloadConfig();
                loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "[TwitchReward] Configuration reloaded!");
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Usage: /twitchreward reload");
            return true;
        }
        return false;
    }

    private void loadConfigValues() {
        botUsername = getConfig().getString("bot_username", "StreamElements");
        String patternString = getConfig().getString("message_pattern", "^.* opened a (.*) for (.*)$");
        try {
            messagePattern = Pattern.compile(patternString);
        } catch (Exception e) {
            getLogger().severe("Invalid regex pattern in config: " + patternString);
            messagePattern = Pattern.compile("^.* opened a (.*) for (.*)$");
        }

        loadChests();
    }

    private void loadChests() {
        chests = new HashMap<>();
        ConfigurationSection chestsSection = getConfig().getConfigurationSection("chests");
        if (chestsSection == null) {
            getLogger().warning("No chests section found in config.yml.");
            return;
        }

        for (String key : chestsSection.getKeys(false)) {
            String type = chestsSection.getString(key + ".type");
            
            List<ChestItem> items = new ArrayList<>();
            List<Map<?, ?>> itemList = getConfig().getMapList("chests." + key + ".items");
            
            for (Map<?, ?> itemMap : itemList) {
                String material = (String) itemMap.get("material");
                int amount = itemMap.containsKey("amount") ? (Integer) itemMap.get("amount") : 1;
                int maxAmount = itemMap.containsKey("max_amount") ? (Integer) itemMap.get("max_amount") : 1;
                double probability = itemMap.containsKey("probability") ? ((Number) itemMap.get("probability")).doubleValue() : 100.0;
                
                Map<String, Integer> enchantments = new HashMap<>();
                if (itemMap.containsKey("enchantments")) {
                    List<Map<?, ?>> enchList = (List<Map<?, ?>>) itemMap.get("enchantments");
                    for (Map<?, ?> enchMap : enchList) {
                        String name = (String) enchMap.get("name");
                        int level = (Integer) enchMap.get("level");
                        enchantments.put(name, level);
                    }
                }
                
                items.add(new ChestItem(material, amount, maxAmount, probability, enchantments));
            }
            
            chests.put(key, new Chest(type, items));
        }
        getLogger().info("Loaded " + chests.size() + " chests from config.");
    }

    private void onChatMessage(ChannelMessageEvent event) {
        if (!event.getUser().getName().equalsIgnoreCase(botUsername)) {
            return;
        }
        
        String message = event.getMessage();
        Matcher matcher = messagePattern.matcher(message);

        if (matcher.find()) {
            if (matcher.groupCount() < 2) {
                getLogger().warning("Message matched pattern but didn't have enough groups. Expected 2 (Chest, Player).");
                return;
            }
            
            String chestName = matcher.group(1).trim();
            String playerName = matcher.group(2).trim();
            
            // Clean up names
            chestName = chestName.replaceAll("[^a-zA-Z0-9_]", "");
            playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

            giveChestReward(playerName, chestName);
        }
    }

    private void giveChestReward(String playerName, String chestName) {
        Bukkit.getScheduler().runTask(this, () -> {
            Chest chest = chests.get(chestName);
            if (chest == null) {
                getLogger().warning("Chest not found: " + chestName);
                return;
            }

            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                List<ItemStack> itemsToGive = new ArrayList<>();
                
                switch (chest.type) {
                    case "fixed":
                        for (ChestItem item : chest.items) {
                            ItemStack is = createItemStack(item, item.amount);
                            if (is != null) itemsToGive.add(is);
                        }
                        break;
                    case "random_quantity":
                        for (ChestItem item : chest.items) {
                            int qty = (int) (Math.random() * (item.maxAmount + 1)); // 0 to maxAmount
                            if (qty > 0) {
                                ItemStack is = createItemStack(item, qty);
                                if (is != null) itemsToGive.add(is);
                            }
                        }
                        break;
                    case "random_item":
                        ChestItem selected = selectRandomItem(chest.items);
                        if (selected != null) {
                            ItemStack is = createItemStack(selected, selected.amount);
                            if (is != null) itemsToGive.add(is);
                        }
                        break;
                    default:
                        getLogger().warning("Unknown chest type: " + chest.type);
                        return;
                }

                for (ItemStack is : itemsToGive) {
                    HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(is);
                    // Drop items if inventory is full
                    for (ItemStack drop : leftOver.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                
                getLogger().info("Delivered chest " + chestName + " to " + playerName);
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[Twitch] " + ChatColor.AQUA + playerName + " opened " + chestName + "!");
            } else {
                getLogger().warning("Player " + playerName + " is not online.");
            }
        });
    }

    private ItemStack createItemStack(ChestItem chestItem, int amount) {
        Material mat = Material.matchMaterial(chestItem.material);
        if (mat == null) {
            getLogger().warning("Invalid material: " + chestItem.material);
            return null;
        }
        
        ItemStack itemStack = new ItemStack(mat, amount);
        
        if (chestItem.enchantments != null && !chestItem.enchantments.isEmpty()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                for (Map.Entry<String, Integer> entry : chestItem.enchantments.entrySet()) {
                    Enchantment ench = getEnchantment(entry.getKey());
                    if (ench != null) {
                        meta.addEnchant(ench, entry.getValue(), true);
                    } else {
                        getLogger().warning("Invalid enchantment: " + entry.getKey());
                    }
                }
                itemStack.setItemMeta(meta);
            }
        }
        return itemStack;
    }

    private Enchantment getEnchantment(String name) {
        // Try by key (minecraft:sharpness)
        Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(name.toLowerCase()));
        if (ench != null) return ench;
        
        // Try by name (DAMAGE_ALL) - Legacy support
        return Enchantment.getByName(name.toUpperCase());
    }

    private ChestItem selectRandomItem(List<ChestItem> items) {
        if (items.isEmpty()) return null;
        
        double totalProbability = items.stream().mapToDouble(i -> i.probability).sum();
        double random = Math.random() * totalProbability;
        
        double cumulative = 0;
        for (ChestItem item : items) {
            cumulative += item.probability;
            if (random <= cumulative) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    private static class Chest {
        String type;
        List<ChestItem> items;

        Chest(String type, List<ChestItem> items) {
            this.type = type;
            this.items = items;
        }
    }

    private static class ChestItem {
        String material;
        int amount;
        int maxAmount;
        double probability;
        Map<String, Integer> enchantments;

        ChestItem(String material, int amount, int maxAmount, double probability, Map<String, Integer> enchantments) {
            this.material = material;
            this.amount = amount;
            this.maxAmount = maxAmount;
            this.probability = probability;
            this.enchantments = enchantments;
        }
    }
}