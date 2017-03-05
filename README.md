# BungeeChannelApi

[Javadocs](https://leonardosnt.github.io/BungeeChannelApi/io/github/leonardosnt/bungeechannelapi/BungeeChannelApi.html)

##### Some examples:

Instantiate:
```java
// Create new instance for this plugin.
// Same of "new BungeeChannelApi(this);"
BungeeChannelApi api = BungeeChannelApi.of(this);
```

Get online player count:
```java
api.getPlayerCount("ALL")
  .whenComplete((result, error) -> {
    sender.sendMessage("§aThere are " + result + " players online on all servers.");
  });
```

Get a list of players connected on a certain server, or on ALL the servers:
```java
api.getPlayerList("ALL")
  .whenComplete((result, error) -> {
    sender.sendMessage("§aPlayers online: " + result.stream().collect(Collectors.joining(", ")));
  });
```

Send a message to a player:
```java
api.sendMessage("leonardosnt", "§cHello world");
```

Listen "forward" channels:
```java
// global listener (for all custom channels) 
api.registerForwardListener((channelName, player, data) -> {
  Bukkit.broadcastMessage(channelName + " -> " + Arrays.toString(data));
});

// specific channel
api.registerForwardListener("someName", (channelName, player, data) -> {
  // in this case channelName is "someName"
  Bukkit.broadcastMessage(Arrays.toString(data));
});
```
