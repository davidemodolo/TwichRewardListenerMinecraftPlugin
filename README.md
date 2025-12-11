# TwitchRewardListener

A Minecraft Spigot plugin that listens to Twitch chat messages (specifically from StreamElements) and rewards players in-game when they redeem rewards.

## Features

-   **Twitch Integration**: Connects to Twitch chat to listen for reward redemptions.
-   **Configurable Rewards**: Define chests and rewards in `config.yml` without recompiling.
-   **Three Reward Types**:
    -   **Fixed**: Gives a specific set of items.
    -   **Random Quantity**: Gives items with a random amount (0 to max).
    -   **Random Item**: Gives one item from a list based on probability.
-   **StreamElements Support**: Designed to work with StreamElements bot messages.

## Installation

1.  Download the latest release.
2.  Place the `.jar` file in your server's `plugins` folder.
3.  Restart the server to generate the default `config.yml`.
4.  Configure the plugin (see below).
5.  Restart the server or reload the plugin.

## Configuration

### 1. Twitch OAuth Token
You need a Twitch OAuth token for the bot account (or your own account) to read chat.
1.  Go to [https://twitchtokengenerator.com/](https://twitchtokengenerator.com/)
2.  Select the `chat:read` scope.
3.  Copy the "Access Token" (it should look like `oauth:xyz...`).
4.  Paste it into `config.yml` under `twitch_oauth_token`.

### 2. StreamElements Setup
Create a custom command in StreamElements (e.g., `!chestDiamond`) that triggers when a user redeems a reward.
The response message must match the pattern defined in `config.yml`.

**Default Pattern:** `^.* opened a (.*) for (.*)$`

**Example StreamElements Command:**
-   **Command Name:** `!chestDiamond`
-   **Response:** `$(user) opened a chestDiamond for $(1)`

*Usage in Chat:* `!chestDiamond Steve`
*Bot Output:* `TwitchUser opened a chestDiamond for Steve`

### 3. Defining Chests
Edit `config.yml` to define your chests.

```yaml
chests:
  # Type: Fixed - Gives all items
  chestStarter:
    type: fixed
    items:
      - material: IRON_PICKAXE
        amount: 1
      - material: COOKED_BEEF
        amount: 16

  # Type: Random Quantity - Random amount for each item (0 to max)
  chestWood:
    type: random_quantity
    items:
      - material: OAK_LOG
        max_amount: 10
      - material: STICK
        max_amount: 5

  # Type: Random Item - Picks ONE item based on probability
  chestLucky:
    type: random_item
    items:
      - material: diamond_sword
        amount: 1
        probability: 10.0
        enchantments:
          - name: sharpness
            level: 5
          - name: unbreaking
            level: 3
      - material: coal
        amount: 5
        probability: 90.0
```

## Commands

-   `/twitchreward reload`: Reloads the configuration from `config.yml`.
    -   **Permission**: `twitchreward.admin` (Default: OP)

## Author

**Davide Modolo**
