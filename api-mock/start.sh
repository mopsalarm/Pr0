#!/bin/sh
set -e

VENV=/tmp/api-mock

if [ -d $VENV/bin/activate ] ; then
  . $VENV/bin/activate
else
  virtualenv $VENV
  . $VENV/bin/activate
  pip install git+https://github.com/mopsalarm/mock-server.git
fi

exec mock-server --address=0.0.0.0 --dir="$(dirname "$0")"
