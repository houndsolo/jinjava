package com.hubspot.jinjava.lib.filter;

import java.math.BigInteger;

/** Additional Ansible-style queries; legacy Jinjava queries stay in IpAddrFilter. */
final class IpAddressQueries {

  Object query(IpAddress ip, Object original, String query) {
    BigInteger size = ip.size();
    boolean multiple = size.compareTo(BigInteger.ONE) > 0;
    boolean endpoints = size.compareTo(BigInteger.TWO) > 0;
    boolean network = ip.value.equals(ip.network());
    String address = ip.address();
    String cidr = ip.cidr();
    switch (query) {
      case "bool":
        return true;
      case "ip":
        return !multiple || !network || !endpoints || ip.bits == 128 ? address : null;
      case "host":
        return !multiple || !network || !endpoints ? cidr : null;
      case "address/prefix":
      case "host/prefix":
      case "hostnet":
      case "gw":
      case "router":
        return endpoints && (network || ip.value.equals(ip.last())) ? false : cidr;
      case "ip/prefix":
        return multiple && (!network || !endpoints) ? cidr : null;
      case "ip_netmask":
        return multiple && (!network || !endpoints)
          ? address + " " + ip.format(ip.netmask())
          : null;
      case "cidr":
        return cidr;
      case "subnet":
      case "network/prefix":
        return ip.subnet();
      case "net":
        return multiple && network ? ip.subnet() : null;
      case "network_id":
        return ip.format(ip.network());
      case "wildcard":
      case "hostmask":
        return ip.format(ip.hostmask());
      case "network_netmask":
        return ip.format(ip.network()) + " " + ip.format(ip.netmask());
      case "network_wildcard":
        return ip.format(ip.network()) + " " + ip.format(ip.hostmask());
      case "version":
        return ip.bits == 32 ? 4 : 6;
      case "size":
        return size;
      case "size_usable":
        return multiple
          ? (endpoints ? size.subtract(BigInteger.TWO) : size)
          : BigInteger.ZERO;
      case "type":
        return multiple && network ? "network" : "address";
      case "int":
        return ip.explicitPrefix ? ip.value + "/" + ip.prefix : ip.value;
      case "first_usable":
      case "last_usable":
      case "next_usable":
      case "previous_usable":
      case "range_usable":
      case "peer":
        return usable(ip, query);
      case "ipv4":
      case "v4":
        return convert(ip, original, 32);
      case "ipv6":
      case "v6":
        return convert(ip, original, 128);
      case "6to4":
        if (ip.bits == 128) {
          return in(ip, "2002::/16") && (!ip.explicitPrefix || !network)
            ? original
            : false;
        }
        if ((multiple && network) || (isGlobal(ip) && (isMulticast(ip) || isMask(ip)))) {
          return false;
        }
        return String.format(
          "2002:%04x:%04x::1/48",
          ip.value.shiftRight(16).intValue(),
          ip.value.intValue() & 65535
        );
      case "wrap":
        return ip.bits == 128
          ? "[" + address + "]" + (ip.explicitPrefix ? "/" + ip.prefix : "")
          : original;
      case "revdns":
        return reverseDns(ip);
      case "multicast":
        return isMulticastNetwork(ip) ? original : null;
      case "unicast":
        return !isMulticastNetwork(ip) ? original : null;
      case "link-local":
        return in(ip, ip.bits == 32 ? "169.254.0.0/16" : "fe80::/10") ? original : null;
      case "lo":
      case "loopback":
        return in(ip, ip.bits == 32 ? "127.0.0.0/8" : "::1/128") ? original : null;
      default:
        if (query.matches("[+-]?[0-9]+")) {
          if (!multiple) {
            return ip.explicitPrefix ? cidr : address;
          }
          BigInteger index = new BigInteger(query);
          if (index.signum() < 0) {
            index = index.add(size);
          }
          return index.signum() < 0 || index.compareTo(size) >= 0
            ? false
            : ip.format(ip.network().add(index)) + "/" + ip.prefix;
        }
        try {
          return IpAddress.parse(query).contains(ip) ? original : null;
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Unknown IP query: " + query);
        }
    }
  }

