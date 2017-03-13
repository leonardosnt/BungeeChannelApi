# BungeeChannelApi

[Javadocs](https://leonardosnt.github.io/BungeeChannelApi/io/github/leonardosnt/bungeechannelapi/BungeeChannelApi.html)

##### Some examples:

Instantiate:
```java
// Create new instance for this plugin.
// Same of "new BungeeChannelApi(this);"
BungeeChannelApi api = BungeeChannelApi.of(this); // this = Plugin instance.
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

##### Comparison (without BungeeChannelApi)
This code does the same thing that the first example.
```java

public class Test extends JavaPlugin implements PluginMessageListener {

    @Override
    public void onEnable() {
      this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
      this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
    }

    // Send a message requesting player count
    public void requestPlayerCount() {
      ByteArrayDataOutput out = ByteStreams.newDataOutput();
      out.writeUTF("PlayerCount");
      out.writeUTF("ALL"); // all servers

      Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);

      player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }
    
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
      if (!channel.equals("BungeeCord")) return;
      
      ByteArrayDataInput in = ByteStreams.newDataInput(message);
      String subchannel = in.readUTF();
      
      if (subchannel.equals("PlayerCount")) {
        String server = in.readUTF();
        int count = in.readInt();
        
        // do something with 'count'
      }
    }

}
```
