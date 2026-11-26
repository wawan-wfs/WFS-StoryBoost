import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "restoration_benchmark.py"
if not SCRIPT.exists():
    SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "restoration_benchmark.py"
spec = importlib.util.spec_from_file_location("restoration_benchmark", SCRIPT)
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)


class RestorationBenchmarkTest(unittest.TestCase):
    def test_identity_oracle(self):
        a = b.reference("edges")
        score = b.metrics(a, a)
        self.assertEqual(score["mse"], 0)
        self.assertTrue(score["exact"])
        self.assertIsNone(score["psnr_db"])
        self.assertAlmostEqual(score["ssim_block8"], 1)

    def test_bad_shape(self):
        with self.assertRaises(ValueError):
            b.metrics(b.reference("edges"), b"x")

    def test_deterministic_and_candidate(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)
            first = b.run(p/"first")
            self.assertEqual(first, b.run(p/"second"))
            self.assertEqual(len(first["cases"]), 8)
            candidates = p/"candidate"
            candidates.mkdir()
            for row in first["cases"]:
                name = row["case"]
                self.assertFalse(row["baseline"]["exact"])
                # Ground truth copy is an oracle self-test, NOT model output.
                (candidates/(name+".pgm")).write_bytes((p/"first"/(name+"-truth.pgm")).read_bytes())
            scored = b.run(p/"scored", candidates)
            self.assertTrue(all(row["candidate"]["exact"] for row in scored["cases"]))
            (candidates/"deblur-edges.pgm").write_bytes(b"bad")
            with self.assertRaises(ValueError):
                b.run(p/"bad", candidates)


if __name__ == "__main__":
    unittest.main()
