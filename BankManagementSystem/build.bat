@echo off
rem Compile everything into out\  (needs JDK 17 or later)
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java test\*.java > sources.txt
javac -d out @sources.txt
del sources.txt
echo Build OK
