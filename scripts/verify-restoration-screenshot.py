#!/usr/bin/env python3
"""Fail closed: exact screenshot captured during the actual lab UI test."""
import hashlib, re, struct, sys, zlib
from pathlib import Path

def verify(image, log):
    raw=Path(image).read_bytes()
    if not 1024<len(raw)<=24*1024*1024 or raw[:8]!=b'\x89PNG\r\n\x1a\n':
        raise ValueError("Not a bounded PNG screenshot")
    position=8; size=None; idat=False; end=False
    while position+12<=len(raw):
        length,kind=struct.unpack_from(">I4s",raw,position)
        stop=position+12+length
        if stop>len(raw): raise ValueError("Truncated PNG")
        data=raw[position+8:stop-4]
        if zlib.crc32(kind+data)&0xffffffff!=struct.unpack_from(">I",raw,stop-4)[0]:
            raise ValueError("PNG chunk CRC mismatch")
        if position==8:
            if kind!=b'IHDR' or length!=13: raise ValueError("Missing IHDR")
            size=struct.unpack_from(">II",data)
        if kind==b'IDAT': idat=True
        if kind==b'IEND':
            if length!=0 or stop!=len(raw): raise ValueError("Bad IEND")
            end=True;break
        position=stop
    if not size or size[0]<320 or size[1]<640 or not idat or not end:
        raise ValueError("Invalid screenshot dimensions or data")
    marks=re.findall(r"\brestorationScreenshotSha256=([0-9a-f]{64}) width=(\d+) height=(\d+)\b",Path(log).read_text())
    if len(marks)!=1 or marks[0]!=(hashlib.sha256(raw).hexdigest(),str(size[0]),str(size[1])):
        raise ValueError("Screenshot provenance mismatch")
    print("PASS Restoration Lab screenshot SHA-256",marks[0][0],size)

if __name__=="__main__":verify(*sys.argv[1:])
