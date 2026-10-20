"""Regenerate the recorded oracle; see README.md for pinned reference versions."""
import json
from pathlib import Path
from ansible_collections.ansible.utils.plugins.plugin_utils.base.ipaddr_utils import ipaddr
from ansible_collections.ansible.utils.plugins.filter.ipsubnet import ipsubnet
from ansible_collections.ansible.utils.plugins.filter.ipmath import ipmath
values=['192.168.1.5','192.168.1.0/24','192.168.1.5/24','192.168.1.255/24','192.168.1.0/30','192.168.1.1/30','192.168.1.2/30','192.168.1.3/30','192.168.1.0/31','192.168.1.1/31','192.168.1.1/32','0.0.0.0/0','255.255.255.255/32','8.8.8.8','2001:db8::1','2001:db8::/64','2001:db8::123/64','2001:db8::/126','2001:db8::1/127','2001:db8::/127','2001:db8::1/128','::/0','::1','::ffff:192.168.1.1','::ffff:192.168.1.1/120','fe80::1/64','ff02::1','224.0.0.1','192.168.1.5/255.255.255.0']
queries=['0','1','-1','-2','256','-257','18446744073709551615','bool','host','address/prefix','host/prefix','hostnet','gw','router','ip','ip/prefix','ip_netmask','cidr','subnet','network/prefix','net','network_id','wildcard','hostmask','network_netmask','network_wildcard','version','size','size_usable','type','int','first_usable','last_usable','next_usable','previous_usable','range_usable','peer','ipv4','ipv6','v4','v6','6to4','wrap','revdns','multicast','unicast','link-local','lo','loopback','192.168.0.0/16','2001:db8::/32']
rows=[]
def add(name,v,args,fn):
 row=dict(filter=name,value=v,args=args)
 try: row['expected']=fn(v,*args)
 except Exception: row['error']=True
 if name == 'ipaddr' and args[0] in ('v4','v6'):
  row['jinjavaExpected']=ipaddr(v, 'ipv4' if args[0] == 'v4' else 'ipv6')
  row['difference']='Use the documented alias target rather than the upstream alias TypeError'
 if name == 'ipaddr' and v == '::/0' and args[0] in ('first_usable','next_usable','range_usable'):
  row['jinjavaExpected']=row['expected'].replace('0.0.0.1','::1')
  row['difference']='Retain the IPv6 family when formatting a low numeric IPv6 address'
 if name == 'ipsubnet' and v == '::/0' and args == ['24',0]:
  row['jinjavaExpected']='::/24'
  row['difference']='Retain the IPv6 family when formatting a low numeric IPv6 address'
 if name == 'ipsubnet' and ':' in v and args == ['192.168.0.0/16'] and not row.get('error'):
  row['jinjavaError']=True
  row['difference']='Reject subnet ranks across different address families'
 rows.append(row)
for v in values:
 for q in queries: add('ipaddr',v,[q],ipaddr)
for v in values:
 for args in [[],['20'],['24'],['28'],['32'],['64'],['128'],['24',0],['24',1],['24',-1],['24',256],['64',-1],['128',-1],['192.168.0.0/16'],['2001:db8::/32']]: add('ipsubnet',v,args,ipsubnet)
 for n in [0,1,-1,5,-10,256,2**32,2**64,-(2**128)]: add('ipmath',v,[n],ipmath)
Path(__file__).with_name('ansible-utils.json').write_text('[\n'+',\n'.join('  '+json.dumps(x,separators=(',', ':')) for x in rows)+'\n]\n')
print(len(rows),'reference cases')
