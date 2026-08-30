<img src="assets/logo.png" alt="MOTDGate logo" width="256">

# MOTDGate

MOTDGate is a small Paper plugin that hides a server's normal MOTD from addresses that have never
successfully joined. It is intended to let another MOTD plugin continue managing the normal server
list response while returning a safe, configurable response to ping-only scanners.

## Behavior

- A new address sees the configured safe MOTD, and by default no favicon, player sample, or player
  count, in the server list.
- After a player at that address successfully joins, future pings from it are left untouched.
- The ping listener runs at `HIGHEST` priority and changes only configured response fields. Protocol
  and version information are not changed.
- GameSpy 4 basic and full queries are gated when `enable-query=true`. Because that protocol does
  not support Adventure components, its safe MOTD is the plain-text form of `motd`. When enabled,
  the player-count and player-sample settings report `0/0` and no players, respectively.
- There are no commands or permissions.

Configure the response in `plugins/MOTDGate/config.yml` using
[MiniMessage](https://docs.papermc.io/adventure/minimessage/format/):

```yaml
motd: "A Minecraft Server"

# If true, use plugins/MOTDGate/icon.png.
# If the file is missing, send no favicon.
replace-icon: true

# Hide the player-list hover/sample (and GameSpy full-query player list) from unknown addresses.
hide-player-sample: true

# Hide online/max player counts, showing ??? (or 0/0 in GameSpy queries).
hide-player-count: true
```

Restart the server after changing the configuration. To forget all known addresses, stop the server
and delete `plugins/MOTDGate/known-addresses.txt`.

## Stored data

MOTDGate stores only keyed HMAC-SHA-256 values derived from IP address bytes. It does not store raw
addresses, ports, player names, UUIDs, or timestamps. A random key is generated at
`plugins/MOTDGate/secret.key`; keep that file private and do not delete it unless you also want to
invalidate all known-address records. Records do not expire automatically.

The HMACs are a data-minimization measure, not a guarantee that they are legally anonymous data.

## Limitations

MOTDGate is not access control and does not prevent clients from connecting. Addresses can change,
and multiple people behind NAT share one public address. Consequently, one successful join recognizes
that whole public address, while a player whose address changes will see the safe MOTD until joining
again.
