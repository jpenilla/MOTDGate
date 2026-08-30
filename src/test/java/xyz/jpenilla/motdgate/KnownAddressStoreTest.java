package xyz.jpenilla.motdgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@NullMarked
class KnownAddressStoreTest {
  private static final Logger LOGGER = Logger.getLogger(KnownAddressStoreTest.class.getName());

  @TempDir
  Path temporaryDirectory;

  @Test
  void recordsAndReloadsAddressesWithoutStoringPlaintext() throws Exception {
    final InetAddress known = InetAddress.getByName("192.0.2.42");
    final InetAddress unknown = InetAddress.getByName("192.0.2.43");

    try (KnownAddressStore store = KnownAddressStore.open(this.temporaryDirectory, LOGGER)) {
      assertFalse(store.contains(known));
      store.record(known);
      assertTrue(store.contains(known));
      assertFalse(store.contains(unknown));
    }

    final String persisted = Files.readString(
        this.temporaryDirectory.resolve(KnownAddressStore.ADDRESSES_FILE_NAME),
        StandardCharsets.US_ASCII);
    assertFalse(persisted.contains(known.getHostAddress()));

    try (KnownAddressStore store = KnownAddressStore.open(this.temporaryDirectory, LOGGER)) {
      assertTrue(store.contains(known));
      assertFalse(store.contains(unknown));
    }
  }

  @Test
  void secretChangesAddressHashes() throws Exception {
    final InetAddress address = InetAddress.getByName("2001:db8::42");
    final byte[] firstKey = new byte[32];
    final byte[] secondKey = new byte[32];
    secondKey[0] = 1;

    assertNotEquals(
        new AddressHasher(firstKey).hash(address), new AddressHasher(secondKey).hash(address));
  }

  @Test
  void distinguishesIpv4AndIpv6Representations() throws Exception {
    final AddressHasher hasher = new AddressHasher(new byte[32]);
    final InetAddress ipv4 = InetAddress.getByAddress(new byte[] {1, 2, 3, 4});
    final InetAddress ipv6 =
        InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4});

    assertNotEquals(hasher.hash(ipv4), hasher.hash(ipv6));
  }

  @Test
  void recordsAnAddressOnlyOnceUnderConcurrency() throws Exception {
    final InetAddress address = InetAddress.getByName("198.51.100.10");

    try (KnownAddressStore store = KnownAddressStore.open(this.temporaryDirectory, LOGGER);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int task = 0; task < 100; task++) {
        executor.submit(() -> store.record(address));
      }
    }

    final long records;
    try (var lines = Files.lines(
        this.temporaryDirectory.resolve(KnownAddressStore.ADDRESSES_FILE_NAME),
        StandardCharsets.US_ASCII)) {
      records = lines.count();
    }
    assertEquals(1, records);
  }

  @Test
  void ignoresMalformedRecords() throws Exception {
    final InetAddress address = InetAddress.getByName("203.0.113.20");
    final Path records = this.temporaryDirectory.resolve(KnownAddressStore.ADDRESSES_FILE_NAME);

    try (KnownAddressStore store = KnownAddressStore.open(this.temporaryDirectory, LOGGER)) {
      store.record(address);
    }
    Files.writeString(
        records,
        "not-a-valid-record" + System.lineSeparator(),
        StandardCharsets.US_ASCII,
        StandardOpenOption.APPEND);

    try (KnownAddressStore store = KnownAddressStore.open(this.temporaryDirectory, LOGGER)) {
      assertTrue(store.contains(address));
      assertEquals(1, store.size());
    }
  }
}
