package com.hubspot.jinjava.lib.filter;

import com.google.common.net.InetAddresses;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Numeric IP arithmetic, deliberately without DNS lookups or subnet enumeration. */
final class IpAddress {

  static final BigInteger ONE = BigInteger.ONE;
  final BigInteger value;
  final int bits;
  final int prefix;
  final boolean explicitPrefix;

  IpAddress(BigInteger value, int bits, int prefix, boolean explicitPrefix) {
    if (value.signum() < 0 || value.bitLength() > bits || prefix < 0 || prefix > bits) {
      throw new IllegalArgumentException("IP address or prefix out of range");
    }
    this.value = value;
    this.bits = bits;
    this.prefix = prefix;
    this.explicitPrefix = explicitPrefix;
  }

  static IpAddress parse(Object input) {
    if (input == null || input instanceof Boolean) {
      throw new IllegalArgumentException("Expected an IP address");
    }
    String[] parts = input.toString().trim().split("/", -1);
    if (parts.length > 2 || parts[0].isEmpty()) {
      throw new IllegalArgumentException("Invalid IP address");
    }
    String address = parts[0];
    int bits;
    BigInteger number;
    if (address.matches("[0-9]+")) {
      number = new BigInteger(address);
      bits = number.bitLength() <= 32 ? 32 : 128;
      if (
        parts.length == 2 && parts[1].matches("[0-9]+") && Integer.parseInt(parts[1]) > 32
      ) {
        bits = 128;
      }
    } else {
      InetAddress parsed = InetAddresses.forString(address);
      bits = address.contains(":") ? 128 : 32;
      number = new BigInteger(1, parsed.getAddress());
      if (bits == 128 && parsed.getAddress().length == 4) {
        number = number.or(BigInteger.valueOf(65535).shiftLeft(32));
      }
    }
    int prefix = bits;
    if (parts.length == 2) {
      if (parts[1].matches("[0-9]+")) {
        prefix = Integer.parseInt(parts[1]);
      } else {
        IpAddress mask = parse(parts[1]);
        if (mask.bits != bits || mask.explicitPrefix) {
          throw new IllegalArgumentException("Invalid netmask");
        }
        BigInteger hostmask = mask.value.xor(ONE.shiftLeft(bits).subtract(ONE));
        if (!hostmask.and(hostmask.add(ONE)).equals(BigInteger.ZERO)) {
          // Dotted wildcard masks are accepted as well as netmasks.
          hostmask = mask.value;
        }
        if (!hostmask.and(hostmask.add(ONE)).equals(BigInteger.ZERO)) {
          throw new IllegalArgumentException("Non-contiguous netmask");
        }
        prefix = bits - hostmask.bitLength();
      }
    }
    return new IpAddress(number, bits, prefix, parts.length == 2);
  }

  BigInteger size() {
    return ONE.shiftLeft(bits - prefix);
  }

  BigInteger hostmask() {
    return size().subtract(ONE);
  }

  BigInteger netmask() {
    return ONE.shiftLeft(bits).subtract(ONE).xor(hostmask());
  }

  BigInteger network() {
    return value.and(netmask());
  }

  BigInteger last() {
    return network().add(hostmask());
  }

  boolean contains(IpAddress other) {
    return (
      bits == other.bits &&
      prefix <= other.prefix &&
      network().equals(other.value.and(netmask()))
    );
  }

  String address() {
    return format(value);
  }

  String cidr() {
    return address() + "/" + prefix;
  }

  String subnet() {
    return format(network()) + "/" + prefix;
  }

  BigInteger firstUsable() {
    return size().equals(BigInteger.TWO) ? network() : network().add(ONE);
  }

  BigInteger lastUsable() {
    return size().equals(BigInteger.TWO) ? last() : last().subtract(ONE);
  }

  String format(BigInteger number) {
    if (number.signum() < 0 || number.bitLength() > bits) {
      throw new IllegalArgumentException("IP arithmetic overflow");
    }
    if (bits == 32) {
      return Arrays.stream(new int[] { 24, 16, 8, 0 })
        .mapToObj(shift ->
          number.shiftRight(shift).and(BigInteger.valueOf(255)).toString()
        )
        .collect(Collectors.joining("."));
    }
    if (number.shiftRight(32).equals(BigInteger.valueOf(65535))) {
      return (
        "::ffff:" +
        new IpAddress(
          number.and(ONE.shiftLeft(32).subtract(ONE)),
          32,
          32,
          false
        ).address()
      );
    }
    int[] groups = new int[8];
    int bestStart = -1;
    int bestLength = 1;
    for (int i = 0; i < 8; i++) {
      groups[i] = number.shiftRight((7 - i) * 16).intValue() & 65535;
    }
    for (int i = 0; i < 8; i++) {
      if (groups[i] == 0) {
        int end = i;
        while (end < 8 && groups[end] == 0) {
          end++;
        }
        if (end - i > bestLength) {
          bestStart = i;
          bestLength = end - i;
        }
        i = end - 1;
      }
    }
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      if (i == bestStart) {
        result.append("::");
        i += bestLength - 1;
      } else {
        if (result.length() > 0 && result.charAt(result.length() - 1) != ':') {
          result.append(':');
        }
        result.append(Integer.toHexString(groups[i]));
      }
    }
    return result.toString();
  }
}
