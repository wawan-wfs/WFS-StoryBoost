#!/usr/bin/env python3
"""Fail closed unless the exact required Android preview tests and metrics exist."""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CLASS = "com.wfs.storyboost.VideoMediaQaTest"
REQUIRED = (
    "timelinePreviewUsesRenderedCaptionAndCutsAndRemovesPrivateFileAfterRecreation",
    "timelinePrivatePreviewRejectsDeletedSourceAndCanCancelBeforeCompletion",
    "timelineAdjustedRepeatedClipRendersColorAndMute",
    "timelineAdjustmentPanelStagesCancelApplyAndReset",
    "legacySchemaReadOnlyAndV2RoundTrip",
    "soundtrackAndFadeV3RoundTrip",
    "timelineSoundtrackAndDipRenderRealMedia",
    "timelineInteractiveSplitAndSoundtrackPanel",
)


def evidence(reports, log, api, commit):
    if api not in (29, 35) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Expected API29/35 and exact Git commit SHA")
    found = {name: [] for name in REQUIRED}
    files = sorted(Path(reports).rglob("TEST-*.xml"))
    if not files:
        raise ValueError("No Android JUnit XML reports")
    for path in files:
        for case in ET.parse(path).iter("testcase"):
            name=case.get("name")
            expected_class=("com.wfs.storyboost.TimelineAdjustmentsQaTest" if name in ("legacySchemaReadOnlyAndV2RoundTrip","soundtrackAndFadeV3RoundTrip") else CLASS)
            if case.get("classname") == expected_class and name in found:
                found[name].append(case)
    for name, cases in found.items():
        if len(cases) != 1:
            raise ValueError(f"Expected one result for {name}, got {len(cases)}")
        case = cases[0]
        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
            raise ValueError(f"Required test did not pass: {name}")
    raw = Path(log).read_text(encoding="utf-8", errors="replace")
    # Restrict metrics to the dedicated app tag, not arbitrary output.
    lines = [line for line in raw.splitlines() if "WfsTimelinePreviewQA" in line]
    render = [dict(render_ms=int(a), bytes=int(b), duration_ms=int(c))
              for line in lines for a, b, c in re.findall(
                  r"\brenderMs=(\d+) bytes=(\d+) durationMs=(\d+)\b", line)]
    ready = [dict(ready_ms=int(a), duration_ms=int(b))
             for line in lines for a, b in re.findall(
                 r"\breadyMs=(\d+) durationMs=(\d+)\b", line)]
    seeks = [int(x) for line in lines for x in re.findall(r"\bseekSettledMs=(\d+)\b", line)]
    # The required test renders a two-second timeline, with a cut at 1000 ms.
    # Allow the existing encoder duration tolerance, but require the actual
    # two-second preview and decoded-media JUnit gate, not just any log line.
    valid_render = [r for r in render if r["duration_ms"] == 2000 and r["bytes"] >= 1024]
    valid_ready = [r for r in ready if 1750 <= r["duration_ms"] <= 2250]
    if not valid_render or not valid_ready:
        raise ValueError("Missing two-second preview render/player readiness metrics")
    if not any(0 <= s < 1000 for s in seeks) or not any(1000 <= s <= 2250 for s in seeks):
        raise ValueError("Missing settled seek evidence on both sides of the cut")
    mix = [(float(a), float(b)) for line in lines for a,b in re.findall(
        r"\bsoundtrackRms=([0-9.eE+-]+) gapRms=([0-9.eE+-]+) dip=verified\b", line)]
    if len(mix)<2 or not all(0.015<a<=1 and 0<=b<a*0.1 for a,b in mix):
        raise ValueError("Missing or invalid decoded soundtrack and dip evidence on preview/export")
    return {"commit": commit, "api": api, "scope": "x86_64 emulator connected preview gate",
            "soundtrack_pcm": [{"audible_rms":a,"gap_rms":b} for a,b in mix],
            "required_tests": {name: "passed" for name in REQUIRED},
            "render": render, "ready": ready, "seek_settled_ms": seeks,
            "junit_files": len(files), "status": "passed"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--api", type=int, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = evidence(args.reports, args.log, args.api, args.commit)
        code = 0
    except (ValueError, OSError, ET.ParseError) as exc:
        result = {"commit": args.commit, "api": args.api, "status": "failed", "reason": str(exc)}
        code = 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2), file=sys.stdout if code == 0 else sys.stderr)
    return code


if __name__ == "__main__":
    sys.exit(main())
