package xyz.jpenilla.motdgate;

import com.destroystokyo.paper.event.server.GS4QueryEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MOTDGate extends JavaPlugin implements Listener {
  private static final Component FALLBACK_MOTD = Component.text("A Minecraft Server");

  private Component unknownMotd = FALLBACK_MOTD;
  private String unknownQueryMotd =
      PlainTextComponentSerializer.plainText().serialize(FALLBACK_MOTD);
  private boolean replaceIcon;
  private boolean hidePlayerSample;
  private boolean hidePlayerCount;
  private @Nullable CachedServerIcon unknownIcon;
  private @Nullable KnownAddressStore knownAddresses;

  @Override
  public void onEnable() {
    this.saveDefaultConfig();
    this.unknownMotd = this.loadUnknownMotd();
    this.unknownQueryMotd = PlainTextComponentSerializer.plainText().serialize(this.unknownMotd);
    this.replaceIcon = this.getConfig().getBoolean("replace-icon", true);
    this.hidePlayerSample = this.getConfig().getBoolean("hide-player-sample", true);
    this.hidePlayerCount = this.getConfig().getBoolean("hide-player-count", true);
    this.unknownIcon = this.loadUnknownIcon();
    this.knownAddresses = this.openKnownAddresses();
    this.getServer().getPluginManager().registerEvents(this, this);
    this.getLogger()
        .info("MOTDGate enabled with " + this.knownAddresses().size() + " known address(es)");
  }

  @Override
  public void onDisable() {
    final KnownAddressStore knownAddresses = this.knownAddresses;
    if (knownAddresses != null) {
      knownAddresses.close();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onServerListPing(final PaperServerListPingEvent event) {
    if (!this.knownAddresses().contains(event.getAddress())) {
      event.motd(this.unknownMotd);
      if (this.replaceIcon) {
        event.setServerIcon(this.unknownIcon);
      }
      if (this.hidePlayerCount) {
        event.setHidePlayers(true);
      } else if (this.hidePlayerSample) {
        event.getListedPlayers().clear();
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onQuery(final GS4QueryEvent event) {
    if (!this.knownAddresses().contains(event.getQuerierAddress())) {
      final GS4QueryEvent.QueryResponse.Builder response =
          event.getResponse().toBuilder().motd(this.unknownQueryMotd);
      if (this.hidePlayerCount) {
        response.currentPlayers(0).maxPlayers(0).clearPlayers();
      } else if (this.hidePlayerSample) {
        response.clearPlayers();
      }
      event.setResponse(response.build());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(final PlayerJoinEvent event) {
    final InetSocketAddress socketAddress = event.getPlayer().getAddress();
    if (socketAddress == null) {
      return;
    }

    final InetAddress address = socketAddress.getAddress();
    if (address != null) {
      this.knownAddresses().record(address);
    }
  }

  private Component loadUnknownMotd() {
    try {
      final String configured = this.getConfig().getString("motd");
      if (configured == null) {
        this.getLogger().warning("Missing 'motd' in config.yml; using the safe default");
        return FALLBACK_MOTD;
      }
      return MiniMessage.miniMessage().deserialize(configured);
    } catch (final RuntimeException exception) {
      this.getLogger()
          .log(Level.SEVERE, "Could not load unknown MOTD; using the safe default", exception);
      return FALLBACK_MOTD;
    }
  }

  private @Nullable CachedServerIcon loadUnknownIcon() {
    if (!this.replaceIcon) {
      return null;
    }

    final Path icon = this.getDataFolder().toPath().resolve("icon.png");
    if (!Files.isRegularFile(icon)) {
      return null;
    }

    try {
      return this.getServer().loadServerIcon(icon.toFile());
    } catch (final Exception exception) {
      this.getLogger().log(Level.WARNING, "Could not load icon.png; sending no favicon", exception);
      return null;
    }
  }

  private KnownAddressStore openKnownAddresses() {
    final Path dataDirectory = this.getDataFolder().toPath();
    try {
      return KnownAddressStore.open(dataDirectory, this.getLogger());
    } catch (final Exception exception) {
      this.getLogger()
          .log(
              Level.SEVERE,
              "Could not load the address store. All addresses will be treated as unknown and "
                  + "new records will not survive a restart.",
              exception);
      return KnownAddressStore.inMemory(this.getLogger());
    }
  }

  private KnownAddressStore knownAddresses() {
    return Objects.requireNonNull(this.knownAddresses, "this.knownAddresses");
  }
}
