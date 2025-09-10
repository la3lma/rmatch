# GitHub Actions Setup Requirements

This document describes the setup requirements for the rmatch GitHub Actions workflows to function properly.

## Repository Secrets

### Required Secrets

#### `REPORT_TOKEN` (Required for Performance Testing Workflows)

A Personal Access Token (PAT) with appropriate permissions for GitHub API operations that require elevated access beyond the default `GITHUB_TOKEN`.

**Why needed**: The default `GITHUB_TOKEN` has limited permissions in certain contexts, particularly for:
- Posting PR comments via GitHub API
- Setting status checks via GitHub API  
- Pushing to protected branches or PR branches from forks
- Uploading test reports that interact with the GitHub checks API

**Permissions required**:
- `repo` - Full control of private repositories
- `write:packages` - Upload packages to GitHub Package Registry
- `read:org` - Read org and team membership, read org projects
- `workflow` - Update GitHub Action workflows

**How to create**:
1. Go to GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens
2. Create a new token with the permissions listed above
3. Copy the token value

**How to configure**:
1. Go to repository Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Name: `REPORT_TOKEN`
4. Value: Paste the PAT token value
5. Click "Add secret"

### Optional Fallback Behavior

All workflows are configured to fallback to the default `GITHUB_TOKEN` if `REPORT_TOKEN` is not available:

```yaml
env:
  GITHUB_TOKEN: ${{ secrets.REPORT_TOKEN || secrets.GITHUB_TOKEN }}
```

This ensures backwards compatibility and allows the workflows to run with reduced functionality if the elevated token is not configured.

## Affected Workflows

The following workflows require `REPORT_TOKEN` for full functionality:

- `.github/workflows/performance-check.yml` - PR performance validation
- `.github/workflows/perf.yml` - Nightly performance testing  
- `.github/workflows/ci.yml` - Continuous integration with performance comparison
- `.github/workflows/update-performance-charts.yml` - Daily performance chart updates

## Troubleshooting

### "Resource not accessible by integration" Error

If you see this error in workflow logs:
```
##[error]HttpError: Resource not accessible by integration
```

This indicates that the `GITHUB_TOKEN` lacks sufficient permissions. Ensure `REPORT_TOKEN` is configured as described above.

### Workflows Run but Don't Post Comments or Set Status Checks

This typically means the workflows are falling back to `GITHUB_TOKEN` but the operations requiring elevated permissions are silently failing. Check that `REPORT_TOKEN` is properly configured.

### Permission Denied on Git Push

For workflows that commit and push changes (like performance chart updates), ensure `REPORT_TOKEN` has `repo` scope permissions.