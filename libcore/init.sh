#!/bin/bash

chmod -R 777 .build 2>/dev/null
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# Install gomobile (pinned; daniellansun/gomobile = MatsuriDayo master2 17d6af34
# plus relative local-replace paths in the generated go.mod)
GOMOBILE_COMMIT=00e7dc75c03bad26666bee9235d27bdadaa75f30
if [ ! -f "$GOPATH/bin/gomobile-matsuri" ]; then
    git clone https://github.com/daniellansun/gomobile.git
    pushd gomobile
    git checkout --detach "$GOMOBILE_COMMIT" || exit 1
    [ "$(git rev-parse HEAD)" = "$GOMOBILE_COMMIT" ] || exit 1
    pushd cmd
    pushd gomobile
    go install -v
    popd
    pushd gobind
    go install -v
    popd
    popd
    rm -rf gomobile
    mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
    mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
fi

GOBIND=gobind-matsuri gomobile-matsuri init
