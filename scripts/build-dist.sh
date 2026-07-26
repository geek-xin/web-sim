#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUN_TESTS=false
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --with-tests)
      RUN_TESTS=true
      shift
      ;;
    -h|--help)
      cat <<'USAGE'
Usage: scripts/build-dist.sh [--with-tests]

Build the React admin UI, package the executable Spring Boot jar, and create
release archives:
  - target/web-sim-<version>.tar.gz
  - target/web-sim-<version>.zip
  - target/dist/web-sim-<version>.tar.gz
  - target/dist/web-sim-<version>.zip

Tests are skipped by default during Maven packaging. Pass --with-tests to run
the full Maven test phase.

The release archive contains:
  - executable Spring Boot jar
  - run.sh / stop.sh for Linux and macOS
  - run.bat / stop.bat for Windows
  - config/application.yml as external Spring Boot configuration
  - config/simulations/ for simulation JSON files
  - README.md when present

Options:
  --with-tests   Run tests during Maven package
  -h, --help     Show this help
USAGE
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Run scripts/build-dist.sh --help for usage." >&2
      exit 2
      ;;
  esac
done

require_command() {
  local command_name=$1
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required but was not found in PATH." >&2
    exit 1
  fi
}

copy_simulation_configs() {
  local source_dir=$1
  local target_dir=$2

  mkdir -p "$target_dir"
  if [[ ! -d "$source_dir" ]]; then
    return 0
  fi

  find "$source_dir" -maxdepth 1 -type f \( -name '*.json' -o -name '.gitkeep' \) -exec cp {} "$target_dir/" \;
}

convert_to_crlf() {
  local file_path=$1
  local tmp_path="${file_path}.tmp"

  awk '{ printf "%s\r\n", $0 }' "$file_path" > "$tmp_path"
  mv "$tmp_path" "$file_path"
}

require_command mvn
require_command npm
require_command tar
require_command zip

if [[ -d frontend ]]; then
  echo "==> Building React admin UI"
  (cd frontend && npm install && npm run build)
fi

if [[ "$RUN_TESTS" == "true" ]]; then
  echo "==> Building Spring Boot jar: mvn clean package"
  mvn clean package
else
  echo "==> Building Spring Boot jar: mvn clean package -DskipTests"
  mvn clean package -DskipTests
fi

ARTIFACT_ID="$(mvn help:evaluate -Dexpression=project.artifactId -q -DforceStdout)"
VERSION="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"
APP_NAME="${ARTIFACT_ID}-${VERSION}"
DIST_ROOT="${ROOT_DIR}/target/dist"
STAGING_DIR="${DIST_ROOT}/${APP_NAME}"
DIST_TAR_PATH="${DIST_ROOT}/${APP_NAME}.tar.gz"
TARGET_TAR_PATH="${ROOT_DIR}/target/${APP_NAME}.tar.gz"
DIST_ZIP_PATH="${DIST_ROOT}/${APP_NAME}.zip"
TARGET_ZIP_PATH="${ROOT_DIR}/target/${APP_NAME}.zip"
JAR_PATH="${ROOT_DIR}/target/${APP_NAME}.jar"
APPLICATION_CONFIG="${ROOT_DIR}/src/main/resources/application.yml"
SIMULATION_CONFIG_DIR="${ROOT_DIR}/config/simulations"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Expected jar was not found: $JAR_PATH" >&2
  exit 1
fi

if [[ ! -f "$APPLICATION_CONFIG" ]]; then
  echo "Expected backend config was not found: $APPLICATION_CONFIG" >&2
  exit 1
fi

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/config/simulations"

cp "$JAR_PATH" "$STAGING_DIR/"
cp "$APPLICATION_CONFIG" "$STAGING_DIR/config/application.yml"
copy_simulation_configs "$SIMULATION_CONFIG_DIR" "$STAGING_DIR/config/simulations"

if [[ -f README.md ]]; then
  cp README.md "$STAGING_DIR/"
fi

cat > "$STAGING_DIR/run.sh" <<RUNEOF
#!/bin/sh
set -eu
APP_DIR=\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)
cd "\${APP_DIR}"

JAR_FILE="\${APP_DIR}/${APP_NAME}.jar"
PID_FILE="\${APP_DIR}/web-sim.pid"
LOG_DIR="\${APP_DIR}/logs"
BOOTSTRAP_OUT_FILE="\${LOG_DIR}/web-sim.bootstrap.out"
BOOTSTRAP_ERR_FILE="\${LOG_DIR}/web-sim.bootstrap.err"
START_WAIT_SECONDS="\${WEB_SIM_START_WAIT_SECONDS:-5}"

pid_matches_app() {
  pid=\$1
  command=\$(ps -p "\${pid}" -o command= 2>/dev/null || true)
  case "\${command}" in
    "") return 0 ;;
    *"\${JAR_FILE}"*) return 0 ;;
    *) return 1 ;;
  esac
}

if [ ! -f "\${JAR_FILE}" ]; then
  echo "Jar file not found: \${JAR_FILE}" >&2
  exit 1
