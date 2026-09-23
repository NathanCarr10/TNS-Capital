#!/bin/bash

################################################################################
# Snowflake Database Reset Script
# 
# Purpose: Drop and recreate the TNS_ANALYTICS database with all dimension
#          and fact tables for the trading analytics system.
#
# Usage:
#   ./scripts/reset_snowflake_db.sh [--dry-run]
#
# Options:
#   --dry-run       Preview SQL commands without executing
#
# Requirements:
#   - Python 3.8+ with snowflake-connector-python installed
#   - .env file with Snowflake credentials (in python/ directory)
#
################################################################################

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DRY_RUN=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PYTHON_DIR="$PROJECT_ROOT/python"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--dry-run]"
            exit 1
            ;;
    esac
done

################################################################################
# Helper Functions
################################################################################

log_info() {
    echo "[INFO] $*"
}

log_success() {
    echo "[SUCCESS] $*"
}

log_warning() {
    echo "[WARNING] $*"
}

log_error() {
    echo "[ERROR] $*" >&2
}

# Check if Python is installed
check_python() {
    if ! command -v python3 &> /dev/null; then
        log_error "Python 3 is not installed or not in PATH"
        exit 1
    fi
    log_success "Python found: $(command -v python3)"
}

# Check if .env file exists
check_env_file() {
    if [[ ! -f "$PYTHON_DIR/.env" ]]; then
        log_error ".env file not found at $PYTHON_DIR/.env"
        exit 1
    fi
    log_success ".env file found"
}

# Check if reset_database module exists
check_reset_module() {
    if [[ ! -f "$PYTHON_DIR/etl/reset_database.py" ]]; then
        log_error "reset_database.py module not found at $PYTHON_DIR/etl/reset_database.py"
        exit 1
    fi
    log_success "reset_database module found"
}

################################################################################
# Execute Reset via Python
################################################################################

execute_reset() {
    local python_args=""
    
    if [[ "$DRY_RUN" == true ]]; then
        python_args="--dry-run"
    fi
    
    cd "$PYTHON_DIR" || exit 1
    python3 -m etl.reset_database $python_args
    local exit_code=$?
    cd - > /dev/null
    
    return $exit_code
}

################################################################################
# Main Execution
################################################################################

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════╗"
    echo "║                 Snowflake Database Reset Script                        ║"
    echo "╚════════════════════════════════════════════════════════════════════════╝"
    echo ""
    
    if [[ "$DRY_RUN" == true ]]; then
        log_warning "Running in DRY-RUN mode (no changes will be made)"
        echo ""
    fi
    
    # Validation phase
    log_info "=== Phase 1: Validation ==="
    check_python
    check_env_file
    check_reset_module
    echo ""
    
    # Execute reset via Python
    log_info "=== Phase 2: Execute Reset ==="
    execute_reset
    local exit_code=$?
    
    if [[ $exit_code -ne 0 ]]; then
        log_error "Reset failed with exit code $exit_code"
        exit $exit_code
    fi
    
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════╗"
    if [[ "$DRY_RUN" == true ]]; then
        log_warning "Dry-run completed. Review the SQL above and re-run without --dry-run to execute."
    else
        log_success "Database reset completed successfully!"
    fi
    echo "╚════════════════════════════════════════════════════════════════════════╝"
    echo ""
}

# Run main function
main "$@"
