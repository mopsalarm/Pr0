#!/bin/sh

if [ $# != 1 ] ; then
	echo "usage: $0 icon.svg"
	exit 1
fi

SVG=$1
PNG=$(basename $SVG .svg).png

inkscape -d$((90*160/240)) --export-png=../app/src/main/res/drawable-mdpi/$PNG $SVG	
inkscape -d$((90*240/240)) --export-png=../app/src/main/res/drawable-hdpi/$PNG $SVG	
inkscape -d$((90*320/240)) --export-png=../app/src/main/res/drawable-xhdpi/$PNG $SVG	
inkscape -d$((90*480/240)) --export-png=../app/src/main/res/drawable-xxhdpi/$PNG $SVG	

