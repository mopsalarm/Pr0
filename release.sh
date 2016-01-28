#!/bin/bash

set -eu -o pipefail

if [ $# -eq 0 ] ; then
  VERSION=$(egrep -o '[0-9]+' <app/version.gradle)

else
  echo "usage: $(basename "$0")"
  exit 1
fi

VERSION_NEXT=$(( VERSION + 1 ))
VERSION_PREVIOUS=$(curl -s https://app.pr0gramm.com/beta/open/update.json | jq .version)
UPLOAD_AUTH=$(<upload_auth)

# check if we are clear to go
if [ -n "$(git status --porcelain)" ] ; then
  echo "Please commit all your changes and clean working directory."
  git status
  exit 1
fi

echo "Release steps:"
echo " * Start release of version $VERSION (current beta is $VERSION_PREVIOUS)"
echo " * Upload apk to the update manager using auth '$UPLOAD_AUTH'"
echo " * Create tag for version v$VERSION"
echo " * Increase version to $VERSION_NEXT"
echo ""

# user needs to type yes to continue
read -p 'Is this correct?: ' CONFIRM || exit 1
[ "$CONFIRM" == "yes" ] || exit 1

function format_version() {
  local VERSION=$1
  echo -n "1."$(( VERSION/10 ))'.'$((VERSION % 10 ))
}

function deploy_upload_apk() {
  local FLAVOR=$1
  local APK=app/build/outputs/apk/app-${FLAVOR}-release.apk

  echo "Upload apk file now..."
  curl -u "$UPLOAD_AUTH" -F apk=@$APK https://app.pr0gramm.com/update-manager/upload
}

# compile code and create apks
./gradlew clean assembleOpenRelease generateOpenDebugSources

for FLAVOR in "open" ; do
  deploy_upload_apk ${FLAVOR}
done

# create tag for this version
git tag -a "$(format_version ${VERSION})" \
        -m "Released version $(format_version ${VERSION})"

# increase app version for further development
echo "ext { appVersion = $VERSION_NEXT }" > app/version.gradle
git add app/version.gradle
git commit -m "Increase version to $VERSION_NEXT after release"
git push
git push --tags
