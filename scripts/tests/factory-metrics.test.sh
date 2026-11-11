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
    file=unknown
    for arg in "$@"; do
      if [[ $arg =~ ^repos/\{owner\}/\{repo\}/issues/([0-9]+)/timeline$ ]]; then
        file=timeline-${BASH_REMATCH[1]}.json
      fi
    done
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
# Any actor other than our bot is human for the metrics, other bots included.
other_bot='{"login": "copilot-pull-request-reviewer[bot]", "type": "Bot"}'

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

# Intent #3: aligned by the human (the bot applies `ready-for-agent` on their
# behalf, as in this repo), then re-aligned by the human; worked by PR #4.
fixture issue-3.json <<'EOF'
{"createdAt": "2026-02-01T00:00:00Z", "number": 3, "parent": null, "title": "Fail CI on non-bot commits"}
EOF
# Its 8 human touches, counted from the first Alignment: the comment at 02:00,
# the re-Alignment at 03:00 and the comment at 04:00 here; on PR #4 below, the
# human commit at 05:00, the review at 05:30, the reply at 06:00, the comment
# at 06:30 and another bot's comment at 06:40. Not touches: the comment at
# 00:30 (before Alignment), bot activity, a label change, the final approval
# and the comment at 09:00 (after the merge). The timeline comes in two pages,
# as `gh api --paginate` prints it.
fixture timeline-3.json <<EOF
[
  {"event": "commented", "actor": $human, "user": $human, "id": 300, "created_at": "2026-02-01T00:30:00Z"},
  {"event": "labeled", "actor": $bot, "created_at": "2026-02-01T01:00:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "commented", "actor": $human, "user": $human, "id": 301, "created_at": "2026-02-01T02:00:00Z"},
  {"event": "unlabeled", "actor": $human, "created_at": "2026-02-01T02:01:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "labeled", "actor": $human, "created_at": "2026-02-01T03:00:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "commented", "actor": $bot, "user": $bot, "id": 302, "created_at": "2026-02-01T03:30:00Z"}
]
[
  {"event": "labeled", "actor": $human, "created_at": "2026-02-01T03:40:00Z", "label": {"name": "enhancement"}},
  {"event": "commented", "actor": $human, "user": $human, "id": 303, "created_at": "2026-02-01T04:00:00Z"},
  {"event": "closed", "actor": $human, "created_at": "2026-02-01T08:00:02Z"},
  {"event": "commented", "actor": $human, "user": $human, "id": 304, "created_at": "2026-02-01T09:00:00Z"}
]
EOF
# The review at 05:30 carries two inline comments: they belong to the review
# and are not in the timeline. The reply at 06:00 in its thread is wrapped by
# GitHub in a review of its own. 07:50 is the final approval.
fixture timeline-4.json <<EOF
[
  {"event": "committed", "sha": "62c7b8a", "author": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-02-01T04:00:00Z"}, "committer": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-02-01T04:00:00Z"}},
  {"event": "committed", "sha": "0cba0b9", "author": {"name": "Victor", "email": "victor@example.com", "date": "2026-02-01T05:00:00Z"}, "committer": {"name": "Victor", "email": "victor@example.com", "date": "2026-02-01T05:00:00Z"}},
  {"event": "reviewed", "id": 402, "user": $human, "state": "commented", "submitted_at": "2026-02-01T05:30:00Z"},
  {"event": "reviewed", "id": 403, "user": $human, "state": "commented", "submitted_at": "2026-02-01T06:00:00Z"},
  {"event": "commented", "actor": $human, "user": $human, "id": 404, "created_at": "2026-02-01T06:30:00Z"},
  {"event": "commented", "actor": $other_bot, "user": $other_bot, "id": 407, "created_at": "2026-02-01T06:40:00Z"},
  {"event": "commented", "actor": $bot, "user": $bot, "id": 405, "created_at": "2026-02-01T06:45:00Z"},
  {"event": "committed", "sha": "5ffc294", "author": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-02-01T07:00:00Z"}, "committer": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-02-01T07:00:00Z"}},
  {"event": "reviewed", "id": 406, "user": $human, "state": "approved", "submitted_at": "2026-02-01T07:50:00Z"},
  {"event": "merged", "actor": $human, "created_at": "2026-02-01T08:00:00Z"}
]
EOF

