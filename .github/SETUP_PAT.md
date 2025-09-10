# Setting up Personal Access Token (PAT) for GitHub Actions

This repository uses GitHub Actions workflows that require elevated permissions beyond the default `GITHUB_TOKEN`. To enable these workflows to function properly, you need to set up a Personal Access Token (PAT) with the appropriate permissions.

## Required Permissions

The PAT needs the following scopes/permissions:

- **Contents: Write** - To push commits and create/update files
- **Pull requests: Write** - To comment on pull requests and create status checks
- **Issues: Write** - To comment on issues (for greetings workflow)
- **Checks: Write** - To create status checks for performance validation
- **Actions: Read** - To read workflow run status
- **Metadata: Read** - Standard repository metadata access

## How to Create the PAT

1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token" → "Generate new token (classic)"
3. Give it a descriptive name like "rmatch-ci-actions"
4. Select the following scopes:
   - `repo` (Full control of private repositories)
   - `workflow` (Update GitHub Action workflows)
   - `write:packages` (if you need package publishing)

## Setting up the Repository Secret

1. Go to your repository → Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Name: `REPORT_TOKEN`
4. Value: Paste your generated PAT
5. Click "Add secret"

## Workflows that use this token

The following workflows will use the `REPORT_TOKEN` when available, falling back to the default `GITHUB_TOKEN`:

- `ci.yml` - For posting performance comparison comments
- `performance-check.yml` - For committing performance data and posting status checks
- `update-performance-charts.yml` - For pushing updated performance charts
- `perf.yml` - For posting performance comparison comments
- `greetings.yml` - For commenting on first issues/PRs

## Security Notes

- Store the PAT as a repository secret, never commit it to code
- Use the minimum required permissions
- Consider setting an expiration date on the PAT
- Regularly rotate the PAT for security best practices

## Troubleshooting

If you see errors like "Resource not accessible by integration":

1. Verify the `REPORT_TOKEN` secret is set correctly
2. Check that the PAT has all required permissions
3. Ensure the PAT hasn't expired
4. Make sure you're using a personal PAT, not a GitHub App token

The workflows are designed to fallback to `GITHUB_TOKEN` when `REPORT_TOKEN` is not available, but some operations may fail with reduced permissions.