#!/usr/bin/env bash

cat $* | tr -d '[:punct:]'  | awk '{ for (i = 1; i <= NF; i++) print $i }'  | sort | sort -u