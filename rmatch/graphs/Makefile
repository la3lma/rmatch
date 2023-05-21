DOT=dot
SRC_DIR:=.
OBJ_DIR:=.
GV_FILES := $(wildcard $(SRC_DIR)/*.gv)
PDF_FILES := $(patsubst $(SRC_DIR)/%.gv,$(OBJ_DIR)/%.pdf,$(GV_FILES))

info:
	echo $(GV_FILES)
	echo $(PDF_FILES)

clean:
	-rm  -f *.pdf *.ps

%.pdf: %.gv
	$(DOT) -Tpdf $<  -o  $@


all: $(PDF_FILES)

viewDfas: clean all
	open -a Preview *Dfa*.pdf