fi

if [ -f "\${PID_FILE}" ]; then
  OLD_PID=\$(cat "\${PID_FILE}" 2>/dev/null || true)
  if [ -n "\${OLD_PID}" ] && kill -0 "\${OLD_PID}" 2>/dev/null && pid_matches_app "\${OLD_PID}"; then
    echo "web-sim is already running, pid=\${OLD_PID}"
    exit 0
  fi
  rm -f "\${PID_FILE}"
fi

mkdir -p "\${LOG_DIR}" "\${APP_DIR}/config/simulations" "\${APP_DIR}/logs/simulations"
nohup java -jar "\${JAR_FILE}" "\$@" > "\${BOOTSTRAP_OUT_FILE}" 2> "\${BOOTSTRAP_ERR_FILE}" &
APP_PID=\$!
echo "\${APP_PID}" > "\${PID_FILE}"

sleep "\${START_WAIT_SECONDS}"
if ! kill -0 "\${APP_PID}" 2>/dev/null; then
  rm -f "\${PID_FILE}"
  echo "web-sim failed to start. Recent log output:" >&2
  tail -n 80 "\${BOOTSTRAP_ERR_FILE}" >&2 || true
  tail -n 80 "\${BOOTSTRAP_OUT_FILE}" >&2 || true
  exit 1
fi

echo "web-sim started, pid=\${APP_PID}"
echo "Bootstrap log: \${BOOTSTRAP_OUT_FILE}"
echo "Bootstrap error log: \${BOOTSTRAP_ERR_FILE}"
echo "Admin URL: http://localhost:9998/admin"
RUNEOF
chmod +x "$STAGING_DIR/run.sh"

cat > "$STAGING_DIR/stop.sh" <<STOPEOF
#!/bin/sh
set -eu
APP_DIR=\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)
cd "\${APP_DIR}"

APP_NAME="${APP_NAME}"
JAR_FILE="\${APP_DIR}/\${APP_NAME}.jar"
PID_FILE="\${APP_DIR}/web-sim.pid"

is_running() {
  pid=\$1
  [ -n "\${pid}" ] && kill -0 "\${pid}" 2>/dev/null
}

pid_matches_app() {
  pid=\$1
  command=\$(ps -p "\${pid}" -o command= 2>/dev/null || true)
  case "\${command}" in
    "") return 0 ;;
    *"\${JAR_FILE}"*) return 0 ;;
    *) return 1 ;;
  esac
}

stop_pid() {
  pid=\$1

  if ! is_running "\${pid}"; then
    return 0
  fi

  echo "Stopping web-sim, pid=\${pid}"
  kill "\${pid}" 2>/dev/null || true

  count=0
  while is_running "\${pid}"; do
    count=\$((count + 1))
    if [ "\${count}" -ge 30 ]; then
      echo "Process did not stop within 30 seconds, force killing pid=\${pid}"
      kill -9 "\${pid}" 2>/dev/null || true
      break
    fi
    sleep 1
  done
}

STOPPED=false

if [ -f "\${PID_FILE}" ]; then
  PID=\$(cat "\${PID_FILE}" 2>/dev/null || true)
  if is_running "\${PID}" && pid_matches_app "\${PID}"; then
    stop_pid "\${PID}"
    STOPPED=true
  elif is_running "\${PID}"; then
    echo "PID file points to a different process, not killing pid=\${PID}" >&2
  fi
  rm -f "\${PID_FILE}"
fi

PIDS=\$(ps -eo pid=,command= 2>/dev/null | awk -v jar="\${JAR_FILE}" 'index(\$0, jar) { print \$1 }')
for PID in \${PIDS}; do
  if is_running "\${PID}"; then
    stop_pid "\${PID}"
    STOPPED=true
  fi
done

rm -f "\${PID_FILE}"

if [ "\${STOPPED}" = "true" ]; then
  echo "web-sim stopped"
else
  echo "web-sim is not running"
fi
STOPEOF
chmod +x "$STAGING_DIR/stop.sh"

cat > "$STAGING_DIR/run.bat" <<RUNBATEOF
@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "APP_NAME=${APP_NAME}"
set "APP_DIR=%CD%"
set "JAR_FILE=%CD%\\%APP_NAME%.jar"
set "PID_FILE=%CD%\\web-sim.pid"
set "LOG_DIR=%CD%\\logs"
set "BOOTSTRAP_OUT_FILE=%LOG_DIR%\\web-sim.bootstrap.out"
set "BOOTSTRAP_ERR_FILE=%LOG_DIR%\\web-sim.bootstrap.err"
set "APP_ARGS=%*"
if "%WEB_SIM_START_WAIT_SECONDS%"=="" set "WEB_SIM_START_WAIT_SECONDS=5"

if not exist "%JAR_FILE%" (
  echo Jar file not found: "%JAR_FILE%"
  exit /b 1
)

