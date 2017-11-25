#!/bin/bash

set -ex

if [ $# != 1 ] ; then
	echo "usage: $0 icon.svg"
	exit 1
fi

SVG=$1
PNG=$(basename $SVG .svg).png

FACTOR=1
if [[ $SVG == *app*.svg ]] ; then
  FACTOR=4
fi

PREFIX=drawable
if [[ $SVG == ic_app.svg || $SVG == ic_roundapp.svg ]] ; then
	PREFIX=mipmap
fi

function dpi() {
	dc -e "6k 90 $1 * $FACTOR 160 * / p"
}

mkdir -p ../app/src/main/res/$PREFIX-hdpi
inkscape -d$(dpi 240) --export-png=../app/src/main/res/$PREFIX-hdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-hdpi/$PNG
git add ../app/src/main/res/$PREFIX-hdpi/$PNG

mkdir -p ../app/src/main/res/$PREFIX-xhdpi
inkscape -d$(dpi 360) --export-png=../app/src/main/res/$PREFIX-xhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-xhdpi/$PNG
git add ../app/src/main/res/$PREFIX-xhdpi/$PNG

mkdir -p ../app/src/main/res/$PREFIX-xxhdpi
inkscape -d$(dpi 480) --export-png=../app/src/main/res/$PREFIX-xxhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-xxhdpi/$PNG
git add ../app/src/main/res/$PREFIX-xxhdpi/$PNG

mkdir -p ../app/src/main/res/$PREFIX-xxxhdpi
inkscape -d$(dpi 640) --export-png=../app/src/main/res/$PREFIX-xxxhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-xxxhdpi/$PNG
git add ../app/src/main/res/$PREFIX-xxxhdpi/$PNG
