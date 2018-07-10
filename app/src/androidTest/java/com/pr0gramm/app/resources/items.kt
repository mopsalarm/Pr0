package com.pr0gramm.app.resources

import org.intellij.lang.annotations.Language

const val ItemsGetEmpty = """
{
  "atEnd": false,
  "atStart": true,
  "error": null,
  "items": [],
  "ts": 1531241492,
  "cache": "stream:new:1:tabcef:n1",
  "rt": 2,
  "qc": 1
}
"""

const val ItemsGetRepost = """
{
  "atEnd": false,
  "atStart": true,
  "error": null,
  "items": [
    {
      "id": 4,
      "promoted": 3,
      "up": 638,
      "down": 59,
      "created": 1169585592,
      "image": "2007/01/girlfriend.jpg",
      "thumb": "2007/01/girlfriend.jpg",
      "fullsize": "",
      "width": 815,
      "height": 604,
      "audio": false,
      "source": "http://www.rib-it.de/temp/girlfriend.jpg",
      "flags": 1,
      "user": "rib",
      "mark": 2
    }
  ],
  "ts": 1531239543,
  "cache": "stream:new:1",
  "rt": 3,
  "qc": 1
}
"""

const val ItemsGetSFW = """
{
  "atEnd": false,
  "atStart": true,
  "error": null,
  "items": [
    {
      "id": 4,
      "promoted": 3,
      "up": 638,
      "down": 59,
      "created": 1169585592,
      "image": "2007/01/girlfriend.jpg",
      "thumb": "2007/01/girlfriend.jpg",
      "fullsize": "",
      "width": 815,
      "height": 604,
      "audio": false,
      "source": "http://www.rib-it.de/temp/girlfriend.jpg",
      "flags": 1,
      "user": "rib",
      "mark": 2
    },
    {
      "id": 5,
      "promoted": 0,
      "up": 338,
      "down": 72,
      "created": 1169585652,
      "image": "2007/01/earless-cat12.jpg",
      "thumb": "2007/01/earless-cat12.jpg",
      "fullsize": "",
      "width": 450,
      "height": 339,
      "audio": false,
      "source": "http://www.funpic.tv/wp-content/earless_cat12.jpg",
      "flags": 1,
      "user": "Pesthoernchen",
      "mark": 4
    }
  ],
  "ts": 1531239543,
  "cache": "stream:new:1",
  "rt": 3,
  "qc": 1
}
"""

@Language("JSON")
const val ItemsInfo4 = """{
  "tags": [
    {
      "id": 286468,
      "confidence": 0.675584,
      "tag": "girlfriends"
    },
    {
      "id": 1259482,
      "confidence": 0.206543,
      "tag": "porn"
    },
    {
      "id": 1138434,
      "confidence": 0.879353,
      "tag": "pr0n"
    },
    {
      "id": 10018599,
      "confidence": 0.438494,
      "tag": "relationship"
    },
    {
      "id": 28,
      "confidence": 0.206543,
      "tag": "sfw"
    }
  ],
  "comments": [
    {
      "id": 75716,
      "parent": 0,
      "content": "lachte",
      "created": 1368964824,
      "up": 12,
      "down": 3,
      "confidence": 0.548141,
      "name": "L4wl",
      "mark": 4
    },
    {
      "id": 10908433,
      "parent": 10892748,
      "content": "jep",
      "created": 1474234067,
      "up": 20,
      "down": 0,
      "confidence": 0.83887,
      "name": "rib",
      "mark": 2
    }
  ],
  "ts": 1531243453,
  "cache": "item:4",
  "rt": 2,
  "qc": 2
}
"""