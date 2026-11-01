#!/bin/bash

set -e

DIR=app/src/main/assets/sing-box
rm -rf $DIR
mkdir -p $DIR
cd $DIR

# Pinned release tags and sha256 of the uncompressed .db files.
# Bump these together when updating the bundled geo assets.
VERSION_GEOIP=20261012
SHA256_GEOIP=f24050f4ce11783cd632fe3982f24895d959fa01afc8fab6ca8a6a3615b5de56
VERSION_GEOSITE=20261025014458
SHA256_GEOSITE=abbb33e5948fd396c9e4ace3442ae3cc483f31ee4400e02cfc40dc1dfae4e008

verify_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    echo "$1  $2" | sha256sum -c -
  else
    echo "$1  $2" | shasum -a 256 -c -
  fi
}

####
echo VERSION_GEOIP=$VERSION_GEOIP
echo -n $VERSION_GEOIP > geoip.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geoip/releases/download/$VERSION_GEOIP/geoip.db
verify_sha256 $SHA256_GEOIP geoip.db
xz -9 geoip.db

####
echo VERSION_GEOSITE=$VERSION_GEOSITE
echo -n $VERSION_GEOSITE > geosite.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geosite/releases/download/$VERSION_GEOSITE/geosite.db
verify_sha256 $SHA256_GEOSITE geosite.db
xz -9 geosite.db
