/*
 *  Copyright (C) 2017 leonardosnt (leonardosnt@outlook.com)
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/

package io.github.leonardosnt.bungeechannelapi;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

/**
 * Documentation copied from https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/
 *
 * @author leonardosnt (leonardosnt@outlook.com)
 */
public class BungeeChannelApi {

  private static WeakHashMap<Plugin, BungeeChannelApi> registeredInstances = new WeakHashMap<>();
  
  private final PluginMessageListener messageListener;
  private final Plugin plugin;
  private final Map<String, Queue<CompletableFuture<?>>> callbackMap;
  
  private Map<String, ForwardConsumer> forwardListeners;
  private ForwardConsumer globalForwardListener;
  
  /*
   * Get or create new BungeeChannelApi instance
   */
  public synchronized static BungeeChannelApi of(Plugin plugin) {
    return registeredInstances.compute(plugin, (k, v) -> {
      if (v == null) v = new BungeeChannelApi(plugin);
      return v;
    });
  }
  
  public BungeeChannelApi(Plugin plugin) {
    this.plugin = plugin;
    this.callbackMap = new HashMap<>();
    
    // Prevent dev's from registering multiple channel listeners
    synchronized (registeredInstances) {
      registeredInstances.compute(plugin, (k, oldInstance) -> {
        if (oldInstance != null) oldInstance.unregister();
        return this;
      });
    }
    
    this.messageListener = this::onPluginMessageReceived;
    
    Messenger messenger = Bukkit.getServer().getMessenger();
    messenger.registerOutgoingPluginChannel(plugin, "BungeeCord");
    messenger.registerIncomingPluginChannel(plugin, "BungeeCord", messageListener);
  }
  
  public void registerForwardListener(ForwardConsumer globalListener) {
    this.globalForwardListener = globalListener;
  }
  
  public void registerForwardListener(String targetChannel, ForwardConsumer listener) {
    if (forwardListeners == null) {
      forwardListeners = new HashMap<>();
    }
    synchronized (forwardListeners) {
      forwardListeners.put(targetChannel, listener);
    }
  }
  
