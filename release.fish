#!/usr/bin/fish

if [ (count $argv) -eq 1 ]
  set version (cat app/version.gradle | egrep -o '[0-9]+')
  set changelog $argv[1]

else if [ (count $argv) -eq 2 ]
  set version $argv[1]
  set changelog $argv[2]

else
  echo "usage: "(status -f)" [version] changelog"
  exit 1
end

# check if we are clear to go
if [ -n "(git status --porcelain)" ]
  echo "Please commit all your changes and clean working directory."
  git status
  exit 1
end

# path of the update repo
set update_repo ../pr0gramm-updates

echo "Release steps:"
echo " * Start release of version $version."
echo " * Copy apk and json files to $update_repo and commit"
echo " * Create tag for version v$version"
echo " * Increase version to $version_next"
echo ""

# user needs to type yes to continue
read -p 'echo "Is this correct?: "' confirm
[ $confirm != "yes" ] ; and exit 1

function generate_update_json
  set flavor $argv[1]
  set url https://cdn.rawgit.com/mopsalarm/pr0gramm-updates/beta/$flavor/pr0gramm-v1.$version.apk

  echo "{}" \
    | jq '.version = '$version \
    | jq '.versionStr = "1.'(math $version/10)'.'(math $version%10)'"' \
    | jq '.changelog = "'$changelog'"' \
    | jq '.apk = "'$url'"' > $flavor/update.json

  git -C $update_repo add $flavor/update.json
end

function copy_apk_file
  set flavor $argv[1]
  cp app/build/outputs/apk/app-$flavor-release.apk  $update_repo/$flavor/pr0gramm-v1.$version.apk
  git -C $update_repo add $flavor/pr0gramm-v1.$version.apk
end

# compile code and create apks
./gradlew clean assembleRelease generateOpenDebugSources ; or exit 1

# copy apks and generate update.json in beta branch
git -C $update_repo checkout beta
git -C $update_repo pull
for flavor in "open" "play"
  copy_apk_file $flavor
  generate_update_json $flavor
end

# commit those files
git -C $update_repo commit -m "Version $version"

# create tag for this version
git tag -a "v$version"

# increase app version for further development
echo "ext { appVersion = $version_next }" > app/version.gradle
git add app/version.gradle
git commit -m "Increase version to $version_next after release"
