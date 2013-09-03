# set terminal png size 1200,800
# set output "load.png"

unset grid
set xlabel "No of regexps"

# logfile="logs/logfile-2013-09-02-13:51:53.csv"

set key left box

set multiplot layout 3 , 2 title sprintf("Results from %s", logfile)


# This works fine

set title "Time to process 'Wuthering Heights'"
set ylabel "Seconds"
plot logfile using 1:2 index 0 title "rmatch" with lines, \
     logfile using 1:3 index 0 title "java regexp" with lines

plot logfile using 1:2 index 0 title "rmatch" with lines
plot logfile using 1:3 index 0 title "java regexp" with lines



set title "Time spent per regexp."
set ylabel "Milliseconds"

plot  logfile using 1:($2/$1) index 0 title "rmatch" with lines, \
      logfile using 1:($3/$1) index 0 title "java regexp" with lines

plot  logfile using 1:($2/$1) index 0 title "rmatch" with lines
plot  logfile using 1:($3/$1) index 0 title "java regexp" with lines


unset multiplot