  /**
   * Get the amount of players on a certain server, or on ALL the servers.
   *
   * @param serverName the server name of the server to get the player count of, or ALL to get the global player count
   * @return A {@link CompletableFuture} that, when completed, will return 
   *         the amount of players on a certain server, or on ALL the servers.
   */
  public CompletableFuture<Integer> getPlayerCount(String serverName) {
    Player player = getFirstPlayer();
    CompletableFuture<Integer> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("PlayerCount", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("PlayerCount");
    output.writeUTF(serverName);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Get a list of players connected on a certain server, or on ALL the servers.
   *
   * @param serverName the name of the server to get the list of connected players, or ALL for global online player list
   * @return A {@link CompletableFuture} that, when completed, will return a 
   *         list of players connected on a certain server, or on ALL the servers.
   */
  public CompletableFuture<List<String>> getPlayerList(String serverName) {
    Player player = getFirstPlayer();
    CompletableFuture<List<String>> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("PlayerList", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("PlayerList");
    output.writeUTF(serverName);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Get a list of server name strings, as defined in BungeeCord's config.yml
   *
   * @return A {@link CompletableFuture} that, when completed, will return a 
   *         list of server name strings, as defined in BungeeCord's config.yml
   */
  public CompletableFuture<List<String>> getServers() {
    Player player = getFirstPlayer();
    CompletableFuture<List<String>> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("GetServers", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("GetServers");
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Connects a player to said subserver.
   * 
   * @param player the player you want to teleport
   * @param serverName the name of server to connect to, as defined in BungeeCord config.yml
   */
  public void connect(Player player, String serverName) {
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("Connect");
    output.writeUTF(serverName);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
  }
  
  /**
   * Connect a named player to said subserver.
   *
   * @param playerName name of the player to teleport
   * @param server name of server to connect to, as defined in BungeeCord config.yml
   */
  public void connectOther(String playerName, String server) {
    Player player = getFirstPlayer();
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    
    output.writeUTF("ConnectOther");
    output.writeUTF(playerName);
    output.writeUTF(server);
    
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
  }
  
  /**
   * Get the (real) IP of a player.
   * 
   * @param player The player you wish to get the IP of.
   * @return A {@link CompletableFuture} that, when completed, will return the (real) IP of {@code player}.
   */
  public CompletableFuture<InetSocketAddress> getIp(Player player) {
    CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("IP", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("IP");
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Send a message (as in, a chat message) to the specified player.
   * 
   * @param playerName the name of the player to send the chat message
   * @param message the message to send to the player
   */
  public void sendMessage(String playerName, String message) {
    Player player = getFirstPlayer();
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    
    output.writeUTF("Message");
    output.writeUTF(playerName);
    output.writeUTF(message);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
  }
  
  /**
   * Get this server's name, as defined in BungeeCord's config.yml
   * 
   * @return A {@link CompletableFuture} that, when completed, will return 
   *         the {@code server's} name, as defined in BungeeCord's config.yml
   */
  public CompletableFuture<String> getServer() {
    Player player = getFirstPlayer();
    CompletableFuture<String> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("GetServer", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("GetServer");
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Request the UUID of this player
   * 
   * @param player The player whose UUID you requested
   * @return A {@link CompletableFuture} that, when completed, will return the UUID of {@code player}
   */
  public CompletableFuture<String> getUUID(Player player) {
    CompletableFuture<String> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("UUID", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("UUID");
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Request the UUID of any player connected to the BungeeCord proxy
   * 
   * @param playerName the name of the player whose UUID you would like
   * @return A {@link CompletableFuture} that, when completed, will return the UUID of {@code playerName}
   */
  public CompletableFuture<String> getUUID(String playerName) {
    Player player = getFirstPlayer();
    CompletableFuture<String> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("UUIDOther", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("UUIDOther");
    output.writeUTF(playerName);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Request the IP of any server on this proxy
   * 
   * @param serverName the name of the server
   * @return A {@link CompletableFuture} that, when completed, will return the requested ip
   */
  public CompletableFuture<InetSocketAddress> getServerIp(String serverName) {
    Player player = getFirstPlayer();
    CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("ServerIP", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("ServerIP");
    output.writeUTF(serverName);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
    return future;
  }
  
  /**
   * Kick any player on this proxy
   * 
   * @param playerName the name of the player
   * @param kickMessage the reason the player is kicked with
   */
  public void kickPlayer(String playerName, String kickMessage) {
    Player player = getFirstPlayer();
    CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();
    
    synchronized (callbackMap) {
      callbackMap.compute("KickPlayer", this.computeQueueValue(future));
    }
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("KickPlayer");
    output.writeUTF(playerName);
    output.writeUTF(kickMessage);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
  }
  
  /**
   * Send a custom plugin message to said server. This is one of the most useful channels ever.
   * <b>Remember, the sending and receiving server(s) need to have a player online.</b>
   * 
   * @param server the name of the server to send to, 
   *        ALL to send to every server (except the one sending the plugin message), 
   *        or ONLINE to send to every server that's online (except the one sending the plugin message).
   *
   * @param channelName Subchannel for plugin usage.
   * @param data data to send.
   */
  public void forward(String server, String channelName, byte[] data) {
    Player player = getFirstPlayer();
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("Forward");
    output.writeUTF(server);
    output.writeUTF(channelName);
    output.writeShort(data.length);
    output.write(data);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
  }
  
  /**
   * Send a custom plugin message to specific player.
   * 
   * @param playerName the name of the player to send to.
   * @param channelName Subchannel for plugin usage.
   * @param data data to send.
   */
  public void forwardToPlayer(String playerName, String channelName, byte[] data) {
    Player player = getFirstPlayer();
    
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    output.writeUTF("ForwardToPlayer");
    output.writeUTF(playerName);
    output.writeUTF(channelName);
    output.writeShort(data.length);
    output.write(data);
    player.sendPluginMessage(this.plugin, "BungeeCord", output.toByteArray());
  }

  @SuppressWarnings("unchecked")
  private void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!channel.equalsIgnoreCase("BungeeCord")) return;

    ByteArrayDataInput input = ByteStreams.newDataInput(message);
    String subchannel = input.readUTF();
    
    synchronized (callbackMap) {
      Queue<CompletableFuture<?>> callbacks = callbackMap.get(subchannel);
      
      if (callbacks != null) {
        CompletableFuture<?> callback = callbacks.poll();
        
        if (callback == null) {
          return;
        }
        
        switch (subchannel) {
          case "PlayerCount":
            input.readUTF(); // ignore server name
            ((CompletableFuture<Integer>) callback).complete(Integer.valueOf(input.readInt()));
            return;
          
          case "PlayerList":
            input.readUTF(); // ignore server name
            ((CompletableFuture<List<String>>) callback).complete(Arrays.asList(input.readUTF().split(", ")));
            return;
            
          case "GetServers":
            ((CompletableFuture<List<String>>) callback).complete(Arrays.asList(input.readUTF().split(", ")));
            return;
            
          case "GetServer":
            ((CompletableFuture<String>) callback).complete(input.readUTF());
            return;
    
          case "UUID":
            ((CompletableFuture<String>) callback).complete(input.readUTF());
            return;
  
          case "UUIDOther":
            input.readUTF(); // ignore player name
            ((CompletableFuture<String>) callback).complete(input.readUTF());
            return;
            
          case "ServerIP": {
            input.readUTF(); // ignore server name
            String ip = input.readUTF();
            int port = input.readUnsignedShort();
            ((CompletableFuture<InetSocketAddress>) callback).complete(new InetSocketAddress(ip, port));
            return;
          }
            
          case "IP": {
            String ip = input.readUTF();
            int port = input.readInt();
            ((CompletableFuture<InetSocketAddress>) callback).complete(new InetSocketAddress(ip, port));
            return;
          }
          
          default: 
            break;
        }
      } else { // custom channel's (from Forward, ForwardToPlayer)
        short dataLength = input.readShort();
        byte[] data = new byte[dataLength];
        input.readFully(data);
        
        if (globalForwardListener != null) {
          globalForwardListener.accept(subchannel, player, data);
        }
        
        if (forwardListeners != null) {
          synchronized (forwardListeners) {
            ForwardConsumer listener = forwardListeners.get(subchannel);
            if (listener != null) {
              listener.accept(subchannel, player, data);
            }
          }
        }
      }
    }
  }

  /**
   * Unregister message channels.
   */
  public void unregister() {
    Messenger messenger = Bukkit.getServer().getMessenger();
    messenger.unregisterIncomingPluginChannel(plugin, "BungeeCord", messageListener);
    messenger.unregisterOutgoingPluginChannel(plugin);
    callbackMap.clear();
  }
  
  private BiFunction<String, Queue<CompletableFuture<?>>, Queue<CompletableFuture<?>>> computeQueueValue(CompletableFuture<?> queueValue) {
    return (key, value) -> {
      if (value == null) value = new ArrayDeque<CompletableFuture<?>>();
      value.add(queueValue);
      return value;
    };
  }
  
  private Player getFirstPlayer() {
    Player ret = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
    
    if (ret == null) {
      throw new IllegalArgumentException("Bungee Messaging Api requires at least one player online.");
    }
    
    return ret;
  }
  
  @FunctionalInterface
  public interface ForwardConsumer {
    void accept(String channel, Player player, byte[] data);
  }
}