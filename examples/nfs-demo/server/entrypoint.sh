#!/bin/sh
set -eu
rpcbind -w
exec ganesha.nfsd -F -L /dev/stdout -f /etc/ganesha/ganesha.conf
