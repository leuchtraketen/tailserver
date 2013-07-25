rm bin/*
i586-mingw32msvc-c++ -mwindows -o bin/tailserver.exe src/tail.cpp -Wl,--subsystem,windows
rm compiled.zip ; zip -r compiled.zip bin/

