#!/bin/bash
# Compila Instalar-TFC-Create.exe (Linux/WSL con Go y binutils-mingw-w64)
set -e
cd "$(dirname "$0")"
x86_64-w64-mingw32-windres --preprocessor=cat -O coff -o rsrc_windows_amd64.syso res.rc
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -H windowsgui" -o ../publicar/Instalar-TFC-Create.exe .
rm -f rsrc_windows_amd64.syso
echo "OK -> publicar/Instalar-TFC-Create.exe"
