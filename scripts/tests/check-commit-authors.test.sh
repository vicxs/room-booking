#!/usr/bin/env bash
# Exercises scripts/check-commit-authors against a throwaway git repo.
# Usage: scripts/tests/check-commit-authors.test.sh
set -euo pipefail

scripts=$(cd "$(dirname "$0")/.." && pwd)
check="$scripts/check-commit-authors"
# shellcheck source=SCRIPTDIR/../bot-identity.sh
source "$scripts/bot-identity.sh"

repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
failures=0

fail() {
  echo "FAIL: $*"
  failures=$((failures + 1))
}

commit_as() { # name email subject -> prints the new commit's SHA
  GIT_AUTHOR_NAME="$1" GIT_AUTHOR_EMAIL="$2" GIT_COMMITTER_NAME="$1" GIT_COMMITTER_EMAIL="$2" \
    git -C "$repo" commit -q --allow-empty -m "$3"
  git -C "$repo" rev-parse HEAD
}

# Runs the check on base..head; sets $output and asserts the exit code and that
# the output contains every expected substring.
expect() { # description expected-exit base head [expected-substring...]
  local description=$1 expected=$2 base=$3 head=$4
  shift 4
  local status=0
  output=$(cd "$repo" && "$check" "$base" "$head" 2>&1) || status=$?
  [[ $status == "$expected" ]] || fail "$description: exit $status, expected $expected. Output:"$'\n'"$output"
  local needle
  for needle in "$@"; do
    [[ $output == *"$needle"* ]] || fail "$description: output lacks '$needle'. Output:"$'\n'"$output"
  done
}

git -C "$repo" init -q
base=$(commit_as "Someone" "someone@example.com" "Base commit, not checked")
bot1=$(commit_as "$BOT_NAME" "$BOT_EMAIL" "Bot work")
expect "only bot-authored commits pass" 0 "$base" "$bot1"

human=$(commit_as "Human" "human@example.com" "Human touch")
bot2=$(commit_as "$BOT_NAME" "$BOT_EMAIL" "More bot work")
expect "a human-authored commit fails, naming it and the fix" 1 "$base" "$bot2" \
  "$(git -C "$repo" rev-parse --short "$human")" "Human <human@example.com>" "Human touch" \
  "scripts/bot-git" "force-push"
[[ $output != *"Bot work"* ]] || fail "bot-authored commits listed as offending:"$'\n'"$output"

spoof=$(commit_as "$BOT_NAME" "human@example.com" "Bot name, human email")
expect "the bot name with another email fails" 1 "$bot2" "$spoof" "Bot name, human email"

git -C "$repo" checkout -q -b upstream "$spoof"
upstream=$(commit_as "Someone" "someone@example.com" "Upstream change on main")
git -C "$repo" checkout -q -b feature "$bot1"
GIT_AUTHOR_NAME="Human" GIT_AUTHOR_EMAIL="human@example.com" \
  GIT_COMMITTER_NAME="GitHub" GIT_COMMITTER_EMAIL="noreply@github.com" \
  git -C "$repo" merge -q --no-ff -m "Merge main into feature (Update branch)" "$upstream"
expect "a human-authored merge commit fails" 1 "$upstream" "$(git -C "$repo" rev-parse HEAD)" \
  "Merge main into feature (Update branch)"

expect "missing arguments is a usage error" 2 "" ""

if ((failures)); then
  echo "$failures failure(s)"
  exit 1
fi
echo "check-commit-authors: all tests passed"