  private Object usable(IpAddress ip, String query) {
    if (!ip.explicitPrefix) {
      throw new IllegalArgumentException("Not a network address");
    }
    BigInteger size = ip.size();
    if (query.equals("peer")) {
      if (size.equals(BigInteger.TWO)) {
        return ip.format(ip.value.xor(BigInteger.ONE));
      }
      if (
        size.equals(BigInteger.valueOf(4)) &&
        !ip.value.equals(ip.network()) &&
        !ip.value.equals(ip.last())
      ) {
        return ip.format(ip.value.xor(BigInteger.valueOf(3)));
      }
      throw new IllegalArgumentException("Not a usable point-to-point address");
    }
    if (size.equals(BigInteger.ONE)) {
      return null;
    }
    switch (query) {
      case "first_usable":
        return ip.format(ip.firstUsable());
      case "last_usable":
        return ip.format(ip.lastUsable());
      case "range_usable":
        return ip.format(ip.firstUsable()) + "-" + ip.format(ip.lastUsable());
      default:
        BigInteger next = ip.value.add(
          query.equals("next_usable") ? BigInteger.ONE : BigInteger.ONE.negate()
        );
        ip.format(next); // Reject arithmetic overflow before checking usable boundaries.
        return (
            next.compareTo(ip.firstUsable()) >= 0 && next.compareTo(ip.lastUsable()) <= 0
          )
          ? ip.format(next)
          : null;
    }
  }

  private Object convert(IpAddress ip, Object original, int bits) {
    if (bits == ip.bits) {
      return original;
    }
    if (bits == 128) {
      return new IpAddress(
        BigInteger.valueOf(65535).shiftLeft(32).or(ip.value),
        128,
        ip.prefix + 96,
        true
      ).cidr();
    }
    BigInteger high = ip.value.shiftRight(32);
    if (
      (!high.equals(BigInteger.ZERO) && !high.equals(BigInteger.valueOf(65535))) ||
      ip.prefix < 96
    ) {
      return false;
    }
    return new IpAddress(
      ip.value.and(BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE)),
      32,
      ip.prefix - 96,
      true
    ).cidr();
  }

  private String reverseDns(IpAddress ip) {
    if (ip.bits == 32) {
      String[] octets = ip.address().split("\\.");
      return (
        octets[3] + "." + octets[2] + "." + octets[1] + "." + octets[0] + ".in-addr.arpa."
      );
    }
    String hex = String.format("%032x", ip.value);
    StringBuilder result = new StringBuilder();
    for (int i = hex.length() - 1; i >= 0; i--) {
      result.append(hex.charAt(i)).append('.');
    }
    return result.append("ip6.arpa.").toString();
  }

  private boolean in(IpAddress ip, String cidr) {
    IpAddress range = IpAddress.parse(cidr);
    return range.bits == ip.bits && ip.value.and(range.netmask()).equals(range.network());
  }

  private boolean isMulticast(IpAddress ip) {
    return in(ip, ip.bits == 32 ? "224.0.0.0/4" : "ff00::/8");
  }

  private boolean isMulticastNetwork(IpAddress ip) {
    return IpAddress.parse(ip.bits == 32 ? "224.0.0.0/4" : "ff00::/8").contains(ip);
  }

  private boolean isMask(IpAddress ip) {
    BigInteger complement = ip.value.xor(
      BigInteger.ONE.shiftLeft(ip.bits).subtract(BigInteger.ONE)
    );
    return (
      ip.value.and(ip.value.add(BigInteger.ONE)).signum() == 0 ||
      complement.and(complement.add(BigInteger.ONE)).signum() == 0
    );
  }

  private boolean isGlobal(IpAddress ip) {
    // IANA special-purpose address space. Classification follows netaddr 1.3.
    String[] excluded = ip.bits == 32
      ? new String[] {
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.0.0.0/24",
        "192.0.2.0/24",
        "192.168.0.0/16",
        "198.18.0.0/15",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "240.0.0.0/4",
      }
      : new String[] {
        "::/128",
        "::1/128",
        "::ffff:0:0/96",
        "64:ff9b:1::/48",
        "100::/64",
        "2001::/23",
        "2001:db8::/32",
        "2002::/16",
        "fc00::/7",
        "fe80::/10",
      };
    if (ip.bits == 32 && (in(ip, "192.0.0.9/32") || in(ip, "192.0.0.10/32"))) {
      return true;
    }
    if (
      ip.bits == 128 &&
      (in(ip, "2001:1::1/128") ||
        in(ip, "2001:1::2/128") ||
        in(ip, "2001:3::/32") ||
        in(ip, "2001:4:112::/48") ||
        in(ip, "2001:20::/28"))
    ) {
      return true;
    }
    for (String range : excluded) {
      if (in(ip, range)) {
        return false;
      }
    }
    return true;
  }
}
