#!/bin/sh
# Public domain. Originally written by Norbert Preining and Karl Berry, 2018.

scriptname=`basename "$0"`
jar="$scriptname.jar"
jarpath=`kpsewhich --progname="$scriptname" --format=texmfscripts "$jar"`

kernel=`uname -s 2>/dev/null`
if test "${kernel#*CYGWIN}" != "$kernel"
  CYGWIN_ROOT=`cygpath -w /`
  export CYGWIN_ROOT
  jarpath=`cygpath -w "$jarpath"`
fi

exec java -jar "$jarpath" "$@"

