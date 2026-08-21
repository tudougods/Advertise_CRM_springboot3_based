@echo off
setlocal

set "PROJECT_ROOT=%~dp0.."
set "TEST_LOG=%TEMP%\advertiser-crm-test-%RANDOM%-%RANDOM%.log"

pushd "%PROJECT_ROOT%"
call mvnw.cmd -q test >"%TEST_LOG%" 2>&1
set "TEST_EXIT_CODE=%ERRORLEVEL%"
popd

if "%TEST_EXIT_CODE%"=="0" (
    findstr /b /l /c:"[OK]" "%TEST_LOG%"
    del /q "%TEST_LOG%" >nul 2>&1
    exit /b 0
)

type "%TEST_LOG%"
del /q "%TEST_LOG%" >nul 2>&1
echo Test failed. The full Maven log is shown above.
exit /b %TEST_EXIT_CODE%
