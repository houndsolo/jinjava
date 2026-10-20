# Python reference fixtures

`ansible-utils.json` records 2,175 cases covering the new IP queries, subnet
operations, and address arithmetic. The Java parameterized test checks values,
return types, and whether an argument error occurs. Existing query semantics
are covered by the unchanged `IpAddrFilterTest` rather than this oracle.

Reference versions:

- ansible.utils: commit `1f3e1bdfe2ba8904d397222dd6f90911d6f2402b`
- netaddr: 1.3.0
- ansible-core: 2.19.3

Reference source:

- https://github.com/ansible-collections/ansible.utils/blob/1f3e1bdfe2ba8904d397222dd6f90911d6f2402b/plugins/plugin_utils/base/ipaddr_utils.py
- https://github.com/ansible-collections/ansible.utils/blob/1f3e1bdfe2ba8904d397222dd6f90911d6f2402b/plugins/filter/ipsubnet.py
- https://github.com/ansible-collections/ansible.utils/blob/1f3e1bdfe2ba8904d397222dd6f90911d6f2402b/plugins/filter/ipmath.py

To regenerate, install the pinned Python dependencies in a separate virtual
environment, check out that collection commit under
`ansible_collections/ansible/utils`, add the parent of `ansible_collections` to
`PYTHONPATH`, and run `generate_reference.py` from this directory. No Python
packages or collection source are needed to run the Java tests.

`expected` and `error` preserve the Python outcome. Deliberate differences have
`jinjavaExpected` or `jinjavaError`, and a `difference` explaining why. Those
cover working conversion aliases, retaining the IPv6 family for low-valued
addresses, and rejecting cross-family subnet ranks. They are not silently
normalized away.
