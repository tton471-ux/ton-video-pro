# TON SMOOTH ENGINE — Android implementation plan

## Product goal
A private, local-only Android video preparation app for TikTok. Its goal is smooth motion at 60 FPS or higher where practical, while preserving the source resolution, high quality, and HDR metadata whenever the device's decode/encode path supports it.

## Default export policy
- FPS: Smart target, minimum 60 FPS for sources below 60 when interpolation is feasible; preserve source FPS at 60 or above by default.
- Resolution: Original (never upscale/downscale silently).
- Quality: Maximum practical quality; choose codec/bitrate based on device capabilities and output size.
- HDR: Preserve when the complete decode → processing → encode path supports it. If not, warn and ask before SDR conversion.
- Processing: Local only. No video upload, cloud processing, analytics upload, or TikTok account integration.
- Source: Never overwrite the original.

## Important engineering truth
- Merely changing a file's FPS metadata is not frame interpolation.
- 30→60 FPS requires generating intermediate frames (motion-compensated optical flow or a suitable ML model) or duplicating frames. Duplication reaches a 60 FPS container but does not make motion genuinely smoother.
- The first release must not claim true interpolation until intermediate frames are generated and the output is verified.
- 60 FPS and higher does not guarantee TikTok will retain that frame rate or quality; TikTok may transcode uploaded media.

## App workflow
1. Select local video via Android Storage Access Framework.
2. Analyze container, duration, dimensions, rotation, frame rate, video/audio codecs, bitrate, and HDR/color metadata where available.
3. Recommend an output plan and explain whether genuine interpolation is available on this device.
4. Process locally using Android MediaExtractor / MediaCodec and a frame-processing stage.
5. Preserve audio timing; remux or encode audio only when necessary.
6. Verify output by reopening it and checking actual frame timestamps, dimensions, codec, bitrate, and color metadata.
7. Save a new file; keep source untouched.

## UI
- Home: one main card, “SMOOTH ENGINE”, plus recent jobs/settings.
- Analyze: source preview and media properties.
- Process: target FPS, quality, HDR handling, progress and operation log.
- Result: source/output comparison and verification details.

## Device compatibility
Detect available decoders, encoders, hardware acceleration, supported sizes/rates, color formats, HDR profiles, and memory constraints at runtime. Do not assume capabilities from phone model alone. Prefer a stable supported Android SDK for the build; Android 17 devices can run apps built against earlier stable SDKs, subject to compatibility testing.

## Development milestones
- M1: Native app shell, local video picker, metadata analyzer, and capability report.
- M2: Reliable local 60 FPS output path with audio sync and output verification.
- M3: Genuine frame interpolation (motion-compensated or ML-based) with device-specific performance tuning.
- M4: HDR preservation tests (HLG/PQ, BT.2020, 10-bit) and TikTok-oriented export presets.
- M5: Stress tests, thermal/memory handling, APK signing and install validation.

## Acceptance tests
- No network permission or video upload path in the local-only build.
- Source file remains unchanged.
- 30 FPS source is only labeled “interpolated 60 FPS” if new intermediate frames exist.
- Output timestamps are inspected to verify actual frame cadence.
- Audio/video sync is checked.
- Resolution and HDR are retained where supported, with an explicit warning otherwise.
- Long clips fail safely without corrupting the source.
