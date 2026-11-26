import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "qa-beta16-evidence.py"
spec = importlib.util.spec_from_file_location("qa_evidence", SCRIPT)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.xml = self.root / "TEST-fixture.xml"
        self.log = self.root / "preview.log"
        self.commit = "a" * 40
        self.metrics = "\n".join("I/WfsTimelinePreviewQA: " + s for s in (
            "renderMs=300 bytes=4096 durationMs=2000",
            "readyMs=430 durationMs=2001", "seekSettledMs=600", "seekSettledMs=1400",
            "soundtrackRms=0.05 gapRms=0.0 dip=verified", "soundtrackRms=0.05 gapRms=0.0 dip=verified",
            "dualTrackFirstRms=0.03 overlapRms=0.045 early780=0.04 early440=0.001 late780=0.04 late440=0.04 keyframe=verified","dualTrackFirstRms=0.03 overlapRms=0.045 early780=0.04 early440=0.001 late780=0.04 late440=0.04 keyframe=verified"))
        self.log.write_text(self.metrics)
        self.write_xml()

    def write_xml(self, first="", second="", duplicate=False, omit=False):
        cases = [f'<testcase classname="{qa.CLASS if n not in ("legacySchemaReadOnlyAndV2RoundTrip","soundtrackAndFadeV3RoundTrip","dualTrackAndKeyframesV4RoundTrip") else "com.wfs.storyboost.TimelineAdjustmentsQaTest"}" name="{n}">{tag}</testcase>'
                 for n, tag in zip(qa.REQUIRED, (first, second) + ("",)*(len(qa.REQUIRED)-2))]
        if omit:
            cases.pop()
        if duplicate:
            cases.append(cases[0])
        self.xml.write_text("<testsuite>" + "".join(cases) + "</testsuite>")

    def check(self, api=29, commit=None):
        return qa.evidence(self.root, self.log, api, commit or self.commit)

    def test_valid_api29(self):
        self.assertEqual(self.check()["status"], "passed")

    def test_valid_api35(self):
        self.assertEqual(self.check(35)["api"], 35)

    def test_skip(self):
        self.write_xml(first="<skipped/>")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            self.check()

    def test_failure(self):
        self.write_xml(second="<failure/>")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            self.check()

    def test_error(self):
        self.write_xml(first="<error/>")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            self.check()

    def test_missing_test(self):
        self.write_xml(omit=True)
        with self.assertRaisesRegex(ValueError, "got 0"):
            self.check()

    def test_duplicate(self):
        self.write_xml(duplicate=True)
        with self.assertRaisesRegex(ValueError, "got 2"):
            self.check()

    def test_missing_xml(self):
        self.xml.unlink()
        with self.assertRaisesRegex(ValueError, "No Android"):
            self.check()

    def test_malformed_xml(self):
        self.xml.write_text("<testsuite>")
        with self.assertRaises(qa.ET.ParseError):
            self.check()

    def test_missing_ready(self):
        self.log.write_text(self.metrics.replace("readyMs=430", "unknown=430"))
        with self.assertRaisesRegex(ValueError, "readiness"):
            self.check()

    def test_wrong_duration(self):
        self.log.write_text(self.metrics.replace("durationMs=2000", "durationMs=9000"))
        with self.assertRaisesRegex(ValueError, "readiness"):
            self.check()

    def test_small_file(self):
        self.log.write_text(self.metrics.replace("bytes=4096", "bytes=12"))
        with self.assertRaisesRegex(ValueError, "readiness"):
            self.check()

    def test_wrong_tag(self):
        self.log.write_text(self.metrics.replace("WfsTimelinePreviewQA", "OtherTag"))
        with self.assertRaisesRegex(ValueError, "readiness"):
            self.check()

    def test_missing_cut_side(self):
        self.log.write_text(self.metrics.replace("seekSettledMs=1400", "seekSettledMs=800"))
        with self.assertRaisesRegex(ValueError, "both sides"):
            self.check()

    def test_missing_soundtrack(self):
        self.log.write_text(self.metrics.replace("soundtrackRms=", "unknown="))
        with self.assertRaisesRegex(ValueError, "soundtrack"):
            self.check()

    def test_soundtrack_leak(self):
        self.log.write_text(self.metrics.replace("gapRms=0.0", "gapRms=0.03"))
        with self.assertRaisesRegex(ValueError, "soundtrack"):
            self.check()

    def test_missing_dual_track(self):
        self.log.write_text(self.metrics.replace("dualTrackFirstRms=", "unknown="))
        with self.assertRaisesRegex(ValueError, "dual-track"):
            self.check()

    def test_missing_voice_frequency(self):
        self.log.write_text(self.metrics.replace('late440=0.04','late440=0.0'))
        with self.assertRaisesRegex(ValueError, 'dual-track'):
            self.check()

    def test_early_voice_leak(self):
        self.log.write_text(self.metrics.replace('early440=0.001','early440=0.04'))
        with self.assertRaisesRegex(ValueError, 'dual-track'):
            self.check()

    def test_wrong_api(self):
        with self.assertRaisesRegex(ValueError, "API29/35"):
            self.check(34)

    def test_short_commit(self):
        with self.assertRaisesRegex(ValueError, "exact Git"):
            self.check(commit="abcdef")


if __name__ == "__main__":
    unittest.main()
