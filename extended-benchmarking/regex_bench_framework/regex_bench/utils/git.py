"""
Git metadata extraction for reproducibility.
"""

import subprocess
from pathlib import Path
from typing import Dict, Any, Optional


class GitMetadata:
    """Extract Git metadata for benchmark reproducibility."""

    def __init__(self, repo_path: Optional[Path] = None):
        self.repo_path = repo_path or Path.cwd()

    def get_metadata(self) -> Dict[str, Any]:
        """Get comprehensive Git metadata."""
        return {
            'repository': self._get_repository_info(),
            'commit': self._get_commit_info(),
            'branch': self._get_branch_info(),
            'status': self._get_status_info(),
            'remotes': self._get_remote_info()
        }

    def _get_repository_info(self) -> Dict[str, Any]:
        """Get repository-level information."""
        info = {}

        try:
            # Get repository root
            result = self._run_git_command(['rev-parse', '--show-toplevel'])
            if result:
                info['root'] = result.strip()

            # Check if we're in a git repository
            result = self._run_git_command(['rev-parse', '--git-dir'])
            if result:
                info['git_dir'] = result.strip()
                info['is_git_repo'] = True
            else:
                info['is_git_repo'] = False

        except Exception:
            info['is_git_repo'] = False

        return info

    def _get_commit_info(self) -> Dict[str, Any]:
        """Get current commit information."""
        commit_info = {}

        try:
            # Current commit hash
            result = self._run_git_command(['rev-parse', 'HEAD'])
            if result:
                commit_info['sha'] = result.strip()

            # Short commit hash
            result = self._run_git_command(['rev-parse', '--short', 'HEAD'])
            if result:
                commit_info['sha_short'] = result.strip()

            # Commit message
            result = self._run_git_command(['log', '-1', '--pretty=format:%s'])
            if result:
                commit_info['message'] = result.strip()

            # Author information
            result = self._run_git_command(['log', '-1', '--pretty=format:%an'])
            if result:
                commit_info['author_name'] = result.strip()

            result = self._run_git_command(['log', '-1', '--pretty=format:%ae'])
            if result:
                commit_info['author_email'] = result.strip()

            # Commit date
            result = self._run_git_command(['log', '-1', '--pretty=format:%ai'])
            if result:
                commit_info['date'] = result.strip()

        except Exception as e:
            commit_info['error'] = str(e)

        return commit_info

    def _get_branch_info(self) -> Dict[str, Any]:
        """Get branch information."""
        branch_info = {}

        try:
            # Current branch
            result = self._run_git_command(['branch', '--show-current'])
            if result:
                branch_info['current'] = result.strip()

            # All branches
            result = self._run_git_command(['branch', '-a'])
            if result:
                branches = [line.strip().lstrip('* ') for line in result.split('\n') if line.strip()]
                branch_info['all_branches'] = branches

        except Exception as e:
            branch_info['error'] = str(e)

        return branch_info

    def _get_status_info(self) -> Dict[str, Any]:
        """Get working directory status."""
        status_info = {}

        try:
            # Check if working directory is clean
            result = self._run_git_command(['status', '--porcelain'])
            if result is not None:
                status_info['is_clean'] = len(result.strip()) == 0
                status_info['modified_files'] = len([line for line in result.split('\n') if line.strip()])

            # Check for untracked files
            result = self._run_git_command(['ls-files', '--others', '--exclude-standard'])
            if result:
                untracked = [line.strip() for line in result.split('\n') if line.strip()]
                status_info['untracked_files'] = len(untracked)
            else:
                status_info['untracked_files'] = 0

            # Check if ahead/behind remote
            result = self._run_git_command(['status', '-b', '--porcelain'])
            if result:
                first_line = result.split('\n')[0]
                if 'ahead' in first_line:
                    status_info['ahead_of_remote'] = True
                if 'behind' in first_line:
                    status_info['behind_remote'] = True

        except Exception as e:
            status_info['error'] = str(e)

        return status_info

    def _get_remote_info(self) -> Dict[str, Any]:
        """Get remote repository information."""
        remote_info = {}

        try:
            # List remotes
            result = self._run_git_command(['remote', '-v'])
            if result:
                remotes = {}
                for line in result.split('\n'):
                    if line.strip():
                        parts = line.strip().split()
                        if len(parts) >= 2:
                            name = parts[0]
                            url = parts[1]
                            remotes[name] = url
                remote_info['remotes'] = remotes

            # Get upstream branch if exists
            result = self._run_git_command(['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}'])
            if result and result.strip():
                remote_info['upstream'] = result.strip()

        except Exception as e:
            remote_info['error'] = str(e)

        return remote_info

    def _run_git_command(self, args: list) -> Optional[str]:
        """Run a git command and return output."""
        try:
            result = subprocess.run(
                ['git'] + args,
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode == 0:
                return result.stdout
            else:
                return None

        except Exception:
            return None