# IP address filters

Jinjava's `ipaddr`, `ipv4`, and `ipv6` support additional Ansible-style queries.
`ipsubnet` and `ipmath` provide subnet selection and address arithmetic. These
filters run entirely in Java and do not require Python, Ansible, external
services, or DNS resolution.

## CLI template examples

```jinja
{% set subnet = '192.168.10.0/24' %}
{{ subnet | ipaddr(0) }}                         {# 192.168.10.0/24 #}
{{ subnet | ipaddr(1) }}                         {# 192.168.10.1/24 #}
{{ subnet | ipaddr(-1) }}                        {# 192.168.10.255/24 #}
{{ subnet | ipaddr(1) | ipaddr('address') }}      {# 192.168.10.1 #}
{{ subnet | ipaddr('wildcard') }}                {# 0.0.0.255 #}
{{ subnet | ipaddr('network_wildcard') }}        {# 192.168.10.0 0.0.0.255 #}
{{ '10.0.0.0/16' | ipsubnet(24, 2) }}            {# 10.0.2.0/24 #}
{{ '10.0.0.0/16' | ipsubnet(index=-1, query=24) }} {# 10.0.255.0/24 #}
{{ '10.0.2.1/24' | ipmath(4) }}                  {# 10.0.2.5 #}
```

Numeric `ipaddr` indexes select from the network address, retain the prefix,
and include network and broadcast addresses. Negative indexes count backwards
from the end. Out-of-range indexes return `false`. An individual host address
is returned unchanged by an index query, following Ansible's behavior.

## Additional `ipaddr` queries

| Query | Result |
| --- | --- |
| `bool` | Whether the input parses as an address or network |
| `ip` | Host address without prefix, excluding an IPv4 network address except on /31 and /32 |
| `host` | Host address with prefix, excluding the network address except on two-address and single-address networks |
| `cidr` | Normalized address and prefix, preserving host bits |
| `subnet`, `network/prefix` | Canonical network and prefix |
| `net` | Network and prefix only when the input is the network address |
| `network_id` | Canonical network address without prefix |
| `hostmask`, `wildcard` | Inverse netmask |
| `network_netmask`, `network_wildcard` | Network address and mask separated by a space |
| `ip/prefix`, `ip_netmask` | Host and prefix or netmask on a network with multiple addresses |
| `address/prefix`, `host/prefix`, `hostnet`, `gw`, `router` | Host and prefix, excluding network and broadcast endpoints on networks larger than two addresses |
| `size`, `size_usable` | Total or usable address count; arbitrary precision for IPv6 |
| `first_usable`, `last_usable` | First or last usable address |
| `next_usable`, `previous_usable` | Adjacent usable address within the input subnet |
| `range_usable` | First and last usable addresses separated by a hyphen |
| `peer` | The other usable address on a four-address or two-address network |
| `int` | Integer address; retains `/prefix` when explicitly supplied |
| `version`, `type` | `4`/`6`, or `address`/`network` |
| `ipv4`, `v4`, `ipv6`, `v6` | Address family conversion; IPv4 is mapped into IPv6 |
| `6to4` | 6to4 `/48` for a suitable IPv4 address |
| `wrap` | Bracketed IPv6 address, retaining any explicit prefix |
| `revdns` | Reverse DNS name |
| `multicast`, `unicast`, `link-local`, `loopback`, `lo` | Original value when it matches the classification |
| CIDR string, e.g. `192.168.0.0/16` | Original value if the entire address or subnet is contained in that CIDR |

Use `query=` for named queries. The existing `function=` keyword remains
supported. List inputs retain Jinjava's existing filtering behavior: rejected
items are removed and accepted results are strings. Maps retain the existing
in-place filtering behavior. New queries accept standard IPv4 and IPv6 literals,
including mapped IPv6, CIDR prefixes, and contiguous netmasks or wildcard masks.
Invalid values do not trigger DNS lookup.

## `ipsubnet(query, index)`

Without a query, return the canonical subnet (or `/32` or `/128` for a host).
For a network, a numeric query gives the number of subnets at that prefix as a
string. Supply an index to select a subnet; negative indexes count from the end.
For an individual address, a numeric query selects its largest containing
supernet at that prefix; an index selects successively more specific supernets.
A subnet query returns the input's one-based rank in the containing subnet.

All arithmetic uses arbitrary precision. Selecting the last `/128` inside `::/0`
does not enumerate the preceding subnets. Invalid input or an out-of-range index
returns `false`; invalid queries and incompatible subnet ranks report a template
argument error.

## `ipmath(amount)`

Add a signed integer to the host address, including when the input has a prefix.
The result omits the prefix and can cross subnet boundaries. Address family is
preserved. Underflow, overflow, malformed addresses, and noninteger amounts report
a template argument error. `amount=` is supported.

## Compatibility

These are Ansible collection filters, not part of core Jinja2. New queries are
checked against `ansible.utils` at commit
`1f3e1bdfe2ba8904d397222dd6f90911d6f2402b` with `netaddr` 1.3.0.
The implementation uses Java integer arithmetic and existing dependencies;
Ansible's GPL implementation is not bundled or translated into this library.

Existing Jinjava behavior is retained deliberately:

- No-query validation returns a boolean rather than the address string.
- `prefix`, `netmask`, `network`, `address`, `broadcast`, `gateway`, `public`, and
  `private` retain their previous return values, supported inputs, and error
  behavior. In particular, `gateway` returns the first host address, and
  `public`/`private` keep Jinjava's previous classification rules.
- Collection results retain their existing string types and map behavior.
- Consequently this is an additive compatibility extension, rather than a
  breaking replacement of existing filters with all Ansible semantics.

A few reference defects are deliberately avoided: `v4`/`v6` work as documented
aliases; low numeric IPv6 results retain their IPv6 family; subnet ranking
rejects comparisons between IPv4 and IPv6. These differences are marked
explicitly in the reference fixtures, alongside the original Python result.
The unchanged legacy tests, reference cases, and template integration tests run
as part of the ordinary Maven test suite.

This change adds library capabilities only. Catalyst Center must adopt a version
containing these changes before its templates can use them.
