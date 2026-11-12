#!/usr/bin/env bash
# Exercises scripts/staged-changes-affect-frontend against a throwaway git repo.
# Usage: scripts/tests/staged-changes-affect-frontend.test.sh
set -euo pipefail

check="$(cd "$(dirname "$0")/.." && pwd)/staged-changes-affect-frontend"

# Ignore the developer's git config: hooks, signing, init.defaultBranch, etc.
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=Test GIT_AUTHOR_EMAIL=test@example.com
export GIT_COMMITTER_NAME=Test GIT_COMMITTER_EMAIL=test@example.com

repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
failures=0

fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

# Starts every case from the same committed tree with an empty index diff.
reset_repo() {
  rm -rf "$repo"
  mkdir -p "$repo"
  git -C "$repo" init -q --initial-branch=main
  mkdir -p "$repo/docs/frontend" "$repo/backend" "$repo/frontend/src" "$repo/.husky"
  local path
  for path in README.md docs/factory.md docs/frontend/notes.md backend/pom.xml \
    frontend/package.json frontend/src/App.tsx openapi.yaml package.json pnpm-lock.yaml \
    pnpm-workspace.yaml .tool-versions .husky/pre-commit; do
    echo original >"$repo/$path"
  done
  git -C "$repo" add -A
  git -C "$repo" commit -q -m "Base"
}

# Asserts the check's exit code (0: affects the frontend, 1: does not) when run
# from the given directory inside the repo, and that it prints nothing.
expect() { # description expected-exit [subdirectory]
  local description=$1 expected=$2 dir="$repo/${3:-}"
  local status=0 output
  output=$(cd "$dir" && "$check" 2>&1) || status=$?
  [[ $status == "$expected" ]] || fail "$description: exit $status, expected $expected. Output:"$'\n'"$output"
  [[ -z $output ]] || fail "$description: printed output:"$'\n'"$output"
}

modify() { # path...
  local path
  for path in "$@"; do
    echo changed >>"$repo/$path"
    git -C "$repo" add -- "$path"
  done
}

reset_repo
modify docs/factory.md
expect "a docs-only change does not affect the frontend" 1

for path in frontend/src/App.tsx openapi.yaml package.json pnpm-lock.yaml pnpm-workspace.yaml \
  .tool-versions .husky/pre-commit; do
  reset_repo
  modify "$path"
  expect "a change to $path affects the frontend" 0
done

reset_repo
modify README.md docs/factory.md backend/pom.xml
expect "docs, README and backend changes do not affect the frontend" 1

reset_repo
expect "nothing staged does not affect the frontend" 1

reset_repo
modify docs/frontend/notes.md
expect "a docs page about the frontend does not affect it" 1

reset_repo
echo new >"$repo/frontend-notes.md"
echo new >"$repo/backend/package.json"
mkdir -p "$repo/backend/frontend"
echo new >"$repo/backend/frontend/x.ts"
git -C "$repo" add -A
expect "only top-level paths count, not lookalikes or nested names" 1

reset_repo
modify docs/factory.md frontend/src/App.tsx
expect "a mixed change affects the frontend" 0

reset_repo
modify frontend/src/App.tsx
expect "it works from a subdirectory" 0 docs
expect "it works from inside frontend" 0 frontend/src

reset_repo
git -C "$repo" rm -q frontend/src/App.tsx
expect "a deleted frontend file affects the frontend" 0

reset_repo
git -C "$repo" mv frontend/src/App.tsx docs/App.tsx
expect "a file renamed out of frontend affects the frontend" 0

reset_repo
git -C "$repo" mv docs/factory.md frontend/src/factory.md
expect "a file renamed into frontend affects the frontend" 0

reset_repo
echo new >"$repo/frontend/src/My Component.tsx"
git -C "$repo" add -A
expect "a new frontend file whose name has a space affects the frontend" 0

reset_repo
echo new >"$repo/docs/my notes.md"
echo new >"$repo/docs/"$'tab\there.md'
git -C "$repo" add -A
expect "new docs files with spaces and tabs in their names do not affect the frontend" 1

status=0
output=$(cd "$(mktemp -d)" && "$check" 2>&1) || status=$?
((status > 1)) || fail "outside a git repository: exit $status, expected >1 so the hook fails. Output:"$'\n'"$output"

if ((failures)); then
  echo "$failures failure(s)"
  exit 1
fi
echo "staged-changes-affect-frontend: all tests passed"
