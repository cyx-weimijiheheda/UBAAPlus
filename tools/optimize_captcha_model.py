"""Reproduce the compact OCR model using onnx==1.20.0 and onnxruntime==1.24.1.

Input: the standard-LSTM model from UBAA 6c4dba8d (common.onnx.gz).
Only the LSTM is quantized; existing quantized convolutions and the full charset remain intact.
Usage: python tools/optimize_captcha_model.py original.onnx.gz output.onnx.gz
"""
import argparse
import gzip
import hashlib
import tempfile
from pathlib import Path

import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = args.source.read_bytes()
    if source[:2] == b"\x1f\x8b":
        source = gzip.decompress(source)
    model = onnx.load_from_string(source)
    if sum(n.op_type == "LSTM" for n in model.graph.node) != 1:
        parser.error("Expected the original standard-LSTM model; do not re-quantize the output")
    with tempfile.TemporaryDirectory() as directory:
        original = Path(directory) / "original.onnx"
        compact = Path(directory) / "compact.onnx"
        original.write_bytes(source)
        quantize_dynamic(str(original), str(compact), op_types_to_quantize=["LSTM"], per_channel=True, weight_type=QuantType.QInt8)
        data = compact.read_bytes()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(gzip.compress(data, mtime=0))
    print(f"Source SHA256: {hashlib.sha256(source).hexdigest()}")
    print(f"Raw model: {len(source)} -> {len(data)} bytes")
    print(f"Output SHA256: {hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
