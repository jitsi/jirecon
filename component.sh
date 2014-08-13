#!/bin/bash

if [[ "$1" == "--help" ]]; then
    echo -e "Usage:"
    echo -e "$0 [OPTIONS...]"
    echo -e "Start Jirecon XMPP external component."
    echo
    echo -e "Examples:"
    echo -e "\t$0 --conf=jirecon.properties --port=5275 --secret=xxxx --domain=jircon.example.com --name=jirecon"
    echo
    echo "Operations can be:"
    echo -e "\t--conf\t sets the configuration file path. Default value is jirecon.properties."
    echo -e "\t--port\t sets the port of external XMPP component. Default value is 5275."
    echo -e "\t--secret\t sets the secret key of external XMPP component. Default value is xxxxx."
    echo -e "\t--domain\t sets the domain of external XMPP component. Default value is jirecon.example.com."
    echo -e "\t--name\t sets the name external XMPP component. Default value is jirecon."
    echo
    exit 1
fi

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

mainClass="org.jitsi.jirecon.xmppcomponent.ComponentLauncher"
cp=$(JARS=($SCRIPT_DIR/jirecon.jar $SCRIPT_DIR/lib/*.jar); IFS=:; echo "${JARS[*]}")
libs="$SCRIPT_DIR/lib/native/linux-64"

java -Djava.library.path=$libs -cp $cp $mainClass $@
