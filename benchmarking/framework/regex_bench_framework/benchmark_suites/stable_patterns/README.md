# Stable Git-Stored Regex Patterns

This directory contains pre-validated regex patterns for consistent benchmark reuse across all regex engines.

## Files

- `patterns_10000.txt` - 10,000 validated regex patterns (148KB)
- `patterns_10000_metadata.json` - Metadata file with configuration details

## Purpose

These patterns are stored in git to provide:
- **Consistency**: Same patterns used across all benchmark runs
- **Reliability**: All patterns have been validated to compile successfully with all engines
- **Speed**: No need to regenerate patterns each time
- **Reuse**: Patterns can be reused across different benchmark configurations

## Compatibility

These patterns are compatible with:
- rmatch
- re2j
- java-native-optimized
- java-native-unfair

## Configuration

- **Seed**: 12345 (matches phase1.json)
- **Suite**: log_mining
- **Pattern Count**: 10,000
- **Total Size**: ~138KB
- **Date Generated**: 2025-12-19

## Usage

The Makefile and benchmark framework will automatically use these git-stored patterns instead of generating new ones when both the pattern file and metadata file are present and match the current configuration.

## Validation Status

All patterns have been validated to:
- Compile successfully without syntax errors
- Work across all supported regex engines
- Provide consistent performance benchmark results