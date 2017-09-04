#!/bin/bash -e

# argument file name where search for variable
# argument variable name to find
getvariable()
{
local rez=`grep "^"$2"" "$1" | cut -d= -f 2-100`
# if java writes props it writes \: insteaod of :
# replacing \: with :
rez=${rez//"\:"/:}      #for bash >= 3.0
rez=${rez//'\\:'/:}     #for bash < 3.0
# replacing \= with =
rez=${rez//"\="/=}      #for bash >= 3.0
rez=${rez//'\\='/=}     #for bash < 3.0
echo $rez
}

if [ -z "$MAGICDRAW_HOME" ]; then
    echo "MAGICDRAW_HOME environment variable not set, please set it to the MagicDraw installation folder"
    exit 1
fi

if [ "$OS" = Windows_NT ]; then
    md_home_url_leader=$(echo "$MAGICDRAW_HOME" | sed -e 's/^/\//' -e 's/ /%20/g' -e 's/\\/\//g')
    md_home_url_base=$(echo "$MAGICDRAW_HOME" | sed -e 's/:/%3A/g' -e 's/ /%20/g' \
                                                    -e 's/\//%2F/g' -e 's/\\/%5C/g')
	cp_delim=";"
else
	md_home_url_leader=$(echo "$MAGICDRAW_HOME" | sed -e 's/ /%20/g')
	md_home_url_base=$(echo "$MAGICDRAW_HOME" | sed -e 's/ /%20/g')
	cp_delim=":"
fi

md_props=$MAGICDRAW_HOME/bin/magicdraw.properties

BOOT_CLASSPATH=`getvariable "$md_props" BOOT_CLASSPATH`

md_cp_url=file:$md_home_url_leader/bin/magicdraw.properties?base=$md_home_url_base#CLASSPATH

OSGI_LAUNCHER=$(echo "$MAGICDRAW_HOME"/lib/com.nomagic.osgi.launcher-*.jar)
OSGI_FRAMEWORK=$(echo "$MAGICDRAW_HOME"/lib/bundles/org.eclipse.osgi_*.jar)
MD_OSGI_FRAGMENT=$(echo "$MAGICDRAW_HOME"/lib/bundles/com.nomagic.magicdraw.osgi.fragment_*.jar)

CP="${OSGI_LAUNCHER}${cp_delim}${OSGI_FRAMEWORK}${cp_delim}${MD_OSGI_FRAGMENT}${cp_delim}\
`  `$MAGICDRAW_HOME/lib/md_api.jar${cp_delim}$MAGICDRAW_HOME/lib/md_common_api.jar${cp_delim}\
`  `$MAGICDRAW_HOME/lib/md.jar${cp_delim}$MAGICDRAW_HOME/lib/md_common.jar${cp_delim}\
`  `$MAGICDRAW_HOME/lib/jna.jar"

if [ "$OS" = Darwin ]; then

java -Xmx1200M -Xss1024K \
     -Xbootclasspath/p:$BOOT_CLASSPATH \
     -Dmd.class.path=$md_cp_url \
     -Dcom.nomagic.osgi.config.dir="$MAGICDRAW_HOME/configuration" \
     -Desi.system.config="$MAGICDRAW_HOME/data/application.conf" \
     -Dlogback.configurationFile="$MAGICDRAW_HOME/data/logback.xml" \
     -cp "$CP" \
     -Xdock:name=MagicDraw \
     -Xdock:icon="$MAGICDRAW_HOME/bin/md.icns" \
     -Dapple.laf.useScreenMenuBar=true \
     com.nomagic.osgi.launcher.ProductionFrameworkLauncher \
     "$@"

else

java -Xmx1200M -Xss1024K \
     -Xbootclasspath/p:$BOOT_CLASSPATH \
     -Dmd.class.path=$md_cp_url \
     -Dcom.nomagic.osgi.config.dir="$MAGICDRAW_HOME/configuration" \
     -Desi.system.config="$MAGICDRAW_HOME/data/application.conf" \
     -Dlogback.configurationFile="$MAGICDRAW_HOME/data/logback.xml" \
     -cp "$CP" \
     com.nomagic.osgi.launcher.ProductionFrameworkLauncher \
     "$@"

fi

#-Dcom.nomagic.magicdraw.launcher=com.nomagic.magicdraw.examples.imagegenerator.ExportDiagramImages \
#-Dmd.additional.class.path="$MAGICDRAW_HOME/openapi/examples/imagegenerator/imagegenerator.jar" \
