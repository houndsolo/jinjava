package com.hubspot.jinjava.lib.filter;

import com.hubspot.jinjava.doc.annotations.JinjavaDoc;
import com.hubspot.jinjava.doc.annotations.JinjavaParam;
import com.hubspot.jinjava.interpret.InvalidArgumentException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import java.math.BigInteger;
import java.util.Map;

/** Add a signed integer to an IPv4 or IPv6 host address, retaining its family. */
@JinjavaDoc(
  value = "Add an integer to an IPv4 or IPv6 address",
  input = @JinjavaParam(
    value = "value",
    type = "string",
    desc = "IP address or network",
    required = true
  ),
  params = {
    @JinjavaParam(value = "amount", type = "integer", desc = "Signed address offset"),
  }
)
public class IpMathFilter implements Filter {

  @Override
  public Object filter(
    Object value,
    JinjavaInterpreter interpreter,
    Object[] args,
    Map<String, Object> kwargs
  ) {
    String[] bound = IpFilterArguments.bind(this, interpreter, args, kwargs, "amount");
    return filter(
      value instanceof com.hubspot.jinjava.objects.SafeString ? value.toString() : value,
      interpreter,
      bound
    );
  }

  @Override
  public Object filter(Object value, JinjavaInterpreter interpreter, String... args) {
    if (args.length != 1 || args[0] == null || !args[0].matches("[+-]?[0-9]+")) {
      throw new InvalidArgumentException(
        interpreter,
        getName(),
        "Expected one integer amount"
      );
    }
    try {
      IpAddress ip = IpAddress.parse(value);
      return ip.format(ip.value.add(new BigInteger(args[0])));
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
    return "ipmath";
  }
}
