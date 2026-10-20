package com.hubspot.jinjava.lib.filter;

import com.hubspot.jinjava.doc.annotations.JinjavaDoc;
import com.hubspot.jinjava.doc.annotations.JinjavaParam;
import com.hubspot.jinjava.interpret.InvalidArgumentException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import java.math.BigInteger;
import java.util.Map;

/** Subnet conversion, splitting, containing supernets, and one-based subnet ranks. */
@JinjavaDoc(
  value = "Select, split, and rank IPv4 or IPv6 subnets",
  input = @JinjavaParam(
    value = "value",
    type = "string",
    desc = "IP address or network",
    required = true
  ),
  params = {
    @JinjavaParam(
      value = "query",
      type = "string",
      desc = "Prefix length or containing subnet"
    ),
    @JinjavaParam(
      value = "index",
      type = "integer",
      desc = "Optional subnet index; negative indexes count from the end"
    ),
  }
)
public class IpSubnetFilter implements Filter {

  @Override
  public Object filter(
    Object value,
    JinjavaInterpreter interpreter,
    Object[] args,
    Map<String, Object> kwargs
  ) {
    String[] bound = IpFilterArguments.bind(
      this,
      interpreter,
      args,
      kwargs,
      "query",
      "index"
    );
    return filter(
      value instanceof com.hubspot.jinjava.objects.SafeString ? value.toString() : value,
      interpreter,
      bound
    );
  }

  @Override
  public Object filter(Object value, JinjavaInterpreter interpreter, String... args) {
    IpAddress ip;
    try {
      ip = IpAddress.parse(value);
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (args.length > 2) {
      throw new InvalidArgumentException(
        interpreter,
        getName(),
        "Expected a query and optional index"
      );
    }
    if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
      return ip.subnet();
    }
    String query = args[0];
    try {
      if (query.matches("[0-9]+")) {
        int prefix = Integer.parseInt(query);
        if (prefix > ip.bits) {
          return false;
        }
        if (ip.prefix == ip.bits) {
          // An address can select each containing network from query through bits-1.
          int count = ip.bits - prefix;
          if (count == 0) {
            if (args.length == 2) {
              return false;
            }
            throw new IllegalArgumentException("No containing supernet at this prefix");
          }
          BigInteger index = args.length == 2 ? new BigInteger(args[1]) : BigInteger.ZERO;
          if (index.signum() < 0) {
            index = index.add(BigInteger.valueOf(count));
          }
          if (index.signum() < 0 || index.compareTo(BigInteger.valueOf(count)) >= 0) {
            return false;
          }
          return new IpAddress(
            ip.value,
            ip.bits,
            prefix + index.intValue(),
            true
          ).subnet();
        }
        if (prefix < ip.prefix) {
          if (args.length == 2) {
            return new BigInteger(args[1]).signum() == 0
              ? new IpAddress(ip.network(), ip.bits, prefix, true).cidr()
              : false;
          }
          throw new IllegalArgumentException(
            "Requested subnet size is smaller than the input prefix"
          );
        }
        BigInteger count = BigInteger.ONE.shiftLeft(prefix - ip.prefix);
        if (args.length < 2) {
          return count.toString();
        }
        BigInteger index = new BigInteger(args[1]);
        if (index.signum() < 0) {
          index = index.add(count);
        }
        if (index.signum() < 0 || index.compareTo(count) >= 0) {
          return false;
        }
        return new IpAddress(
          ip.network().add(index.shiftLeft(ip.bits - prefix)),
          ip.bits,
          prefix,
          true
        ).subnet();
      }
      IpAddress container = IpAddress.parse(query);
      if (!container.contains(ip)) {
        throw new IllegalArgumentException("Input is not in the requested subnet");
      }
      return ip
        .network()
        .subtract(container.network())
        .shiftRight(ip.bits - ip.prefix)
        .add(BigInteger.ONE)
        .toString();
    } catch (IllegalArgumentException e) {
      throw new InvalidArgumentException(interpreter, getName(), e.getMessage());
    }
  }

  @Override
  public boolean preserveSafeString() {
    return false;
  }

  @Override
  public String getName() {
    return "ipsubnet";
  }
}
