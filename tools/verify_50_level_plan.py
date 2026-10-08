"""Static contract check for the 50-level local work-order plan."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ISSUES = ROOT / ".scratch" / "50-level-expansion" / "issues"
GAME_DESIGN = ROOT / "GAME_DESIGN.md"
ISSUE_INDEX = ROOT / ".scratch" / "50-level-expansion" / "ISSUE_INDEX.md"
REQUIRED_TERMS = ("Blocked by", "玩法", "剧情", "资源", "验收")
BOSS_LEVELS = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50}


def main() -> int:
    files = sorted(ISSUES.glob("level-*.md"))
    ids: dict[int, Path] = {}
    ticket_titles: dict[int, str] = {}
    failures: list[str] = []
    for path in files:
        match = re.match(r"level-(\d{2})-", path.name)
        if not match:
            continue
        level = int(match.group(1))
        if level in ids:
            failures.append(f"duplicate level id {level:02d}: {ids[level].name}, {path.name}")
        ids[level] = path
        text = path.read_text(encoding="utf-8")
        heading = re.search(r"^# Level\s+\d{2}[：:]\s*(.+)$", text, re.MULTILINE)
        if not heading:
            failures.append(f"level {level:02d} missing Level heading: {path.name}")
        else:
            ticket_titles[level] = heading.group(1).strip()
        for term in REQUIRED_TERMS:
            if term not in text:
                failures.append(f"level {level:02d} missing {term}: {path.name}")
        if level in BOSS_LEVELS and not re.search(r"boss|Boss|BOSS", text):
            failures.append(f"level {level:02d} is a Boss slot but has no Boss declaration")

    expected = set(range(1, 51))
    missing = sorted(expected - set(ids))
    extras = sorted(set(ids) - expected)
    if missing:
        failures.append("missing levels: " + ", ".join(f"{level:02d}" for level in missing))
    if extras:
        failures.append("unexpected levels: " + ", ".join(f"{level:02d}" for level in extras))

    # Keep the product matrix and the independently actionable tickets from drifting.
    matrix_text = GAME_DESIGN.read_text(encoding="utf-8") if GAME_DESIGN.exists() else ""
    matrix: dict[int, str] = {}
    boss_rows: set[int] = set()
    for match in re.finditer(r"^\| L(\d{2}) \|\s*([^|]+?)\s*\|([^|]+)\|([^|]+)\|([^|]+)\|$", matrix_text, re.MULTILINE):
        level = int(match.group(1))
        matrix[level] = match.group(2).strip()
        if "✓" in match.group(5) or "Boss" in match.group(5):
            boss_rows.add(level)
    if set(matrix) != expected:
        failures.append("GAME_DESIGN matrix must contain exactly L01..L50")
    for level in sorted(expected & set(matrix) & set(ticket_titles)):
        ticket_title = ticket_titles[level]
        matrix_title = matrix[level]
        if not (ticket_title.startswith(matrix_title) or matrix_title.startswith(ticket_title)):
            failures.append(
                f"GAME_DESIGN title drift at L{level:02d}: ticket={ticket_title!r}, matrix={matrix_title!r}"
            )
    if boss_rows != BOSS_LEVELS:
        failures.append(
            "GAME_DESIGN Boss rows must be "
            + ", ".join(f"L{level:02d}" for level in sorted(BOSS_LEVELS))
        )

    # Verify the index points to real level files so a renamed ticket cannot silently disappear.
    index_text = ISSUE_INDEX.read_text(encoding="utf-8") if ISSUE_INDEX.exists() else ""
    index_links = set(re.findall(r"\]\((issues/level-\d{2}-[^)]+\.md)\)", index_text))
    expected_links = {f"issues/{path.name}" for path in ids.values()}
    if index_links != expected_links:
        failures.append("ISSUE_INDEX level links do not match the 50 level ticket files")

    if failures:
        for failure in failures:
            print("FAIL", failure)
        return 1

    print(f"PASS 50 level work orders ({len(ids)} files)")
    print("PASS Boss slots at " + ", ".join(f"{level:02d}" for level in sorted(BOSS_LEVELS)))
    print("PASS GAME_DESIGN matrix and ISSUE_INDEX alignment")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
