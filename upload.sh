#!/usr/bin/env bash
#
# Author: Stefan Buck
# https://gist.github.com/stefanbuck/ce788fee19ab6eb0b4447a85fc99f447
#
#
# This script accepts the following parameters:
#
# * owner
# * repo
# * tag
# * filename
# * github_api_token
#
# Script to upload a release asset using the GitHub API v3.
#
# Example:
#
# upload-github-release-asset.sh github_api_token=TOKEN owner=stefanbuck repo=playground tag=v0.1.0 filename=./build.zip
#

# Check dependencies.
set -e

# Validate settings.
[ "$TRACE" ] && set -x

CONFIG="$@"

function parse_arguments {
    for LINE in $CONFIG; do
      if ! (echo "$LINE" | egrep -qm1 "^[a-z_]+=") ; then
        echo "Invalid argument format: $LINE"
        exit 1
      fi
      eval "$LINE"
    done

    for NAME in "$@" ; do
        if [ -z "$NAME" ] ; then
            echo "Parameter $NAME not defined."
            exit 1
        fi
    done
}

parse_arguments "owner" "repo" "tag" "github_api_token" "filename"

# Define variables.
GH_API="https://api.github.com"
GH_REPO="$GH_API/repos/$owner/$repo"
GH_TAGS="$GH_REPO/releases/tags/$tag"
AUTH="Authorization: token $github_api_token"
CURL_ARGS="-LJO#"

if [[ "$tag" == 'LATEST' ]]; then
  GH_TAGS="$GH_REPO/releases/latest"
fi

# Validate token.
echo "Validating auth token"
curl -o /dev/null -fsSH "$AUTH" $GH_REPO || { echo "Error: Invalid repo, token or network issue!";  exit 1; }

echo "Create a new release (if not already created)"
RELEASE="$(echo "{'tag_name': '$tag', 'target_commitish': 'master', 'name': 'v$tag', 'body': 'App version $tag'}" | tr \' \")"
curl -o /dev/null -XPOST -fsH "$AUTH" -d "$RELEASE" $GH_REPO/releases || true

echo "Read release id from the tag."
RESPONSE=$(curl -sSH "$AUTH" $GH_TAGS)
eval $(echo "$RESPONSE" | grep -m 1 "id.:" | grep -w id | tr : = | tr -cd '[[:alnum:]]=')
[ "$id" ] || { echo "Error: Failed to get release id for tag: $tag"; echo "$RESPONSE" | awk 'length($0)<100' >&2; exit 1; }

echo "Uploading asset..." >&2
GH_ASSET="https://uploads.github.com/repos/$owner/$repo/releases/$id/assets?name=$(basename $filename)"
curl -o /dev/null -sSfH "$AUTH" --data-binary @"$filename" -H "Content-Type: application/octet-stream" $GH_ASSET

URL="https://github.com/$owner/$repo/releases/download/$tag/app-release.apk"
echo "URL for the released file is now: $URL"

echo "Validating files..."
curl -LfsS $URL | md5sum "$filename" -
