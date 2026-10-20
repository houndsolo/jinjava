package com.hubspot.jinjava.lib.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.interpret.InvalidArgumentException;
import java.util.Arrays;
import org.junit.Test;

public class IpFilterIntegrationTest extends BaseInterpretingTest {

  @Test
  public void rendersCatalystAddressSelectionAndWildcard() {
    assertThat(
      interpreter.render(
        "{{ '192.168.10.0/24' | ipaddr(0) }} {{ '192.168.10.0/24' | ipaddr(1) }} {{ '192.168.10.0/24' | ipaddr('wildcard') }}"
      )
    ).isEqualTo("192.168.10.0/24 192.168.10.1/24 0.0.0.255");
    assertThat(
      interpreter.render("{{ '192.168.10.0/24' | ipaddr(1) | ipaddr('address') }}")
    ).isEqualTo("192.168.10.1");
  }

  @Test
  public void bindsKeywordArgumentsByNameAndSupportsChaining() {
    assertThat(
      interpreter.render("{{ '10.0.0.0/16' | ipsubnet(index=-1, query=24) }}")
    ).isEqualTo("10.0.255.0/24");
    assertThat(
      interpreter.render(
        "{{ '10.0.0.0/16' | ipsubnet(24, 2) | ipaddr(1) | ipaddr('address') | ipmath(amount=4) }}"
      )
    ).isEqualTo("10.0.2.5");
    assertThat(
      interpreter.render("{{ '10.0.0.0/24' | ipaddr(query='wildcard') }}")
    ).isEqualTo("0.0.0.255");
    assertThat(
      interpreter.render("{{ '10.0.0.1/24' | ipaddr(function='gateway') }}")
    ).isEqualTo("10.0.0.1");
  }

  @Test
  public void filtersCollectionsAndRetainsLegacyValidation() {
    IpAddrFilter filter = new IpAddrFilter();
    assertThat(filter.filter("192.168.1.1", interpreter)).isEqualTo(true);
    assertThat(filter.filter(13, interpreter)).isEqualTo(false);
    assertThat(filter.filter(13, interpreter, "int")).isEqualTo(
      java.math.BigInteger.valueOf(13)
    );
    assertThat(filter.filter(Arrays.asList(13), interpreter, "bool")).isEqualTo(
      Arrays.asList("13")
    );
    assertThat(
      filter.filter(
        Arrays.asList("192.168.1.0/24", "invalid", "2001:db8::/64"),
        interpreter,
        "1"
      )
    ).isEqualTo(Arrays.asList("192.168.1.1/24", "2001:db8::1/64"));
  }

  @Test
  public void handlesIpv6SizesAndIndexesWithoutEnumeratingSubnets() {
    assertThat(interpreter.render("{{ '::/0' | ipsubnet(128) }}")).isEqualTo(
      "340282366920938463463374607431768211456"
    );
    assertThat(interpreter.render("{{ '::/0' | ipsubnet(128, -1) }}")).isEqualTo(
      "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff/128"
    );
    assertThat(interpreter.render("{{ '2001:db8::/64' | ipaddr('-1') }}")).isEqualTo(
      "2001:db8::ffff:ffff:ffff:ffff/64"
    );
  }

  @Test
  public void rejectsInvalidInputAndArithmeticOverflow() {
    IpMathFilter math = new IpMathFilter();
    for (String address : Arrays.asList(
      "localhost",
      "192.0.2.256",
      "1::2::3",
      "192.0.2.1/33",
      "2001:db8::/129"
    )) {
      assertThatThrownBy(() -> math.filter(address, interpreter, "1")).isInstanceOf(
        InvalidArgumentException.class
      );
    }
    assertThatThrownBy(() -> math.filter("255.255.255.255", interpreter, "1")
    ).isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> math.filter("::", interpreter, "-1")).isInstanceOf(
      InvalidArgumentException.class
    );
    assertThatThrownBy(() -> math.filter("192.0.2.1", interpreter, "1.5")).isInstanceOf(
      InvalidArgumentException.class
    );
    assertThat(new IpSubnetFilter().filter("localhost", interpreter)).isEqualTo(false);
  }
}