# Intent #9: only bot activity and the final approval after Alignment, like
# the real #3, #5 and #6.
fixture issue-9.json <<'EOF'
{"createdAt": "2026-02-10T00:00:00Z", "number": 9, "parent": null, "title": "Quiet the generator"}
EOF
fixture timeline-9.json <<EOF
[
  {"event": "commented", "actor": $bot, "user": $bot, "id": 901, "created_at": "2026-02-10T00:59:00Z"},
  {"event": "labeled", "actor": $bot, "created_at": "2026-02-10T01:00:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "closed", "actor": $human, "created_at": "2026-02-10T02:00:02Z"}
]
EOF
fixture timeline-10.json <<EOF
[
  {"event": "committed", "sha": "22d8e7a", "author": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-02-10T01:10:00Z"}, "committer": {"name": "$BOT_NAME", "email": "$BOT_EMAIL", "date": "2026-02-10T01:10:00Z"}},
  {"event": "commented", "actor": $bot, "user": $bot, "id": 1001, "created_at": "2026-02-10T01:20:00Z"},
  {"event": "labeled", "actor": $bot, "created_at": "2026-02-10T01:21:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "reviewed", "id": 1002, "user": $human, "state": "approved", "submitted_at": "2026-02-10T01:59:00Z"},
  {"event": "merged", "actor": $human, "created_at": "2026-02-10T02:00:00Z"}
]
EOF

# Intent #5: closed by two merged PRs, #6 and #7. #7 is later reverted with
# GitHub's revert button (PR #8), which also closes the reversion issue #11.
# The human re-aligns it at 06:00, between the two merges: its 3 human touches
# are PR #6's review at 01:00, the re-Alignment and PR #7's comment.
fixture issue-5.json <<'EOF'
{"createdAt": "2026-03-01T00:00:00Z", "number": 5, "parent": null, "title": "Align the factory doc"}
EOF
fixture timeline-5.json <<EOF
[
  {"event": "labeled", "actor": $bot, "created_at": "2026-03-01T00:30:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "labeled", "actor": $human, "created_at": "2026-03-01T06:00:00Z", "label": {"name": "ready-for-agent"}},
  {"event": "closed", "actor": $human, "created_at": "2026-03-02T00:00:02Z"}
]
EOF
fixture timeline-6.json <<EOF
[
  {"event": "reviewed", "id": 601, "user": $human, "state": "changes_requested", "submitted_at": "2026-03-01T01:00:00Z"},
  {"event": "reviewed", "id": 602, "user": $human, "state": "approved", "submitted_at": "2026-03-01T01:50:00Z"},
  {"event": "merged", "actor": $human, "created_at": "2026-03-01T02:00:00Z"}
]
EOF
fixture timeline-7.json <<EOF
[
  {"event": "commented", "actor": $human, "user": $human, "id": 701, "created_at": "2026-03-01T12:00:00Z"},
  {"event": "reviewed", "id": 702, "user": $human, "state": "approved", "submitted_at": "2026-03-01T23:50:00Z"},
  {"event": "merged", "actor": $human, "created_at": "2026-03-02T00:00:00Z"}
]
EOF

# Work item #13 of the Spec #12: a sub-issue, closed by PR #14.
fixture issue-13.json <<'EOF'
{"createdAt": "2026-04-01T00:00:00Z", "number": 13, "parent": {"number": 12, "title": "Administrators manage Rooms", "url": "https://github.com/acme/widgets/issues/12"}, "title": "Create a Room"}
EOF

fixture merged-prs.json <<'EOF'
[
  {"number": 14, "title": "Create a Room", "body": "Closes #13", "mergedAt": "2026-04-01T05:00:00Z", "url": "https://github.com/acme/widgets/pull/14",
   "closingIssuesReferences": [{"number": 13, "url": "https://github.com/acme/widgets/issues/13"}]},
  {"number": 8, "title": "Revert \"Align the factory doc, part 2\"", "body": "Reverts acme/widgets#7\r\n\r\nCloses #11", "mergedAt": "2026-03-03T00:00:00Z", "url": "https://github.com/acme/widgets/pull/8",
   "closingIssuesReferences": [{"number": 11, "url": "https://github.com/acme/widgets/issues/11"}]},
  {"number": 7, "title": "Align the factory doc, part 2", "body": "Closes #5", "mergedAt": "2026-03-02T00:00:00Z", "url": "https://github.com/acme/widgets/pull/7",
   "closingIssuesReferences": [{"number": 5, "url": "https://github.com/acme/widgets/issues/5"}]},
  {"number": 6, "title": "Align the factory doc, part 1", "body": "Closes #5", "mergedAt": "2026-03-01T02:00:00Z", "url": "https://github.com/acme/widgets/pull/6",
   "closingIssuesReferences": [{"number": 5, "url": "https://github.com/acme/widgets/issues/5"}]},
  {"number": 10, "title": "Quiet the OpenAPI generator output", "body": "Closes #9", "mergedAt": "2026-02-10T02:00:00Z", "url": "https://github.com/acme/widgets/pull/10",
   "closingIssuesReferences": [{"number": 9, "url": "https://github.com/acme/widgets/issues/9"}, {"number": 77, "url": "https://github.com/other/repo/issues/77"}]},
  {"number": 4, "title": "Fail CI when a PR contains commits not authored by the bot", "body": "Closes #3", "mergedAt": "2026-02-01T08:00:00Z", "url": "https://github.com/acme/widgets/pull/4",
   "closingIssuesReferences": [{"number": 3, "url": "https://github.com/acme/widgets/issues/3"}]},
  {"number": 2, "title": "P0 Foundations: walking skeleton", "body": "Closes #1", "mergedAt": "2026-01-01T10:30:00Z", "url": "https://github.com/acme/widgets/pull/2",
   "closingIssuesReferences": [{"number": 1, "url": "https://github.com/acme/widgets/issues/1"}]}
]
EOF
# #11 and the Revert PR #8 both point at PR #7: one Reversion. #17 has no
# Revert PR: PR #4 was fixed forward.
fixture reversion-issues.json <<'EOF'
[
  {"number": 17, "body": "The check added in PR #4 fails on merge commits.\n\nCaused by #4"},
  {"number": 11, "body": "Part 2 contradicts part 1.\n\nCaused by #7"}
]
EOF

# --- Tests ---------------------------------------------------------------------

expect "an Intent without Alignment shows lead time and dashes" 0 "" \
  "| #1 | P0 Foundations | #2 | 10.5 | — | — | 0 |"
expect "unattended time starts at the last ready-for-agent event" 0 "" \
  "| #3 | Fail CI on non-bot commits | #4 | 8.0 | 5.0 |"
expect "human touches after Alignment: re-alignment, comments, a commit, reviews" 0 "" \
  "| #3 | Fail CI on non-bot commits | #4 | 8.0 | 5.0 | 8 |"
expect "another bot's comment is a human touch: only our bot is not human" 0 "" \
  "| #3 | Fail CI on non-bot commits | #4 | 8.0 | 5.0 | 8 |"
expect "an aligned Intent with only the final approval has no human touches" 0 "" \
  "| #9 | Quiet the generator | #10 | 2.0 | 1.0 | 0 | 0 |"
expect "an Intent closed by two merged PRs ends at the last merge and sums touches" 0 "" \
  "| #5 | Align the factory doc | #6, #7 | 24.0 | 18.0 | 3 |"
expect "an Intent re-aligned between two merged PRs keeps the first PR's touches" 0 "" \
  "| #5 | Align the factory doc | #6, #7 | 24.0 | 18.0 | 3 |"
expect "a Revert PR counts a Reversion on the reverted Intent" 0 "" \
  "| #5 | Align the factory doc | #6, #7 | 24.0 | 18.0 | 3 | 1 |"
expect "a reversion issue counts a Reversion on the Intent of the PR that caused it" 0 "" \
  "| #3 | Fail CI on non-bot commits | #4 | 8.0 | 5.0 | 8 | 1 |"
expect "a Revert PR and a reversion issue of the same PR count one Reversion" 0 "" \
  "| #5 | Align the factory doc | #6, #7 | 24.0 | 18.0 | 3 | 1 |"
[[ $output != *"#11"* && $output != *"#8"* ]] ||
  fail "a Revert PR, or the issue only it closes, is listed as an Intent. Output:"$'\n'"$output"
expect "a sub-issue is not listed" 0 ""
[[ $output != *"#13"* && $output != *"#14"* ]] || fail "a sub-issue is listed as an Intent. Output:"$'\n'"$output"

expected_json='[
  {"issue": 1, "title": "P0 Foundations", "prs": [2], "leadTimeHours": 10.5, "unattendedTimeHours": null, "humanTouches": null, "reversions": 0},
  {"issue": 3, "title": "Fail CI on non-bot commits", "prs": [4], "leadTimeHours": 8, "unattendedTimeHours": 5, "humanTouches": 8, "reversions": 1},
  {"issue": 5, "title": "Align the factory doc", "prs": [6, 7], "leadTimeHours": 24, "unattendedTimeHours": 18, "humanTouches": 3, "reversions": 1},
  {"issue": 9, "title": "Quiet the generator", "prs": [10], "leadTimeHours": 2, "unattendedTimeHours": 1, "humanTouches": 0, "reversions": 0}
]'
expect "--json prints the same data as JSON" 0 "--json"
jq -e --argjson expected "$expected_json" '. == $expected' <<<"$output" >/dev/null ||
  fail "--json: unexpected output:"$'\n'"$output"

# #5's last merge is 2026-03-02T00:00:00Z; its first one, 2026-03-01.
expect "--since keeps Intents whose last merge is on or after the date" 0 "--since 2026-03-02 --json"
[[ $(jq -c 'map(.issue)' <<<"$output") == "[5]" ]] || fail "--since 2026-03-02: unexpected output:"$'\n'"$output"
expect "--since works with the table too" 0 "--since 2026-02-10" "| #5 |" "| #9 |"
[[ $output != *"| #3 |"* ]] || fail "--since 2026-02-10 lists #3. Output:"$'\n'"$output"
expect "--since after every merge lists nothing" 0 "--since 2026-03-03 --json"
[[ $output == "[]" ]] || fail "--since 2026-03-03: unexpected output:"$'\n'"$output"

status=0
output=$(FAKE_GH_FIXTURES=$work/no-fixtures "$metrics" 2>&1) || status=$?
[[ $status == 1 && $output == *"Error: 'gh pr list"*"failed"* && $output == *"HTTP 404"* ]] ||
  fail "a gh failure: exit $status, expected 1 with a clear error. Output:"$'\n'"$output"

for args in "--bogus" "--since" "--since 2026-3-1" "--since 2026-02-30" "--since yesterday" "extra"; do
  expect "'$args' is a usage error" 2 "$args" "Usage:"
done

if ((failures)); then
  echo "$failures failure(s)"
  exit 1
fi
echo "factory-metrics: all tests passed"
