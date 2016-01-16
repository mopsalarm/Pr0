#!/bin/sh

if [ $# != 1 ] ; then
	echo "usage: $0 icon.svg"
	exit 1
fi

SVG=$1
PNG=$(basename $SVG .svg).png

# inkscape -d$((90*160/160)) --export-png=../app/src/main/res/drawable-mdpi/$PNG $SVG
inkscape -d$((90*240/160)) --export-png=../app/src/main/res/drawable-hdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/drawable-hdpi/$PNG
git add ../app/src/main/res/drawable-hdpi/$PNG

inkscape -d$((90*320/160)) --export-png=../app/src/main/res/drawable-xhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/drawable-xhdpi/$PNG
git add ../app/src/main/res/drawable-xhdpi/$PNG

inkscape -d$((90*480/160)) --export-png=../app/src/main/res/drawable-xxhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/drawable-xxhdpi/$PNG
git add ../app/src/main/res/drawable-xxhdpi/$PNG
