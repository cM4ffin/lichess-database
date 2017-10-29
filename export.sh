#!/bin/bash

month=${1}
dir=${2}

export() {
    echo "---------------------------"
    ./export-single-variant.sh $month $dir $1
}

variants="standard chess960 antichess atomic crazyhouse horde kingOfTheHill racingKings threeCheck"

for variant in $variants; do
  export $variant
done
