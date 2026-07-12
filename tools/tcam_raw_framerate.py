#!/usr/bin/env python3
"""Raw tCam frame-rate probe.

Connects directly to the camera's TCP JSON socket (port 5001), starts
continuous streaming, and discards every frame's payload as soon as its
boundary is found. No base64 decode, no JSON parsing, no image processing —
this isolates the camera + WiFi link's own delivery rate from any app-side
overhead.

Usage:
    ./tcam_raw_framerate.py [host] [--port 5001] [--duration 15]
"""

import argparse
import socket
import statistics
import sys
import time

STX = b"\x02"
ETX = b"\x03"

STREAM_ON_CMD = (
    b'\x02{"cmd":"stream_on", "args": {\n'
    b'    "delay_msec":0,\n'
    b'    "num_frames":0\n'
    b"   }}\x03"
)
STREAM_OFF_CMD = b'\x02{"cmd":"stream_off"}\x03'


def run(host: str, port: int, duration: float, recv_size: int, gaps_file: str | None = None) -> None:
    sock = socket.create_connection((host, port), timeout=10)
    sock.settimeout(1.0)
    print(f"Connected to {host}:{port}")

    sock.sendall(STREAM_ON_CMD)
    print("Sent stream_on, streaming for %.1fs..." % duration)

    buf = bytearray()
    frame_count = 0
    total_bytes = 0
    gaps = []
    last_frame_time = None
    start = time.monotonic()

    try:
        while time.monotonic() - start < duration:
            try:
                chunk = sock.recv(recv_size)
            except socket.timeout:
                continue
            if not chunk:
                print("Camera closed the connection")
                break

            total_bytes += len(chunk)
            buf += chunk

            while True:
                stx = buf.find(STX)
                if stx == -1:
                    buf.clear()
                    break
                etx = buf.find(ETX, stx + 1)
                if etx == -1:
                    del buf[:stx]
                    break
                frame = buf[stx + 1:etx]
                del buf[:etx + 1]

                if b'"radiometric"' in frame:
                    now = time.monotonic()
                    frame_count += 1
                    if last_frame_time is not None:
                        gaps.append(now - last_frame_time)
                    last_frame_time = now
    except KeyboardInterrupt:
        print("\nInterrupted")
    finally:
        elapsed = time.monotonic() - start
        try:
            sock.sendall(STREAM_OFF_CMD)
        except OSError:
            pass
        sock.close()

    print()
    print(f"elapsed:        {elapsed:.2f}s")
    print(f"frames:         {frame_count}")
    print(f"raw fps:        {frame_count / elapsed:.2f}")
    print(f"bytes received: {total_bytes} ({total_bytes / elapsed / 1024:.1f} KB/s)")
    if gaps:
        print(f"inter-frame gap: avg={statistics.mean(gaps) * 1000:.1f}ms "
              f"min={min(gaps) * 1000:.1f}ms max={max(gaps) * 1000:.1f}ms "
              f"stdev={statistics.pstdev(gaps) * 1000:.1f}ms")
    if gaps_file:
        with open(gaps_file, "w") as f:
            f.write("\n".join(f"{g * 1000:.3f}" for g in gaps))
        print(f"wrote {len(gaps)} gap samples to {gaps_file}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("host", nargs="?", default="192.168.68.76", help="camera IP address")
    parser.add_argument("--port", type=int, default=5001)
    parser.add_argument("--duration", type=float, default=15.0, help="seconds to stream")
    parser.add_argument("--recv-size", type=int, default=65536, help="socket recv() buffer size")
    parser.add_argument("--dump-gaps", metavar="PATH", help="write raw inter-frame gap samples (ms, one per line) to this file")
    args = parser.parse_args()

    try:
        run(args.host, args.port, args.duration, args.recv_size, args.dump_gaps)
    except OSError as e:
        print(f"Connection error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
