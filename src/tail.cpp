#include <windows.h>
#include <iostream>
#include <cstdlib>
using namespace std;
int main()
{
	WinExec("java tail.TailServer", SW_HIDE);
//    system( "java tail.TailServer" );
}
