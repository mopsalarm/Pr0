#!/bin/sh

set -e

curl -fsS -L http://app.pr0gramm.com/pr0gramm-latest.apk > /tmp/pr0gramm-stable.apk
adb install -r -d /tmp/pr0gramm-stable.apk

