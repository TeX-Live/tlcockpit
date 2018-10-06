#
# release.sh

PROJECT=tlcockpit
DIR=`pwd`/..
VER=${VER:-`grep ^version build.sbt | awk -F\" '{print$2}'`}

TEMP=/tmp

echo "Making Release $VER. Ctrl-C to cancel."
read REPLY
if test -d "$TEMP/$PROJECT-$VER"; then
  echo "Warning: the directory '$TEMP/$PROJECT-$VER' is found:"
  echo
  ls $TEMP/$PROJECT-$VER
  echo
  echo -n "I'm going to remove this directory. Continue? yes/No"
  echo
  read REPLY <&2
  case $REPLY in
    y*|Y*) rm -rf $TEMP/$PROJECT-$VER;;
    *) echo "Aborted."; exit 1;;
  esac
fi
echo
git commit -m "Release $VER" --allow-empty
git archive --format=tar --prefix=$PROJECT-$VER/ HEAD | (cd $TEMP && tar xf -)
git --no-pager log --date=short --format='%ad  %aN  <%ae>%n%n%x09* %s%d [%h]%n' > $TEMP/$PROJECT-$VER/ChangeLog
cd $TEMP
# exclude unnecessary files for CTAN
rm -f $PROJECT-$VER/.gitignore
rm -rf $PROJECT-$VER/scripts/update-tl
rm -rf $PROJECT-$VER/scripts/release.sh

tar zcf $DIR/$PROJECT-$VER.tar.gz $PROJECT-$VER
echo
echo You should execute
echo
echo "  git push && git tag $VER && git push origin $VER"
echo
echo Informations for submitting CTAN: 
echo "  CONTRIBUTION: $PROJECT"
echo "  VERSION:      $VER"
echo "  AUTHOR:       Norbert Preining"
echo "  SUMMARY:      TeX Live Manager GUI frontend"
echo "  DIRECTORY:    support/$PROJECT"
echo "  LICENSE:      free/GPLv3"
echo "  FILE:         $DIR/$PROJECT-$VER.tar.gz"

