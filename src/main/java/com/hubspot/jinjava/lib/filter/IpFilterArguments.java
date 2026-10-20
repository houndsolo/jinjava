package com.hubspot.jinjava.lib.filter;

import com.hubspot.jinjava.interpret.InvalidArgumentException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import java.util.Arrays;
import java.util.Map;

/** Bind keyword arguments by name instead of relying on map iteration order. */
final class IpFilterArguments {

  private IpFilterArguments() {}

  static String[] bind(
    Filter filter,
    JinjavaInterpreter interpreter,
    Object[] positional,
    Map<String, Object> keywords,
    String... names
  ) {
    if (positional.length > names.length) {
      throw new InvalidArgumentException(
        interpreter,
        filter.getName(),
        "Too many arguments"
      );
    }
    String[] result = new String[names.length];
    boolean[] supplied = new boolean[names.length];
    int length = positional.length;
    for (int i = 0; i < positional.length; i++) {
      result[i] = positional[i] == null ? null : positional[i].toString();
      supplied[i] = true;
    }
    for (Map.Entry<String, Object> entry : keywords.entrySet()) {
      int index = Arrays.asList(names).indexOf(entry.getKey());
      if (index < 0 || supplied[index]) {
        throw new InvalidArgumentException(
          interpreter,
          filter.getName(),
          "Unknown or duplicate argument: " + entry.getKey()
        );
      }
      supplied[index] = true;
      result[index] = entry.getValue() == null ? null : entry.getValue().toString();
      length = Math.max(length, index + 1);
    }
    return Arrays.copyOf(result, length);
  }
}
