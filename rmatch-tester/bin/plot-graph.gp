unset grid
set xlabel "No of regexps"

# logfile="logs/logfile-2013-09-02-13:51:53.csv"

set key left box

# Set the size of the text used on the x and y axis
set xtics font "Times-Roman, 10"
set ytics font "Times-Roman, 10"


# set multiplot layout 3 , 2 title sprintf("Results from %s", logfile)


# This works fine

set title "Time to process 'Wuthering Heights'"
set ylabel "Milliseconds"
plot logfile using 1:2 index 0 title "rmatch" with lines, \
     logfile using 1:3 index 0 title "java regexp" with lines

clear
plot logfile using 1:2 index 0 title "rmatch" with lines

clear
plot logfile using 1:3 index 0 title "java regexp" with lines



set title "Time spent per regexp."
set ylabel "Milliseconds"

clear
plot  logfile using 1:(1000*$2/$1) index 0 title "rmatch" with lines, \
      logfile using 1:(1000*$3/$1) index 0 title "java regexp" with lines

clear
plot  logfile using 1:(1000*$2/$1) index 0 title "rmatch" with lines
clear
plot  logfile using 1:(1000*$3/$1) index 0 title "java regexp" with lines

clear

set title "Trend over time"
set ylabel "matcher/javaregexp ratio"
plot logfile using 1:4 index 0 title "Ratio" with lines

#unset multiplot