@echo off
REM Use -Dexit.after.run=true so the app runs the flow then exits.
java -Dexit.after.run=true -jar "target\webhook-sql-submitter-0.0.1-SNAPSHOT.jar"
echo.
echo Done. Press any key to close...
pause >nul