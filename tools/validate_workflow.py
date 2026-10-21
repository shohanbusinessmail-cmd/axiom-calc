#!/usr/bin/env python3
"""Validate .github/workflows/*.yml offline.

GitHub Actions cannot be dry-run locally, and two workflow defects already cost
real CI runs on this project:

  1. `./gradlew ... | tee log` reported step success while Gradle had failed,
     because bash reports the exit status of the LAST command in a pipeline.
     Every piped verification step now sets `set -o pipefail`.
  2. A `python3 - <<'TAG'` heredoc inside a `run: |` block scalar. YAML strips
     the block's common indentation, so the terminator only reaches bash at
     column 0 if it is written at exactly the block's base indent in the file,
     and the python body only parses if it is also at that base indent. Getting
     this wrong either breaks YAML parsing or hangs the step at EOF.

This tool checks, for every workflow:
  * the file parses as YAML;
  * every `run` script passes `bash -n` (which reports an unterminated
    here-document as "here-document delimited by end-of-file");
  * every python heredoc inside a `run` script compiles;
  * every piped step that is meant to be verified sets `pipefail`;
  * no command that can legitimately exit non-zero stands alone. GitHub runs
    every bash step as `bash -e -o pipefail`, so a `grep` that matches nothing,
    or a `[ -f x ] && cat x` test that is false, kills the step. That is how a
    clean build first broke the evidence step here and silently skipped 75
    instrumented tests;
  * every step id referenced by an expression actually exists in the job.

Requires PyYAML. Usage: python3 tools/validate_workflow.py
Exit: 0 when clean, 1 otherwise.
"""

from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORKFLOWS = sorted((ROOT / ".github/workflows").glob("*.yml"))

HEREDOC_RE = re.compile(r"^\s*python3 - <<'?(\w+)'?\s*$", re.MULTILINE)

# A command whose non-zero exit is a normal outcome, standing alone under
# `set -e`. `grep` exiting 1 means "no match"; `[ -f x ] && ...` exiting 1
# means "the file is absent". Both are legitimate results that must be guarded.
UNGUARDED_RE = re.compile(
    r"(?<![|&!\w-])"                     # not already after a pipe/and/not
    r"(?:\bgrep\b|\[\s[^\]]*\]\s*&&)",
)
CONDITION_PREFIX_RE = re.compile(
    r"^\s*(?:if|elif|while|until|then|else|do|!)\b")
GUARD_RE = re.compile(r"\|\|\s*(?:true|:)\b|\|\|\s*\w+=|&&\s*(?:true|:)\b")
STEP_REF_RE = re.compile(r"steps\.([A-Za-z0-9_-]+)\.")


def main() -> int:
    try:
        import yaml
    except ImportError:
        print("PyYAML is required: pip install pyyaml", file=sys.stderr)
        return 2
    if not WORKFLOWS:
        print("no workflows found", file=sys.stderr)
        return 1

    problems: list[str] = []
    checked_scripts = checked_heredocs = 0

    for wf in WORKFLOWS:
        rel = wf.relative_to(ROOT)
        try:
            doc = yaml.safe_load(wf.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001 - report any parse failure
            problems.append(f"{rel}: YAML does not parse: {exc}")
            continue
        if not isinstance(doc, dict) or "jobs" not in doc:
            problems.append(f"{rel}: not a workflow (no jobs key)")
            continue

        for job_name, job in (doc.get("jobs") or {}).items():
            steps = job.get("steps") or []
            ids = {s.get("id") for s in steps if isinstance(s, dict) and s.get("id")}

            for pos, step in enumerate(steps):
                if not isinstance(step, dict):
                    problems.append(f"{rel}:{job_name}#{pos}: step is not a mapping")
                    continue
                label = step.get("name") or step.get("id") or f"step {pos}"

                # Every `steps.<id>` reference must resolve.
                script_blob = str(step.get("run", "")) + str(step.get("if", ""))
                for ref in STEP_REF_RE.findall(script_blob):
                    if ref not in ids:
                        problems.append(
                            f"{rel}:{job_name}/{label}: references steps.{ref} "
                            f"but no step in this job has that id")

                run = step.get("run")
                if not run:
                    continue
                checked_scripts += 1

                # Commands whose failure is a normal result must be guarded,
                # because GitHub Actions runs `bash -e -o pipefail`.
                joined = re.sub(r"\\\n\s*", " ", run)
                # Track whether we are inside an `if`/`while` body, where a
                # non-zero status is the point of the command, or inside a
                # `for` body, where it is not.
                blocks: list[str] = []
                for ln, raw_line in enumerate(joined.split("\n"), 1):
                    stripped = raw_line.strip()
                    if not stripped or stripped.startswith("#"):
                        continue
                    if re.match(r"^(?:if|while|until)\b", stripped):
                        blocks.append("cond")
                    elif re.match(r"^for\b", stripped):
                        blocks.append("loop")
                    elif re.match(r"^(?:fi|done)\b", stripped):
                        if blocks:
                            blocks.pop()
                    if "cond" in blocks:
                        continue
                    if CONDITION_PREFIX_RE.match(raw_line):
                        continue
                    if GUARD_RE.search(stripped):
                        continue
                    if UNGUARDED_RE.search(stripped):
                        problems.append(
                            f"{rel}:{job_name}/{label} line {ln}: a grep or "
                            f"`[ ... ] &&` stands alone, and GitHub runs bash "
                            f"with -e, so a non-match fails the step: "
                            f"{stripped[:120]}")

                # A step that pipes into tee must set pipefail, otherwise the
                # step outcome reports tee's status instead of the tool's.
                if re.search(r"\|\s*tee\b", run) and "pipefail" not in run:
                    problems.append(
                        f"{rel}:{job_name}/{label}: pipes into tee without "
                        f"set -o pipefail, so a failure would report success")

                # bash syntax, which catches unterminated here-documents.
                with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False) as fh:
                    fh.write(run)
                    tmp = fh.name
                proc = subprocess.run(["bash", "-n", tmp], capture_output=True, text=True)
                Path(tmp).unlink(missing_ok=True)
                if proc.returncode != 0:
                    problems.append(
                        f"{rel}:{job_name}/{label}: bash -n failed: "
                        f"{' '.join(proc.stderr.split())[:300]}")

                # Embedded python must compile.
                for m in HEREDOC_RE.finditer(run):
                    tag = m.group(1)
                    end = re.search(rf"^{tag}$", run[m.end():], re.MULTILINE)
                    if not end:
                        problems.append(
                            f"{rel}:{job_name}/{label}: heredoc {tag} is never "
                            f"terminated at column 0 of the rendered script")
                        continue
                    body = run[m.end():m.end() + end.start()]
                    checked_heredocs += 1
                    try:
                        compile(body, f"{rel}:{job_name}/{label}:{tag}", "exec")
                    except SyntaxError as exc:
                        problems.append(
                            f"{rel}:{job_name}/{label}: python heredoc {tag} "
                            f"does not compile: {exc}")

    print("=" * 72)
    print("Axiom Calc - GitHub Actions workflow validation")
    print("=" * 72)
    print(f"Workflows checked ......... {len(WORKFLOWS)}")
    print(f"run scripts bash -n'd ..... {checked_scripts}")
    print(f"python heredocs compiled .. {checked_heredocs}")
    print("-" * 72)
    if problems:
        for pr in problems:
            print("FAIL", pr)
        print("-" * 72)
        print(f"RESULT: FAIL - {len(problems)} problem(s)")
        return 1
    print("No problems found.")
    print("-" * 72)
    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
