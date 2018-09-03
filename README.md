# BungeeChannelApi

[Javadocs](https://ci.codemc.org/job/leonardosnt/job/BungeeChannelApi/javadoc)

##### Development builds:

[![Build Status](https://ci.codemc.org/buildStatus/icon?job=leonardosnt/BungeeChannelApi)](https://ci.codemc.org/job/leonardosnt/job/BungeeChannelApi)

##### Maven dependency:

How to include BungeeChannelApi into your maven project:

```xml
    <repositories>
        <repository>
            <id>codemc-repo</id>
            <url>https://repo.codemc.org/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>io.github.leonardosnt</groupId>
            <artifactId>bungeechannelapi</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
```

Remember to include/relocate the library into your final jar, example:

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.1.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <relocations>
                        <relocation>
                            <pattern>io.github.leonardosnt.bungeechannelapi</pattern>
                            <shadedPattern>YOUR.PLUGIN.PACKAGE.libs.bungeechannelapi</shadedPattern>
                        </relocation>
                    </relocations>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

##### Some examples:

BungeeChannelApi uses [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)'s, so make sure you know how it works before using this.

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

#### Custom subchannels/messages using [Forward](https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/#forward) 

Send:
```java
byte[] ourData = "Hello World".getBytes();
api.forward("serverName", "example", ourData);
```

Listen:
```java
// global listener (for all subchannels) 
api.registerForwardListener((channelName, player, data) -> {
  Bukkit.broadcastMessage(channelName + " -> " + Arrays.toString(data));
});

// specific channel
api.registerForwardListener("example", (channelName, player, data) -> {
  // in this case channelName is "example"
  Bukkit.broadcastMessage("Message sent using forward: " + new String(data));
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
