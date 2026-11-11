#!/usr/bin/env bash
# Exercises scripts/check-commit-authors against a throwaway git repo.
# Usage: scripts/tests/check-commit-authors.test.sh
set -euo pipefail

scripts=$(cd "$(dirname "$0")/.." && pwd)
check="$scripts/check-commit-authors"
# shellcheck source=SCRIPTDIR/../bot-identity.sh
source "$scripts/bot-identity.sh"

# Ignore the developer's git config: hooks, signing, init.defaultBranch, etc.
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
failures=0

fail() {
  echo "FAIL: $*" >&2
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

git -C "$repo" init -q --initial-branch=main
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

tabbed=$(commit_as "Human" "human@example.com" $'Before tab\tafter tab')
expect "a subject containing a tab is printed whole" 1 "$spoof" "$tabbed" $'Before tab\tafter tab'

git -C "$repo" checkout -q -b upstream "$spoof"
upstream=$(commit_as "Someone" "someone@example.com" "Upstream change on main")
git -C "$repo" checkout -q -b feature "$bot1"
GIT_AUTHOR_NAME="Human" GIT_AUTHOR_EMAIL="human@example.com" \
  GIT_COMMITTER_NAME="GitHub" GIT_COMMITTER_EMAIL="noreply@github.com" \
  git -C "$repo" merge -q --no-ff -m "Merge main into feature (Update branch)" "$upstream"
expect "a human-authored merge commit fails" 1 "$upstream" "$(git -C "$repo" rev-parse HEAD)" \
  "Merge main into feature (Update branch)"

# The suggested fix must work: run its rebase as scripts/bot-git would (it
# exports the bot identity, then execs git), without minting a token.
git -C "$repo" update-ref refs/remotes/origin/main "$upstream"
commit_as "Human" "human@example.com" "Human fix on feature" >/dev/null
expect "the fix rebases onto the PR's base branch" 1 "$upstream" HEAD "PR's base branch" "rebase origin/main"
rebase=$(grep -m1 'scripts/bot-git rebase ' <<<"$output" | sed 's/^ *scripts\/bot-git /git /')
[[ -n $rebase ]] || fail "no rebase command in the fix. Output:"$'\n'"$output"
(
  cd "$repo"
  export GIT_AUTHOR_NAME="$BOT_NAME" GIT_AUTHOR_EMAIL="$BOT_EMAIL"
  export GIT_COMMITTER_NAME="$BOT_NAME" GIT_COMMITTER_EMAIL="$BOT_EMAIL"
  eval "$rebase" >/dev/null 2>&1
) || fail "the suggested rebase failed: $rebase"
expect "the suggested rebase re-authors the commits as the bot" 0 origin/main HEAD
[[ $(git -C "$repo" log -1 --format=%s) == "Human fix on feature" ]] ||
  fail "the suggested rebase lost the commit's content"

missing=0123456789abcdef0123456789abcdef01234567
expect "an unknown base fails clearly" 2 "$missing" "$bot1" "cannot resolve base '$missing'" "fetch-depth"
expect "an unknown head fails clearly" 2 "$base" "$missing" "cannot resolve head '$missing'" "fetch-depth"

expect "empty arguments are a usage error" 2 "" "" "Usage:"
for args in "" "$base"; do
  status=0
  # shellcheck disable=SC2086 # word splitting turns "" into zero arguments
  output=$(cd "$repo" && "$check" $args 2>&1) || status=$?
  [[ $status == 2 && $output == *"Usage:"* ]] ||
    fail "missing arguments ('$args'): exit $status, expected 2. Output:"$'\n'"$output"
done

if ((failures)); then
  echo "$failures failure(s)"
  exit 1
fi
echo "check-commit-authors: all tests passed"
