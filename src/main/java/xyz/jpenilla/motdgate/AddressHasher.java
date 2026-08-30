package xyz.jpenilla.motdgate;

import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class AddressHasher {
  private static final String ALGORITHM = "HmacSHA256";

  private final ThreadLocal<Mac> mac;

  AddressHasher(final byte[] key) {
    final SecretKeySpec secretKey = new SecretKeySpec(key.clone(), ALGORITHM);
    this.mac = ThreadLocal.withInitial(() -> createMac(secretKey));
  }

  String hash(final InetAddress address) {
    final byte[] addressBytes = address.getAddress();
    final Mac currentMac = this.mac.get();
    currentMac.reset();
    currentMac.update((byte) addressBytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(currentMac.doFinal(addressBytes));
  }

  private static Mac createMac(final SecretKeySpec secretKey) {
    try {
      final Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(secretKey);
      return mac;
    } catch (final GeneralSecurityException exception) {
      throw new IllegalStateException("JVM does not support " + ALGORITHM, exception);
    }
  }
}
