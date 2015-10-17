#!/bin/bash

set -eu -o pipefail

if [ $# -eq 1 ] ; then
  VERSION=$(egrep -o '[0-9]+' <app/version.gradle)
  CHANGELOG="$1"

else
  echo "usage: $(basename "$0") changelog"
  exit 1
fi

# check if we are clear to go
if [ -n "$(git status --porcelain)" ] ; then
  echo "Please commit all your changes and clean working directory."
  git status
  exit 1
fi

# path of the update repo
UPDATE_REPO_PATH=../pr0gramm-updates
VERSION_NEXT=$(( VERSION + 1 ))
VERSION_PREVIOUS=$(jq .version < $UPDATE_REPO_PATH/open/update.json)

echo "Release steps:"
echo " * Start release of version $VERSION (current is $VERSION_PREVIOUS)"
echo " * Copy apk and json files to $UPDATE_REPO_PATH and commit"
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

function deploy_make_update_json() {
  local FLAVOR=$1
  local URL="https://cdn.rawgit.com/mopsalarm/pr0gramm-updates/beta/$FLAVOR/pr0gramm-v1.$VERSION.apk"

  echo "{}" \
    | jq ".version = $VERSION" \
    | jq ".versionStr = \"1.$(( VERSION/10 )).$((VERSION % 10 ))\"" \
    | jq ".changelog = \"$CHANGELOG\"" \
    | jq ".apk = \"$URL\"" > $UPDATE_REPO_PATH/$FLAVOR/update.json

  git -C $UPDATE_REPO_PATH add $FLAVOR/update.json
}

function deploy_copy_apk_file() {
  local FLAVOR=$1
  cp app/build/outputs/apk/app-$FLAVOR-release.apk  $UPDATE_REPO_PATH/$FLAVOR/pr0gramm-v1.$VERSION.apk
  git -C $UPDATE_REPO_PATH add $FLAVOR/pr0gramm-v1.$VERSION.apk
}

# compile code and create apks
./gradlew clean assembleRelease generateOpenDebugSources

# copy apks and generate update.json in beta branch
git -C $UPDATE_REPO_PATH checkout beta
git -C $UPDATE_REPO_PATH pull
for FLAVOR in "open" "play" ; do
  deploy_copy_apk_file $FLAVOR
  deploy_make_update_json $FLAVOR
done

# commit those files
git -C $UPDATE_REPO_PATH commit -m "Version $(format_version $VERSION)"

# create tag for this version
git tag -a "$(format_version $VERSION)" \
        -m "Released version $(format_version $VERSION)"

# increase app version for further development
echo "ext { appVersion = $VERSION_NEXT }" > app/version.gradle
git add app/version.gradle
git commit -m "Increase version to $VERSION_NEXT after release"
git push
git push --tags
