#!/usr/bin/env bash
# Exercises scripts/factory-metrics against a fake `gh` that serves fixture JSON
# shaped like real responses from this repo, so it needs no network.
# Usage: scripts/tests/factory-metrics.test.sh
set -euo pipefail

scripts=$(cd "$(dirname "$0")/.." && pwd)
metrics="$scripts/factory-metrics"
# shellcheck source=SCRIPTDIR/../bot-identity.sh
source "$scripts/bot-identity.sh"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

# The fake gh maps each read the script makes to a fixture file in
# $FAKE_GH_FIXTURES; a read without a fixture fails the way gh does.
mkdir "$work/bin"
cat >"$work/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "$1 $2" in
  "pr list") file=merged-prs.json ;;
  "issue list") file=reversion-issues.json ;;
  "issue view") file=issue-$3.json ;;
  "api "*)
    if [[ $2 =~ ^repos/\{owner\}/\{repo\}/issues/([0-9]+)/timeline$ ]]; then
      file=timeline-${BASH_REMATCH[1]}.json
    elif [[ $2 =~ ^repos/\{owner\}/\{repo\}/pulls/([0-9]+)/comments$ ]]; then
      file=review-comments-${BASH_REMATCH[1]}.json
    else
      file=unknown
    fi
    ;;
  *) file=unknown ;;
esac
if [[ ! -f $FAKE_GH_FIXTURES/$file ]]; then
  echo "HTTP 404: Not Found (fake gh: no fixture for: gh $*)" >&2
  exit 1
fi
cat "$FAKE_GH_FIXTURES/$file"
EOF
chmod +x "$work/bin/gh"
export PATH="$work/bin:$PATH"

fixtures="$work/fixtures"
mkdir "$fixtures"
fixture() { cat >"$fixtures/$1"; } # name, JSON on stdin

bot='{"login": "vicxs-room-booking-bot[bot]", "type": "Bot"}'
human='{"login": "vicxs", "type": "User"}'

# Runs the script; sets $output and asserts the exit code and that the output
# contains every expected substring.
expect() { # description expected-exit "args" [expected-substring...]
  local description=$1 expected=$2 args=$3
  shift 3
  local status=0
  # shellcheck disable=SC2086 # word splitting turns the args string into arguments
  output=$(FAKE_GH_FIXTURES=$fixtures "$metrics" $args 2>&1) || status=$?
  [[ $status == "$expected" ]] || fail "$description: exit $status, expected $expected. Output:"$'\n'"$output"
  local needle
  for needle in "$@"; do
    [[ $output == *"$needle"* ]] || fail "$description: output lacks '$needle'. Output:"$'\n'"$output"
  done
}

# --- The fixture world ---------------------------------------------------------

# Intent #1: never aligned (like the real #1), closed by PR #2.
fixture issue-1.json <<'EOF'
{"createdAt": "2026-01-01T00:00:00Z", "number": 1, "parent": null, "title": "P0 Foundations"}
EOF
fixture timeline-1.json <<EOF
[
  {"event": "labeled", "actor": $bot, "created_at": "2026-01-01T00:00:01Z", "label": {"name": "factory"}},
  {"event": "closed", "actor": $human, "created_at": "2026-01-01T10:30:02Z"}
]
EOF
fixture timeline-2.json <<EOF
[
  {"event": "committed", "sha": "a1db037", "author": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-01-01T00:10:00Z"}, "committer": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-01-01T00:10:00Z"}},
  {"event": "reviewed", "id": 101, "user": $human, "state": "approved", "submitted_at": "2026-01-01T10:29:00Z"},
  {"event": "merged", "actor": $human, "created_at": "2026-01-01T10:30:00Z"}
]
EOF
fixture review-comments-2.json <<<'[]'

fixture merged-prs.json <<'EOF'
[
  {"number": 2, "title": "P0 Foundations: walking skeleton", "body": "Closes #1", "mergedAt": "2026-01-01T10:30:00Z", "url": "https://github.com/acme/widgets/pull/2",
   "closingIssuesReferences": [{"number": 1, "url": "https://github.com/acme/widgets/issues/1"}]}
]
EOF
fixture reversion-issues.json <<<'[]'

# --- Tests ---------------------------------------------------------------------

expect "an Intent without Alignment shows lead time and dashes" 0 "" \
  "| #1 | P0 Foundations | #2 | 10.5 | — | — | 0 |"

if ((failures)); then
  echo "$failures failure(s)"
  exit 1
fi
echo "factory-metrics: all tests passed"
