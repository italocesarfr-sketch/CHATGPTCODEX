#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/domain-tests"
mkdir -p "$OUT"
# Funciona também quando o JDK tem o módulo compilador, mas não o executável javac.
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -encoding UTF-8 -d "$OUT" \
    "$ROOT"/app/src/main/java/br/com/diariodeclasse/domain/*.java \
    "$ROOT"/app/src/test/java/br/com/diariodeclasse/DomainCases.java
java -cp "$OUT" br.com.diariodeclasse.DomainCases
