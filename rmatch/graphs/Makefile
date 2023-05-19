DOT=dot

clean:
	-rm  -f *.pdf *.ps

%.pdf: %.gv
	$(DOT) -Tpdf $<  -o  $@


all: ladenPostRunningDfa.pdf ladenPostRunningNdfa.pdf
