#!/bin/bash

set -ex

if [ $# != 1 ] ; then
	echo "usage: $0 icon.svg"
	exit 1
fi

SVG=$1
PNG=$(basename $SVG .svg).png

WIDTH=$(identify -format '%w' $SVG)
HEIGHT=$(identify -format '%h' $SVG)

FACTOR=1
if [[ $SVG == *app*.svg ]] ; then
  FACTOR=4
fi

PREFIX=drawable
if [[ $SVG == ic_app.svg || $SVG == ic_roundapp.svg ]] ; then
	PREFIX=mipmap
fi

function width() {
	dc -e "6k $1 160 / $FACTOR / $WIDTH * p"
}

function height() {
	dc -e "6k $1 160 / $FACTOR / $HEIGHT * p"
}

mkdir -p ../app/src/main/res/$PREFIX-hdpi
inkscape -w$(width 240) -h$(height 240) --export-png=../app/src/main/res/$PREFIX-hdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-hdpi/$PNG
git add ../app/src/main/res/$PREFIX-hdpi/$PNG

mkdir -p ../app/src/main/res/$PREFIX-xhdpi
inkscape -w$(width 320) -h$(height 320) --export-png=../app/src/main/res/$PREFIX-xhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-xhdpi/$PNG
git add ../app/src/main/res/$PREFIX-xhdpi/$PNG

mkdir -p ../app/src/main/res/$PREFIX-xxhdpi
inkscape -w$(width 480) -h$(height 480) --export-png=../app/src/main/res/$PREFIX-xxhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-xxhdpi/$PNG
git add ../app/src/main/res/$PREFIX-xxhdpi/$PNG

mkdir -p ../app/src/main/res/$PREFIX-xxxhdpi
inkscape -w$(width 640) -h$(height 640) --export-png=../app/src/main/res/$PREFIX-xxxhdpi/$PNG $SVG
optipng -o7 ../app/src/main/res/$PREFIX-xxxhdpi/$PNG
git add ../app/src/main/res/$PREFIX-xxxhdpi/$PNG
