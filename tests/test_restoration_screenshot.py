import hashlib,importlib.util,random,struct,tempfile,unittest,zlib
from pathlib import Path
spec=importlib.util.spec_from_file_location("verify",Path(__file__).resolve().parents[1]/"scripts"/"verify-restoration-screenshot.py")
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
def chunk(name,data):
    return struct.pack(">I",len(data))+name+data+struct.pack(">I",zlib.crc32(name+data)&0xffffffff)
class Gate(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.image=Path(self.tmp.name)/"image.png";self.log=Path(self.tmp.name)/"preview.log"
        rng=random.Random(22)
        pixels=b"".join(b"\0"+rng.randbytes(320) for _ in range(640))
        self.raw=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",320,640,8,0,0,0,0))+chunk(b"IDAT",zlib.compress(pixels))+chunk(b"IEND",b"")
        self.image.write_bytes(self.raw)
        self.log.write_text("restorationScreenshotSha256="+hashlib.sha256(self.raw).hexdigest()+" width=320 height=640\n")
    def test_valid_mock(self):v.verify(self.image,self.log)
    def test_old_error(self):
        self.image.write_bytes(b"run-as: unknown package: com.wfs.storyboost.studio\n")
        with self.assertRaises(ValueError):v.verify(self.image,self.log)
    def test_bad_crc(self):
        b=bytearray(self.raw);b[60]^=1;self.image.write_bytes(b)
        with self.assertRaisesRegex(ValueError,"CRC"):v.verify(self.image,self.log)
    def test_wrong_sha(self):
        self.log.write_text("restorationScreenshotSha256="+"a"*64+" width=320 height=640\n")
        with self.assertRaisesRegex(ValueError,"provenance"):v.verify(self.image,self.log)
    def test_duplicate(self):
        self.log.write_text(self.log.read_text()*2)
        with self.assertRaisesRegex(ValueError,"provenance"):v.verify(self.image,self.log)
    def test_truncated(self):
        self.image.write_bytes(self.raw[:-8])
        with self.assertRaises(ValueError):v.verify(self.image,self.log)
if __name__=="__main__":unittest.main()
