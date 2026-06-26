@echo off
setlocal

set "BASELINE_DIR=%~dp0..\..\..\swing-heat-map-baseline"

if not exist "%BASELINE_DIR%\gradlew.bat" (
    echo Baseline worktree was not found:
    echo %BASELINE_DIR%
    echo.
    echo Create it from the main repository with:
    echo git worktree add --detach ..\swing-heat-map-baseline b0658a5
    exit /b 1
)

pushd "%BASELINE_DIR%"

call gradlew.bat clean build
if errorlevel 1 (
    echo Build failed.
    popd
    exit /b 1
)

java -cp build/classes/java/main io.drozda.coding.demo.Starter
set "APP_EXIT_CODE=%ERRORLEVEL%"

popd
exit /b %APP_EXIT_CODE%
