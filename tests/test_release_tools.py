from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"


class ReleaseToolsTest(unittest.TestCase):
    def make_tree(self, base: Path) -> Path:
        root = base / "project"
        (root / "config").mkdir(parents=True)
        (root / "release").mkdir()
        (root / "src").mkdir()
        (root / "src/a.txt").write_text("alpha\n", encoding="utf-8")
        (root / "BUILD_STATUS.md").write_text("# Status\n\n## VERIFIED\n\n- source\n\n## NOT VERIFIED\n\n- device\n", encoding="utf-8")
        (root / "config/toolchain.lock.json").write_text(json.dumps({
            "format": 1,
            "source_date_epoch": 1788220800,
            "toolchains": {"test": {"version": "1"}},
        }) + "\n", encoding="utf-8")
        return root

    def run_script(self, name: str, *args: str, expect: int = 0) -> subprocess.CompletedProcess[str]:
        result = subprocess.run([sys.executable, str(SCRIPTS / name), *args], text=True, capture_output=True)
        self.assertEqual(result.returncode, expect, msg=result.stdout + result.stderr)
        return result

    def test_metadata_and_zip_are_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_tree(Path(tmp))
            self.run_script("generate_release_metadata.py", "--root", str(root))
            first_filelist = (root / "FILELIST.txt").read_bytes()
            first_manifest = (root / "release/release-manifest.json").read_bytes()
            self.run_script("generate_release_metadata.py", "--root", str(root), "--check")
            self.assertEqual(first_filelist, (root / "FILELIST.txt").read_bytes())
            self.assertEqual(first_manifest, (root / "release/release-manifest.json").read_bytes())

            a = Path(tmp) / "a.zip"
            b = Path(tmp) / "b.zip"
            self.run_script("package_release.py", "--root", str(root), "--output", str(a))
            self.run_script("package_release.py", "--root", str(root), "--output", str(b))
            self.assertEqual(hashlib.sha256(a.read_bytes()).hexdigest(), hashlib.sha256(b.read_bytes()).hexdigest())

    def test_stale_pass_proof_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_tree(Path(tmp))
            reports = root / "reports"
            reports.mkdir()
            (reports / "latest-p1.json").write_text('{"success": true, "markerSeen": true}\n', encoding="utf-8")
            result = self.run_script("validate_release_tree.py", "--root", str(root), expect=2)
            self.assertIn("acceptance PASS proof", result.stderr)


if __name__ == "__main__":
    unittest.main()
