package xyz.jpenilla.motdgate;

import com.destroystokyo.paper.event.server.GS4QueryEvent;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MOTDGate extends JavaPlugin implements Listener {
  private static final Component FALLBACK_MOTD = Component.text("A Minecraft Server");

  private Component unknownMotd = FALLBACK_MOTD;
  private String unknownQueryMotd =
      PlainTextComponentSerializer.plainText().serialize(FALLBACK_MOTD);
  private @Nullable KnownAddressStore knownAddresses;

  @Override
  public void onEnable() {
    this.unknownMotd = this.loadUnknownMotd();
    this.unknownQueryMotd = PlainTextComponentSerializer.plainText().serialize(this.unknownMotd);
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
  public void onServerListPing(final ServerListPingEvent event) {
    if (!this.knownAddresses().contains(event.getAddress())) {
      event.motd(this.unknownMotd);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onQuery(final GS4QueryEvent event) {
    if (!this.knownAddresses().contains(event.getQuerierAddress())) {
      event.setResponse(
          event.getResponse().toBuilder().motd(this.unknownQueryMotd).build());
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
      this.saveDefaultConfig();
      final String configured = this.getConfig().getString("unknown-motd");
      if (configured == null) {
        this.getLogger().warning("Missing 'unknown-motd' in config.yml; using the safe default");
        return FALLBACK_MOTD;
      }
      return MiniMessage.miniMessage().deserialize(configured);
    } catch (final RuntimeException exception) {
      this.getLogger()
          .log(Level.SEVERE, "Could not load unknown MOTD; using the safe default", exception);
      return FALLBACK_MOTD;
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
