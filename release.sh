#!/bin/bash

set -eu -o pipefail

VERSION=$(egrep -o '[0-9]+' <app/version.gradle)

VERSION_NEXT=$(( VERSION + 1 ))
VERSION_PREVIOUS=$(curl -s https://app.pr0gramm.com/beta/open/update.json | jq .version)

source upload_auth

# check if we are clear to go
if [ -n "$(git status --porcelain)" ] ; then
  echo "Please commit all your changes and clean working directory."
  git status
  exit 1
fi

echo "Release steps:"
echo " * Start release of version $VERSION (current beta is $VERSION_PREVIOUS)"
echo " * Upload apk to the update manager using auth $CREDENTIALS_UPDATE'"
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
  local APK_ALIGNED=app/build/outputs/apk/release/app-release.apk
  local TAG=$(format_version ${VERSION})

  echo "Upload apk file now..."
  curl -u "$CREDENTIALS_UPDATE" -F apk=@"${APK_ALIGNED}" \
    https://app.pr0gramm.com/update-manager/upload

  echo "Upload apk file to github"
  ./upload.sh github_api_token="${CREDENTIALS_GITHUB}" \
    owner="mopsalarm" repo="pr0" tag="$TAG" \
    filename="${APK_ALIGNED}"

  ssh apk.pr0gramm.com "wget -O www/pr0gramm-$TAG.apk \
    https://github.com/mopsalarm/Pr0/releases/download/$TAG/app-release.apk"
}

# compile code and create apks
rm -rf -- api/build/* app/build/*
./gradlew assembleRelease generateDebugSources "$@"

# verify apk
if ! unzip -t app/build/outputs/apk/release/app-release.apk | grep publicsuffixes.gz ; then
    echo "Could not find publicsuffixes.gz in the apk"
    exit 1
fi

# create tag for this version
git tag -a "$(format_version ${VERSION})" \
        -m "Released version $(format_version ${VERSION})"

# increase app version for further development
echo "ext { appVersion = $VERSION_NEXT }" > app/version.gradle
git add app/version.gradle
git commit -m "Increase version to $VERSION_NEXT after release"
git push
git push --tags

deploy_upload_apk

# link to the release manager
echo "Go to the release manager at https://$CREDENTIALS_UPDATE@app.pr0gramm.com/update-manager/"