if exist "%PID_FILE%" (
  set /p OLD_PID=<"%PID_FILE%"
  if not "!OLD_PID!"=="" (
    set "APP_PID=!OLD_PID!"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "\$pidValue = [int]\$env:APP_PID; \$jar = \$env:JAR_FILE; \$process = Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -eq \$pidValue }; if (\$process -and ((-not \$process.CommandLine) -or (\$process.CommandLine -like ('*' + \$jar + '*')))) { exit 0 } else { exit 1 }" >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
      echo web-sim is already running, pid=!OLD_PID!
      exit /b 0
    )
  )
  del /f /q "%PID_FILE%" >nul 2>nul
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%CD%\\config\\simulations" mkdir "%CD%\\config\\simulations"
if not exist "%CD%\\logs\\simulations" mkdir "%CD%\\logs\\simulations"

powershell -NoProfile -ExecutionPolicy Bypass -Command "\$jar = \$env:JAR_FILE; \$appArgs = \$env:APP_ARGS; \$quote = [char]34; \$argLine = '-jar ' + \$quote + \$jar + \$quote; if (\$appArgs) { \$argLine = \$argLine + ' ' + \$appArgs }; \$process = Start-Process -FilePath 'java' -ArgumentList \$argLine -WorkingDirectory \$env:APP_DIR -RedirectStandardOutput \$env:BOOTSTRAP_OUT_FILE -RedirectStandardError \$env:BOOTSTRAP_ERR_FILE -WindowStyle Hidden -PassThru; \$process.Id" > "%PID_FILE%"

if errorlevel 1 (
  del /f /q "%PID_FILE%" >nul 2>nul
  echo web-sim failed to start.
  exit /b 1
)

set /p APP_PID=<"%PID_FILE%"
timeout /t %WEB_SIM_START_WAIT_SECONDS% /nobreak >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-Process -Id %APP_PID% -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
if errorlevel 1 (
  del /f /q "%PID_FILE%" >nul 2>nul
  echo web-sim failed to start. Recent log output:
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path \$env:BOOTSTRAP_ERR_FILE) { Get-Content \$env:BOOTSTRAP_ERR_FILE -Tail 80 }; if (Test-Path \$env:BOOTSTRAP_OUT_FILE) { Get-Content \$env:BOOTSTRAP_OUT_FILE -Tail 80 }"
  exit /b 1
)

echo web-sim started, pid=%APP_PID%
echo Bootstrap log: %BOOTSTRAP_OUT_FILE%
echo Bootstrap error log: %BOOTSTRAP_ERR_FILE%
echo Admin URL: http://localhost:9998/admin
RUNBATEOF

cat > "$STAGING_DIR/stop.bat" <<STOPBATEOF
@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "APP_NAME=${APP_NAME}"
set "JAR_FILE=%CD%\\%APP_NAME%.jar"
set "PID_FILE=%CD%\\web-sim.pid"
set "STOPPED=false"

if exist "%PID_FILE%" (
  set /p APP_PID=<"%PID_FILE%"
  if not "!APP_PID!"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "\$pidValue = [int]\$env:APP_PID; \$jar = \$env:JAR_FILE; \$process = Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -eq \$pidValue }; if (\$process -and ((-not \$process.CommandLine) -or (\$process.CommandLine -like ('*' + \$jar + '*')))) { Stop-Process -Id \$pidValue -Force; exit 0 } else { exit 1 }" >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
      echo web-sim stopped, pid=!APP_PID!
      set "STOPPED=true"
    )
  )
  del /f /q "%PID_FILE%" >nul 2>nul
)

if "%STOPPED%"=="false" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "\$jar = '%JAR_FILE%'; \$processes = Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -like ('*' + \$jar + '*') }; if (\$processes) { \$processes | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }; exit 0 } else { exit 1 }" >nul 2>nul
  if !ERRORLEVEL! EQU 0 (
    echo web-sim stopped
    set "STOPPED=true"
  )
)

if "%STOPPED%"=="false" (
  echo web-sim is not running
)
STOPBATEOF

convert_to_crlf "$STAGING_DIR/run.bat"
convert_to_crlf "$STAGING_DIR/stop.bat"

rm -f "$DIST_TAR_PATH" "$TARGET_TAR_PATH" "$DIST_ZIP_PATH" "$TARGET_ZIP_PATH"
tar -C "$DIST_ROOT" -czf "$DIST_TAR_PATH" "$APP_NAME"
(cd "$DIST_ROOT" && zip -qr "$DIST_ZIP_PATH" "$APP_NAME")
cp "$DIST_TAR_PATH" "$TARGET_TAR_PATH"
cp "$DIST_ZIP_PATH" "$TARGET_ZIP_PATH"

echo "==> Archive created: $TARGET_TAR_PATH"
echo "==> Archive created: $TARGET_ZIP_PATH"
echo "==> Archive also copied to: $DIST_TAR_PATH"
echo "==> Archive also copied to: $DIST_ZIP_PATH"
echo "==> Included external config: ${APP_NAME}/config/application.yml"
echo "==> Included simulation config directory: ${APP_NAME}/config/simulations"
