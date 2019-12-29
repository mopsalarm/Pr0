#!/bin/bash

set -eu -o pipefail

VERSION_PREV=$(egrep -o '[0-9]+' <app/version.gradle)

VERSION_NEXT=$(( VERSION_PREV + 1 ))
VERSION_LIVE=$(curl -s https://app.pr0gramm.com/updates/stable/update.json | jq .version)

source upload_auth

# check if we are clear to go
if [[ -n "$(git status --porcelain)" ]] ; then
  echo "Please commit all your changes and clean working directory."
  git status
  exit 1
fi

echo "Release steps:"
echo " * Increase version to $VERSION_NEXT"
echo " * Start release of version $VERSION_NEXT (current beta is $VERSION_LIVE)"
echo " * Upload apk to the update manager using auth $CREDENTIALS_UPDATE'"
echo " * Create tag for version v$VERSION_NEXT"
echo ""

# user needs to type yes to continue
read -p 'Is this correct?: ' CONFIRM || exit 1
[[ "$CONFIRM" == "yes" ]] || exit 1

function format_version() {
  local VERSION=$1
  echo -n "1."$(( VERSION/10 ))'.'$((VERSION % 10 ))
}

function deploy_upload_apk() {
  local APK_ALIGNED=app/build/outputs/apk/release/app-release.apk
  local TAG=$(format_version ${VERSION_NEXT})

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

# increase app version for further development
echo "ext { appVersion = $VERSION_NEXT }" > app/version.gradle

trap 'git checkout app/version.gradle' ERR

# compile code and create apks
rm -rf -- model/build/* app/build/*
./gradlew --console=plain --no-daemon clean assembleRelease

# verify apk
if ! unzip -t app/build/outputs/apk/release/app-release.apk | grep publicsuffixes.gz ; then
    echo "Could not find publicsuffixes.gz in the apk"
    exit 1
fi

# verify apk
if unzip -t app/build/outputs/apk/release/app-release.apk | grep classes2.dex ; then
    echo "Found classes2.dex in the apk"
    exit 1
fi

git add app/version.gradle
git commit -m "Released version $VERSION_NEXT"

trap - ERR

# create tag for this version
git tag -a "$(format_version ${VERSION_NEXT})" \
        -m "Released version $(format_version ${VERSION_NEXT})"

git push
git push --tags

deploy_upload_apk

# generate debug sources in a final step.
echo "Prepare next dev cycle..."
./gradlew --console=plain --no-daemon clean generateDebugSources > /dev/null

# link to the release manager
echo "Go to the release manager at https://$CREDENTIALS_UPDATE@app.pr0gramm.com/update-manager/"
