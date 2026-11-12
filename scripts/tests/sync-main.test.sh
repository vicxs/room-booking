#!/usr/bin/env bash
# Exercises scripts/sync-main (the SessionStart hook) against a throwaway origin
# and clone. Usage: scripts/tests/sync-main.test.sh
set -euo pipefail

scripts=$(cd "$(dirname "$0")/.." && pwd)
sync="$scripts/sync-main"

# Ignore the developer's git config: hooks, signing, init.defaultBranch, etc.
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=Tester GIT_AUTHOR_EMAIL=tester@example.com
export GIT_COMMITTER_NAME=Tester GIT_COMMITTER_EMAIL=tester@example.com

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
failures=0

fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

# A fresh origin with one commit on main, and a clone of it at $clone whose main
# is then left one commit behind origin/main.
setup() { # name
  origin="$tmp/$1/origin.git" clone="$tmp/$1/clone" upstream="$tmp/$1/upstream"
  git init -q --bare --initial-branch=main "$origin"
  git clone -q "$origin" "$upstream" 2>/dev/null
  git -C "$upstream" commit -q --allow-empty -m "First"
  echo one >"$upstream/file.txt"
  echo notes >"$upstream/notes.txt" # never changed upstream
  git -C "$upstream" add file.txt notes.txt
  git -C "$upstream" commit -q -m "Add file"
  git -C "$upstream" push -q origin main
  git clone -q "$origin" "$clone"
  advance_origin "Second"
}

advance_origin() { # subject
  echo "$1" >>"$upstream/file.txt"
  git -C "$upstream" commit -q -am "$1"
  git -C "$upstream" push -q origin main
}

origin_main() { git -C "$upstream" rev-parse HEAD; }
rev() { git -C "$clone" rev-parse "$1"; }

# Runs the hook in $clone; asserts it exits 0 (it must never block a session).
run_sync() { # description
  local status=0
  output=$(cd "$clone" && "$sync" 2>&1) || status=$?
  [[ $status == 0 ]] || fail "$1: exit $status, expected 0. Output:"$'\n'"$output"
}

# --- Clean main behind origin/main is fast-forwarded.
setup clean
run_sync "clean main behind"
[[ $(rev main) == $(origin_main) ]] || fail "clean main behind: main was not fast-forwarded to origin/main"

# --- A modified tracked file blocks the fast-forward, even one the merge would
# not touch, and the change survives.
setup dirty
before=$(rev main)
echo "local edit" >>"$clone/notes.txt"
run_sync "dirty main"
[[ $(rev main) == "$before" ]] || fail "dirty main: main moved"
[[ $(rev origin/main) == $(origin_main) ]] || fail "dirty main: origin was not fetched"
grep -q "local edit" "$clone/notes.txt" || fail "dirty main: the local edit was lost"

# --- A staged change blocks it too.
setup staged
before=$(rev main)
echo new >"$clone/staged.txt"
git -C "$clone" add staged.txt
run_sync "staged change"
[[ $(rev main) == "$before" ]] || fail "staged change: main moved"

# --- On another branch, only origin is fetched: neither that branch nor main
# moves, even when a fast-forward would be possible.
setup branch
before=$(rev main)
git -C "$clone" switch -q -c feature
run_sync "other branch"
[[ $(rev feature) == "$before" ]] || fail "other branch: the current branch moved"
[[ $(rev main) == "$before" ]] || fail "other branch: main moved"
[[ $(rev origin/main) == $(origin_main) ]] || fail "other branch: origin was not fetched"

# --- Detached HEAD: nothing moves.
setup detached
before=$(rev main)
git -C "$clone" switch -q --detach main
run_sync "detached HEAD"
[[ $(rev HEAD) == "$before" ]] || fail "detached HEAD: HEAD moved"
[[ $(rev main) == "$before" ]] || fail "detached HEAD: main moved"

# --- Diverged main: left as is, and the session is told why.
setup diverged
echo "local work" >>"$clone/notes.txt"
git -C "$clone" commit -q -am "Local commit"
before=$(rev main)
run_sync "diverged main"
[[ $(rev main) == "$before" ]] || fail "diverged main: main moved"
[[ $output == *"diverged"* ]] || fail "diverged main: output does not say so. Output:"$'\n'"$output"

# --- An untracked file the fast-forward would overwrite survives, and main
# stays put.
setup untracked
before=$(rev main)
echo "mine" >"$clone/incoming.txt"
echo "theirs" >"$upstream/incoming.txt"
git -C "$upstream" add incoming.txt
git -C "$upstream" commit -q -m "Add incoming"
git -C "$upstream" push -q origin main
run_sync "untracked file in the way"
[[ $(rev main) == "$before" ]] || fail "untracked file in the way: main moved"
[[ $(cat "$clone/incoming.txt") == mine ]] || fail "untracked file in the way: the file was overwritten"

# --- Untracked files out of the way do not block the fast-forward.
setup untracked-harmless
echo "draft" >"$clone/draft.txt"
run_sync "harmless untracked file"
[[ $(rev main) == $(origin_main) ]] || fail "harmless untracked file: main was not fast-forwarded"

# --- Offline: nothing changes, not even a fast-forward to the stale
# origin/main, and the session is told the base may be stale.
setup offline
git -C "$clone" fetch -q origin
before=$(rev main)
git -C "$clone" remote set-url origin "$tmp/offline/unreachable.git"
run_sync "offline"
[[ $(rev main) == "$before" ]] || fail "offline: main moved"
[[ $output == *"could not fetch"* ]] || fail "offline: output does not say so. Output:"$'\n'"$output"

# --- Outside a git repository: no error, no blocking.
clone="$tmp/not-a-repo"
mkdir -p "$clone"
run_sync "outside a repository"

if ((failures > 0)); then
  echo "$failures check(s) failed" >&2
  exit 1
fi
echo "All sync-main checks passed."
