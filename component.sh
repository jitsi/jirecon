#!/bin/bash

if [[ "$1" == "--help" ]] || [ $# -lt 2 ]; then
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
    echo -e "\t--subdomain\t sets the subdomain of the external XMPP component. Default value is jirecon."
    echo -e "\t--host\t sets the hostname which will be used for the XMPP server. The default value is \"localhost\""
    echo
    exit 1
fi


kernel="$(uname -s)"
if [ $kernel == "Linux" ] ;then
    SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

    architecture="linux"
    machine="$(uname -m)"
    if [ "$machine" == "x86_64" ] || [ "$machine" == "amd64" ] ; then
        architecture=$architecture"-64"
    fi
else
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    architecture="macosx"
fi

libs="$SCRIPT_DIR/lib/native/$architecture"
mainClass="org.jitsi.jirecon.xmppcomponent.ComponentLauncher"
cp=$(JARS=($SCRIPT_DIR/jirecon.jar $SCRIPT_DIR/lib/*.jar); IFS=:; echo "${JARS[*]}")

LD_LIBRARY_PATH=$libs java -Djava.library.path=$libs -cp $cp $mainClass $@
