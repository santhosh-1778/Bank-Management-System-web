@echo off
if not exist out mkdir out
for /R src %%f in (*.java) do echo %%f >> sources.txt
javac -d out @sources.txt
del sources.txt
java -cp out WebServer --demo
