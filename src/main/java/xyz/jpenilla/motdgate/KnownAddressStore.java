package xyz.jpenilla.motdgate;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class KnownAddressStore implements AutoCloseable {
  static final String ADDRESSES_FILE_NAME = "known-addresses.txt";
  static final String SECRET_FILE_NAME = "secret.key";

  private static final int KEY_BYTES = 32;
  private static final int ENCODED_HASH_LENGTH = 43;
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  private final @Nullable Path addressesFile;
  private final AddressHasher hasher;
  private final Set<String> hashes;
  private final @Nullable ExecutorService writer;
  private final Logger logger;
  private final AtomicBoolean persistenceErrorLogged = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  private KnownAddressStore(
      final @Nullable Path addressesFile,
      final byte[] key,
      final Set<String> hashes,
      final @Nullable ExecutorService writer,
      final Logger logger) {
    this.addressesFile = addressesFile;
    this.hasher = new AddressHasher(key);
    this.hashes = hashes;
    this.writer = writer;
    this.logger = logger;
  }

  static KnownAddressStore open(final Path directory, final Logger logger) throws IOException {
    Files.createDirectories(directory);
    final byte[] key = loadOrCreateKey(directory.resolve(SECRET_FILE_NAME));
    final Path addressesFile = directory.resolve(ADDRESSES_FILE_NAME);
    final Set<String> hashes = ConcurrentHashMap.newKeySet();
    loadHashes(addressesFile, hashes, logger);
    return new KnownAddressStore(addressesFile, key, hashes, newWriter(), logger);
  }

  static KnownAddressStore inMemory(final Logger logger) {
    final byte[] key = new byte[KEY_BYTES];
    new SecureRandom().nextBytes(key);
    return new KnownAddressStore(null, key, ConcurrentHashMap.newKeySet(), null, logger);
  }

  boolean contains(final InetAddress address) {
    return this.hashes.contains(this.hasher.hash(address));
  }

  void record(final InetAddress address) {
    final String hash = this.hasher.hash(address);
    final Path addressesFile = this.addressesFile;
    final ExecutorService writer = this.writer;
    if (!this.hashes.add(hash) || addressesFile == null || writer == null || this.closed.get()) {
      return;
    }

    try {
      writer.execute(() -> this.append(addressesFile, hash));
    } catch (final RejectedExecutionException exception) {
      this.logPersistenceError(exception);
    }
  }

  int size() {
    return this.hashes.size();
  }

  @Override
  public void close() {
    final ExecutorService writer = this.writer;
    if (writer == null || !this.closed.compareAndSet(false, true)) {
      return;
    }

    writer.shutdown();
    try {
      if (!writer.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        this.logger.warning("Timed out while saving known addresses");
        writer.shutdownNow();
      }
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      writer.shutdownNow();
      this.logger.warning("Interrupted while saving known addresses");
    }
  }

  private void append(final Path addressesFile, final String hash) {
    try {
      Files.writeString(
          addressesFile,
          hash + System.lineSeparator(),
          StandardCharsets.US_ASCII,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (final IOException exception) {
      this.logPersistenceError(exception);
    }
  }

  private void logPersistenceError(final Exception exception) {
    if (this.persistenceErrorLogged.compareAndSet(false, true)) {
      this.logger.log(
          Level.SEVERE,
          "Could not save known addresses; new records may not survive a restart",
          exception);
    }
  }

  private static ExecutorService newWriter() {
    return Executors.newSingleThreadExecutor(runnable -> {
      final Thread thread = new Thread(runnable, "MOTDGate address writer");
      thread.setDaemon(true);
      return thread;
    });
  }

  private static byte[] loadOrCreateKey(final Path path) throws IOException {
    if (Files.exists(path)) {
      return decodeKey(Files.readString(path, StandardCharsets.US_ASCII).trim());
    }

    final byte[] key = new byte[KEY_BYTES];
    new SecureRandom().nextBytes(key);
    createSecretFile(path);
    Files.writeString(
        path,
        Base64.getEncoder().encodeToString(key) + System.lineSeparator(),
        StandardCharsets.US_ASCII,
        StandardOpenOption.TRUNCATE_EXISTING);
    return key;
  }

  private static void createSecretFile(final Path path) throws IOException {
    try {
      Files.createFile(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    } catch (final UnsupportedOperationException exception) {
      Files.createFile(path);
    }
  }

  private static byte[] decodeKey(final String encoded) throws IOException {
    final byte[] key;
    try {
      key = Base64.getDecoder().decode(encoded);
    } catch (final IllegalArgumentException exception) {
      throw new IOException("Invalid Base64 in " + SECRET_FILE_NAME, exception);
    }
    if (key.length != KEY_BYTES) {
      throw new IOException(SECRET_FILE_NAME + " must contain a " + KEY_BYTES + "-byte key");
    }
    return key;
  }

  private static void loadHashes(final Path path, final Set<String> hashes, final Logger logger)
      throws IOException {
    if (Files.notExists(path)) {
      return;
    }

    int invalidLines = 0;
    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
      String line;
      while ((line = reader.readLine()) != null) {
        final String hash = line.trim();
        if (isValidHash(hash)) {
          hashes.add(hash);
        } else if (!hash.isEmpty() && !hash.startsWith("#")) {
          invalidLines++;
        }
      }
    }
    if (invalidLines > 0) {
      logger.warning("Ignored " + invalidLines + " malformed line(s) in " + ADDRESSES_FILE_NAME);
    }
  }

  private static boolean isValidHash(final String hash) {
    if (hash.length() != ENCODED_HASH_LENGTH) {
      return false;
    }
    try {
      return Base64.getUrlDecoder().decode(hash).length == KEY_BYTES;
    } catch (final IllegalArgumentException exception) {
      return false;
    }
  }
}
