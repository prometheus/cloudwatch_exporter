#!/usr/bin/env bash
#
# Copyright (c) 2026-present Douglas Hoard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly MVNW="${PROJECT_DIR}/mvnw"

readonly LOG_FILE="stress-test.log"

usage() {
    cat <<'EOF'
Usage: ./scripts/stress-test.sh <number-of-iterations>

Run CloudWatch Exporter tests multiple times to check for flaky tests.

Arguments:
  <number-of-iterations>    Number of times to run the test suite.

Examples:
  ./scripts/stress-test.sh 5
  ./scripts/stress-test.sh 10
EOF
}

log() {
    echo "[INFO] $*" | tee -a "${LOG_FILE}"
}

fail() {
    echo "[ERROR] $*" | tee -a "${LOG_FILE}" >&2
    exit 1
}

main() {
    local iterations iteration start_time end_time elapsed_time total_elapsed

    if [[ $# -ne 1 ]]; then
        usage
        exit 1
    fi

    iterations="$1"

    [[ -x "${MVNW}" ]] || fail "mvnw not found or not executable: ${MVNW}"

    if [[ ! "${iterations}" =~ ^[1-9][0-9]*$ ]]; then
        fail "Invalid iterations '${iterations}'. Must be a positive integer."
    fi

    rm -f "${LOG_FILE}"

    log "Starting stress test with ${iterations} iteration(s)"
    log ""

    log "Building project (initial build)..."
    local build_start
    build_start="${SECONDS}"
    if ! (cd "${PROJECT_DIR}" && ./mvnw -B compile -DskipTests) |& tee -a "${LOG_FILE}"; then
        fail "Initial build failed. See ${LOG_FILE} for details."
    fi
    local build_elapsed
    build_elapsed=$((SECONDS - build_start))
    log "Initial build completed in ${build_elapsed}s"
    log ""

    log "Running ${iterations} test iteration(s)..."
    log ""

    for ((iteration = 1; iteration <= iterations; iteration++)); do
        start_time="${SECONDS}"
        log "Iteration ${iteration}/${iterations} started"

        if ! (cd "${PROJECT_DIR}" && ./mvnw -B test) |& tee -a "${LOG_FILE}"; then
            end_time="${SECONDS}"
            elapsed_time=$((end_time - start_time))
            fail "Iteration ${iteration}/${iterations} failed after ${elapsed_time}s. See ${LOG_FILE} for details."
        fi

        end_time="${SECONDS}"
        elapsed_time=$((end_time - start_time))
        log "Iteration ${iteration}/${iterations} passed (${elapsed_time}s)"
        log ""
    done

    total_elapsed="${SECONDS}"

    log "All ${iterations} iteration(s) completed successfully in ${total_elapsed}s"
}

main "$@"
