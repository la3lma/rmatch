# set terminal pngcairo  transparent enhanced font "arial,10" fontscale 1.0 size 500, 350 
# set output 'multiaxis.1.png'

# set output 'testWutheringHeightsCorpusWithSomeRegexps.pdf'
# set xdata time 
# set timefmt "%s" 
# set format x "%D\n%R"
# set key on
# plot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:3 with lines

set dummy jw,y
set grid nopolar
set grid xtics nomxtics noytics nomytics noztics nomztics \
 nox2tics nomx2tics y2tics nomy2tics nocbtics nomcbtics
set grid layerdefault   linetype -1 linecolor rgb "gray"  linewidth 0.200,  linetype -1 linecolor rgb "gray"  linewidth 0.200
set key inside center top vertical Right noreverse enhanced autotitles nobox
set logscale x 10
set logscale y 10
set logscale x2 10
set xtics border out scale 1,0.5 mirror norotate  offset character 0, 0, 0 autojustify
set ytics border out scale 1,0.5 nomirror norotate  offset character 0, 0, 0 autojustify
set ztics border out scale 1,0.5 nomirror norotate  offset character 0, 0, 0 autojustify
set y2tics border out scale 1,0.5 nomirror norotate  offset character 0, 0, 0 autojustify
set y2tics autofreq  norangelimit
set cbtics border out scale 1,0.5 mirror norotate  offset character 0, 0, 0 autojustify
set rtics axis out scale 1,0.5 nomirror norotate  offset character 0, 0, 0 autojustify
set title "Transistor Amplitude and Phase Frequency Response" 
set xlabel "jw (radians)" 
set xrange [ 1.10000 : 90000.0 ] noreverse nowriteback
set x2range [ 1.10000 : 90000.0 ] noreverse nowriteback
set ylabel "magnitude of A(jw)" 
set y2label "Phase of A(jw) (degrees)" 
A(jw) = ({0,1}*jw/({0,1}*jw+p1)) * (1/(1+{0,1}*jw/p2))
p1 = 10
p2 = 10000
GPFUN_A = "A(jw) = ({0,1}*jw/({0,1}*jw+p1)) * (1/(1+{0,1}*jw/p2))"
plot abs(A(jw)) axes x1y1, 180./pi*arg(A(jw)) axes x2y2
# plot 'measurements/testWutheringHeightsCorpusWithSomeRegexps.csv' every::1::56 using 1:3 with lines