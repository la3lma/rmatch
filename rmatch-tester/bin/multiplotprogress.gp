# Some general stuff:
set terminal postscript portrait color enhanced "Helvetica" 16
set size 1, 0.5
set out "testplot.ps"
set title "Evolution of test \n testWutheringHeightsCorpusWithSomeRegexps\n Space and duration of test"

set multiplot

# This is for the various terminal settings
# Set the size for the whole figure (x, y)
# Define the path, the name & the file format of your figure 
# Set the title for your figure, use "\n" to go to another line
# Switch the script to "multiplot" mode
# The first plot (top) of the figure:

set tmarg 0
set bmarg 0
set lmarg 7
set rmarg 3
set size 1, 0.1
set origin 0.0, 0.315
#set xrange [0.300:0.500]
set yrange [600:1000]
set label 1 "ms" at graph -0.125, graph 0.5 center rotate
set ytics nomirror
set ytics (600, 700, 800, 1000)
set format x ""
unset key
plot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:2 with lines
# plot "datafile_stimulus.dat" u 1:2 w l lw 2 lt 1
# f(x)=37.8
# replot f(x) w l lw 2 lt 2


# Set the top margin, see 1.) for details
# Set the bottom margin, see 1.) for details
# Set the left margin, see 1.) for details
# Set the right margin, see 1.) for details
# Set the size for the top plot, see 1.) for details
# Set the origin (= position) of the top plot, see 1.) for details
# Set the range of the x-axis of the top plot
# Set the range of the y-axis of the top plot
# Set the label for the y-axis of the top plot, see 2.) for details
# No tics of the left y-axis are mirrored on the right y-axis of the top plot
# Manually set three tics for the y-axis of the top plot: "0", "50" and  "100"
# No tics on the x-axis of the top plot
# No mentioning of the datafile in the top plot 
# See 3.) for details 
# Plot a horizontal line at y=37.8
# Plot f(x), use "replot" because you plot something additional into an existing plot
# The second plot (in the middle)of the figure:
set tmarg 0
set bmarg 0
set lmarg 7
set rmarg 3
set size 1, 0.1
set origin 0.0, 0.195
# set xrange [300:500]
set yrange [10:30]
set label 1 "MB"
set ytics nomirror
set ytics (0, 10,20, 30)
# set label "MB" at graph 0.3, 0.7
#set format x ""

# XXX Time scale is not displayed, that is bogus
#     But apart from that the  plots now start to look reasonable.
set xdata time 
set timefmt "%s" 
set format x "%D\n%R"
set key on
set xtics (1342684089, 1342693532)

unset title
unset key

# plot "datafile_raster.dat" u 1:2 w l lw 2 lt -1
plot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:3 with lines
unset label

# # Set the top margin, see 1.) for details
# # Set the bottom margin, see 1.) for details
# # Set the left margin, see 1.) for details
# # Set the right margin, see 1.) for details
# # Set the size for the top plot, see 1.) for details
# # Set the origin (= position) of the top plot, see 1.) for details
# # Set the range of the x-axis of the middle plot
# # Set the range of the y-axis of the middle plot
# # Set the label for the y-axis of the middle plot, see 2.) for details
# # No tics of the left y-axis are mirrored on the right y-axis of the middle plot
# # Manually set three tics: "0", "5" and  "10" - for the  y-axis of the middle plot
# # No tics on the x-axis of the middle plot
# # No title for the middle plot
# # No mentioning of the datafile in the middle plot 
# # See 3.) for details 
# # The plot at the bottom:
# set tmarg 0
# set bmarg 0
# set lmarg 7
# set rmarg 3
# set size 1, 0.1
# set origin 0.0, 0.075
# # set xrange [300:500]
# set yrange [0:400]
# set xlabel "Date of test run"
# set label 1 "Duration (ms)"
# set ytics nomirror
# #set ytics (0, 200, 400)
# set format x "%g"
# unset title
# unset key
# #plot "datafile_FR.dat" u 2:1 w l lw 2 lt 1
# plot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:2 with linespoints

unset multiplot	
# Set the top margin, see 1.) for details
# Set the bottom margin, see 1.) for details
# Set the left margin, see 1.) for details
# Set the right margin, see 1.) for details
# Set the size for the top plot, see 1.) for details
# Set the origin (= position) of the top plot, see 1.) for details
# Set the range of the x-axis for the bottom plot
# Set the range of the y-axis for the bottom plot
# Set the label for the y-axis for the bottom plot
# Set the label for the x-axis for the bottom plot, see 2.) for details
# No tics of the left y-axis are mirrored on the right y-axis for the bottom plot
# Manually set three tics for the y-axis of the bottom plot: "0", "200" and "400"
# The bottom plot must have tics
# Put label "kernel type: gaussian (sigma: 2ms)" at position 0.3, 0.7 in bottom plot
# No title for the bottom plot
# No mentioning of the datafile in the bottom plot
# See 3.) for details 

# Deactivate multiplot mode