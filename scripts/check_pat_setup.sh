#!/bin/bash
set -euo pipefail

# Script to verify PAT (Personal Access Token) setup for GitHub Actions
# This helps diagnose token configuration issues

echo "🔍 GitHub Actions Token Configuration Check"
echo "==========================================="

# Check if we're in a GitHub Actions environment
if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    echo "✓ Running in GitHub Actions environment"
    
    # Check if REPORT_TOKEN is available
    if [[ -n "${REPORT_TOKEN:-}" ]]; then
        echo "✓ REPORT_TOKEN is configured"
        echo "  Token starts with: ${REPORT_TOKEN:0:4}..."
    else
        echo "⚠ REPORT_TOKEN not found, falling back to GITHUB_TOKEN"
    fi
    
    # Check if GITHUB_TOKEN is available
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        echo "✓ GITHUB_TOKEN is available"
        echo "  Token starts with: ${GITHUB_TOKEN:0:4}..."
    else
        echo "❌ Neither REPORT_TOKEN nor GITHUB_TOKEN are available"
        exit 1
    fi
    
    # Test GitHub API access (basic)
    if command -v gh >/dev/null 2>&1; then
        echo "✓ GitHub CLI (gh) is available"
        if gh api user --silent 2>/dev/null; then
            echo "✓ GitHub API access test passed"
        else
            echo "⚠ GitHub API access test failed - check token permissions"
        fi
    else
        echo "ℹ GitHub CLI (gh) not available for API testing"
    fi
    
else
    echo "ℹ Not running in GitHub Actions environment"
    echo "  This script is designed to run in CI workflows"
fi

echo ""
echo "📝 Setup Instructions:"
echo "  If you see permission errors, follow the PAT setup guide:"
echo "  .github/SETUP_PAT.md"
echo ""
echo "✅ Token configuration check complete"