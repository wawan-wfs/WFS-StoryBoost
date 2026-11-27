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
            "dualTrackFirstRms=0.03 overlapRms=0.045 early780=0.04 early440=0.001 late780=0.04 late440=0.04 keyframe=verified","dualTrackFirstRms=0.03 overlapRms=0.045 early780=0.04 early440=0.001 late780=0.04 late440=0.04 keyframe=verified",
            "crossfadeVerified duration=3500 midRed=127 midBlue=126 early440=0.12 early780=0.001 overlap440=0.06 overlap780=0.06 late780=0.12 late440=0.001","crossfadeVerified duration=3500 midRed=127 midBlue=126 early440=0.12 early780=0.001 overlap440=0.06 overlap780=0.06 late780=0.12 late440=0.001",
            "pipVerified before=0 early=2000 late=2000 after=0 dx=0.4 dy=0.4","pipVerified before=0 early=2000 late=2000 after=0 dx=0.4 dy=0.4"))
        mixed="I/WfsTimelinePreviewQA: mixedCutVerified duration=5500 mixRed=127 mixBlue=126 pipRed=127 pipBlue=126 baseBlue=253 finalRed=253"
        self.metrics+="\n"+mixed+"\n"+mixed+"\n"+"\n".join(
            f"I/WfsTimelinePreviewQA: hdBenchmarkVerified seconds={s} mode={m} elapsedMs=1000 bytes=4096 videoBitrate=4000000 sampledPssKb=20000 frames={s*24} driftMs=15"
            for s in (2,10,30) for m in ("preview","export"))
        self.log.write_text(self.metrics)
        self.write_xml()

    def write_xml(self, first="", second="", duplicate=False, omit=False):
        cases = [f'<testcase classname="{qa.CLASS if n not in ("legacySchemaReadOnlyAndV2RoundTrip","soundtrackAndFadeV3RoundTrip","dualTrackAndKeyframesV4RoundTrip","zoomV5RoundTrip","overlapV6RoundTrip","pictureV7RoundTrip","cutAndCurveV8RoundTrip") else "com.wfs.storyboost.TimelineAdjustmentsQaTest"}" name="{n}">{tag}</testcase>'
                 for n, tag in zip(qa.REQUIRED, (first, second) + ("",)*(len(qa.REQUIRED)-2))]
        for i,n in enumerate(qa.REQUIRED[:len(cases)]):
            if n in ("originalModelInferenceAndHashGate","restorationLabSyntheticUiRunsOffline","refinedModelsInferenceAndOriginalCompatibility"):
                cases[i]=cases[i].replace(qa.CLASS,"com.wfs.storyboost.RestorationLabQaTest")
        if omit:
            cases.pop()
        cases.append(f'<testcase classname="{qa.CLASS}" name="timelineHdBenchmarkActualAndroid"/>')
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

    def test_crossfade_missing(self):
        self.log.write_text(self.metrics.replace('crossfadeVerified', 'unknown'))
        with self.assertRaisesRegex(ValueError, 'crossfade'):
            self.check()

    def test_crossfade_missing_blue(self):
        self.log.write_text(self.metrics.replace('midBlue=126', 'midBlue=0'))
        with self.assertRaisesRegex(ValueError, 'crossfade'):
            self.check()

    def test_crossfade_missing_audio_ramp(self):
        self.log.write_text(self.metrics.replace('overlap440=0.06', 'overlap440=0.12'))
        with self.assertRaisesRegex(ValueError, 'crossfade'):
            self.check()

    def test_pip_missing_movement(self):
        self.log.write_text(self.metrics.replace('dx=0.4','dx=0.0'))
        with self.assertRaisesRegex(ValueError, 'PiP'):
            self.check()

    def test_pip_outside_range(self):
        self.log.write_text(self.metrics.replace('before=0 early=2000','before=200 early=2000'))
        with self.assertRaisesRegex(ValueError, 'PiP'):
            self.check()

    def test_missing_mixed_cut(self):
        self.log.write_text(self.metrics.replace("mixedCutVerified","missing"))
        with self.assertRaisesRegex(ValueError,"mixed-cut"):
            self.check()
    def test_hd_missing_api35(self):
        self.log.write_text(self.metrics.replace("hdBenchmarkVerified","missing"))
        with self.assertRaisesRegex(ValueError,"HD benchmark"):
            self.check(35)
    def test_wrong_api(self):
        with self.assertRaisesRegex(ValueError, "API29/35"):
            self.check(34)

    def test_short_commit(self):
        with self.assertRaisesRegex(ValueError, "exact Git"):
            self.check(commit="abcdef")


if __name__ == "__main__":
    unittest.main()
