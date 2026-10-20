package com.hubspot.jinjava.lib.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.interpret.InvalidArgumentException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Recorded Python reference results; Python and Ansible are not runtime dependencies. */
@RunWith(Parameterized.class)
public class IpFilterParityTest extends BaseInterpretingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final JsonNode example;

  public IpFilterParityTest(String description, JsonNode example) {
    this.example = example;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> examples() throws IOException {
    List<Object[]> examples = new ArrayList<>();
    try (
      InputStream input =
        IpFilterParityTest.class.getResourceAsStream("/ipfilters/ansible-utils.json")
    ) {
      for (JsonNode row : MAPPER.readTree(input)) {
        examples.add(
          new Object[] {
            row.get("filter") + " " + row.get("value") + " " + row.get("args"),
            row,
          }
        );
      }
    }
    return examples;
  }

  @Test
  public void matchesPythonReference() {
    Filter filter;
    switch (example.get("filter").asText()) {
      case "ipsubnet":
        filter = new IpSubnetFilter();
        break;
      case "ipmath":
        filter = new IpMathFilter();
        break;
      default:
        filter = new IpAddrFilter();
    }
    List<String> arguments = new ArrayList<>();
    example.get("args").forEach(arg -> arguments.add(arg.asText()));
    String[] args = arguments.toArray(new String[0]);
    Object value = MAPPER.convertValue(example.get("value"), Object.class);
    if (
      (example.path("error").asBoolean() && !example.has("jinjavaExpected")) ||
      example.path("jinjavaError").asBoolean()
    ) {
      assertThatThrownBy(() -> filter.filter(value, interpreter, args)).isInstanceOf(
        InvalidArgumentException.class
      );
    } else {
      Object result = filter.filter(value, interpreter, args);
      assertThat(MAPPER.valueToTree(result).toString()).isEqualTo(
        example
          .get(example.has("jinjavaExpected") ? "jinjavaExpected" : "expected")
          .toString()
      );
    }
  }
}
