rm bin/*
i586-mingw32msvc-c++ -mwindows -o bin/tailserver.exe src/tail.cpp
rm compiled.zip ; zip -r compiled.zip bin/

