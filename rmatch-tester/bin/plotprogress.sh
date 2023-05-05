#!/usr/bin/env gnuplot
# Perhaps this http://itb.biologie.hu-berlin.de/~glauser/research/GNUplot_3.html is a better template?, allows
# more dimensions than two in the timeseries, which is good.

clear
set multiplot
set xdata time 
set timefmt "%s" 
set format x "%D\n%R"
set key on
# set grid layerdefault   linetype -1 linecolor rgb "gray"  linewidth 0.200,  linetype -1 linecolor rgb "gray"  linewidth 0.200
set title "Time series plot of time/pace usage of the testWutheringHeightsCorpusWithSomeRegexps test." 
set xlabel "Date the test was run." 
set ylabel "Heap used by test in megabytes"
set y2label "Time used by test in milliseconds."
plot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:3 with linespoints title "Size"

replot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:2 with lines title "Duration"
unset multiplot


