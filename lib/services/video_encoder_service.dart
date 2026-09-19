// SnapTok Video Encoder Service
//
// LICENSING & LEGAL NOTICE:
// This service uses `ffmpeg_kit_flutter_min_gpl` (or full-gpl) for on-device video transcoding.
// Re-encoding to H.264 using libx264 invokes the GNU General Public License (GPL) v3.
// If distributing this application publicly (e.g., via Google Play), ensure your legal team
// reviews GPL compliance obligations, or switch to the native platform channel implementation
// (Android MediaCodec / iOS AVFoundation) which carries standard permissive licensing (Apache 2.0 / Apple).
//
// ARCHITECTURE NOTE:
// All FFmpeg calls are isolated behind this single service class so that the underlying
// transcoding engine can be swapped or upgraded without modifying the rest of the application.

import 'dart:async';
import 'dart:io';
import 'package:ffmpeg_kit_flutter_min_gpl/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_min_gpl/ffmpeg_kit_config.dart';
import 'package:ffmpeg_kit_flutter_min_gpl/ffprobe_kit.dart';
import 'package:ffmpeg_kit_flutter_min_gpl/return_code.dart';
import 'package:ffmpeg_kit_flutter_min_gpl/statistics.dart';
import 'package:path/path.dart' as p;

/// Represents the outcome of an in-app video re-encoding operation.
class VideoEncodeResult {
  final File file;
  final bool wasReencoded;
  final bool wasSkippedAlreadyCompatible;
  final String? warningMessage;
  final double durationSeconds;

  const VideoEncodeResult({
    required this.file,
    required this.wasReencoded,
    required this.wasSkippedAlreadyCompatible,
    this.warningMessage,
    this.durationSeconds = 0.0,
  });
}

class VideoEncoderService {
  static final VideoEncoderService _instance = VideoEncoderService._internal();
  factory VideoEncoderService() => _instance;
  VideoEncoderService._internal();

  /// Re-encodes [input] video to a universally compatible MP4 format:
  /// - Video Codec: H.264 (libx264, pixel format yuv420p)
  /// - Constant Frame Rate (CFR): 30 fps (-r 30 -fps_mode cfr / -vsync cfr)
  /// - Audio Codec: AAC Low Complexity (-c:a aac -profile:a aac_low)
  /// - Audio Sample Rate: 44.1 kHz (-ar 44100) matching container timescale
  /// - Audio Channels: 2 (-ac 2 -channel_layout stereo)
  /// - Container: MP4 with faststart (+faststart for instant moov atom positioning)
  /// - Preset: fast (balanced mobile CPU performance and quality)
  ///
  /// Features:
  /// 1. Pre-inspection: Skips re-encoding if the video is already H.264 + CFR + AAC.
  /// 2. Asynchronous execution with [onProgress] reporting (0.0 to 1.0).
  /// 3. Mandatory 5-point post-encode verification (H.264, AAC, CFR equality, duration sync, channels).
  /// 4. Automatic fallback: Returns original [input] file if encoding or verification fails,
  ///    logging the specific failure and warning rather than shipping a corrupted file.
  /// 5. Cleanup: Deletes the original pre-encode temp file when re-encoding succeeds.
  Future<File> reencodeToCompatibleMp4(
    File input, {
    void Function(double progress)? onProgress,
    bool skipIfAlreadyCompatible = true,
  }) async {
    final result = await processVideo(
      input,
      onProgress: onProgress,
      skipIfAlreadyCompatible: skipIfAlreadyCompatible,
    );
    return result.file;
  }

  /// Full method returning [VideoEncodeResult] with compatibility metadata.
  Future<VideoEncodeResult> processVideo(
    File input, {
    void Function(double progress)? onProgress,
    bool skipIfAlreadyCompatible = true,
    int targetFps = 30,
    String preset = 'fast',
    int crf = 23,
  }) async {
    if (!await input.exists()) {
      throw FileSystemException('Input file does not exist', input.path);
    }

    final inputLength = await input.length();
    if (inputLength == 0) {
      throw FileSystemException('Input file is empty (0 bytes)', input.path);
    }

    // Step 1: Pre-probe inspection to check if already H.264 + AAC + CFR
    double totalDurationSeconds = 0.0;
    try {
      final probeSession = await FFprobeKit.getMediaInformation(input.path);
      final mediaInfo = probeSession.getMediaInformation();
      if (mediaInfo != null) {
        final durationStr = mediaInfo.getDuration();
        totalDurationSeconds = double.tryParse(durationStr ?? '0') ?? 0.0;

        if (skipIfAlreadyCompatible) {
          final streams = mediaInfo.getStreams();
          bool hasH264 = false;
          bool hasAac = false;
          bool isLikelyCfr = true;

          for (final stream in streams) {
            final codec = stream.getCodec()?.toLowerCase() ?? '';
            final type = stream.getType()?.toLowerCase() ?? '';
            if (type == 'video' && (codec == 'h264' || codec == 'avc1')) {
              hasH264 = true;
              final rFrameRate = stream.getRealFrameRate();
              final avgFrameRate = stream.getAverageFrameRate();
              if (rFrameRate != null && avgFrameRate != null && rFrameRate != avgFrameRate) {
                isLikelyCfr = false; // Variable frame rate detected
              }
            } else if (type == 'audio' && (codec == 'aac' || codec == 'mp4a')) {
              hasAac = true;
            }
          }

          if (hasH264 && hasAac && isLikelyCfr && totalDurationSeconds > 0) {
            onProgress?.call(1.0);
            return VideoEncodeResult(
              file: input,
              wasReencoded: false,
              wasSkippedAlreadyCompatible: true,
              durationSeconds: totalDurationSeconds,
            );
          }
        }
      }
    } catch (e) {
      // Non-fatal probe failure; continue to re-encode
    }

    // Step 2: Prepare temporary output file
    final tempDir = input.parent;
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final outputFilePath = p.join(tempDir.path, 'snaptok_cfr_$timestamp.mp4');
    final outputFile = File(outputFilePath);

    onProgress?.call(0.05);

    // Step 3: Configure statistics callback for progress tracking
    if (totalDurationSeconds > 0 && onProgress != null) {
      FFmpegKitConfig.enableStatisticsCallback((Statistics stats) {
        final timeInMs = stats.getTime();
        if (timeInMs > 0 && totalDurationSeconds > 0) {
          final currentSeconds = timeInMs / 1000.0;
          final progress = (currentSeconds / totalDurationSeconds).clamp(0.05, 0.95);
          onProgress(progress);
        }
      });
    }

    // Step 4: Execute FFmpeg command
    //
    // FIX FOR BUG 1: Explicitly pass `-c:v libx264 -pix_fmt yuv420p` (forces standard H.264/AVC instead of HEVC or hardware default).
    // FIX FOR BUG 2: Set `-ar 44100` and do not pass conflicting timescale flags so audio time_base matches sample rate (1/44100).
    // FIX FOR BUG 3: Set `-c:a aac -profile:a aac_low -ac 2 -channel_layout stereo` to ensure valid AAC channel configuration (PCE/ADTS).
    // FIX FOR BUG 4: Use `-r $targetFps` AND `-fps_mode cfr` (with `-vsync cfr` fallback) to enforce true constant frame rate (r_frame_rate == avg_frame_rate).
    // Also include `-movflags +faststart` for quick streaming & third-party editor compatibility.

    String buildCommand(String cfrFlag) {
      return '-y -i "${input.path}" '
          '-c:v libx264 -preset $preset -crf $crf '
          '-r $targetFps $cfrFlag '
          '-pix_fmt yuv420p '
          '-c:a aac -profile:a aac_low -ar 44100 -ac 2 -channel_layout stereo -b:a 128k '
          '-movflags +faststart '
          '"$outputFilePath"';
    }

    bool encodeSucceeded = false;
    String? failureReason;
    _VerificationOutcome? lastVerification;

    try {
      // Primary attempt using -fps_mode cfr
      String primaryCommand = buildCommand('-fps_mode cfr');
      var session = await FFmpegKit.execute(primaryCommand);
      var returnCode = await session.getReturnCode();

      // If -fps_mode cfr is unrecognized in older FFmpeg bundles, retry with -vsync cfr
      if (!ReturnCode.isSuccess(returnCode)) {
        final failLog = await session.getFailStackTrace() ?? '';
        if (failLog.contains('fps_mode') || failLog.contains('Unrecognized option')) {
          final fallbackCommand = buildCommand('-vsync cfr');
          session = await FFmpegKit.execute(fallbackCommand);
          returnCode = await session.getReturnCode();
        }
      }

      if (ReturnCode.isSuccess(returnCode)) {
        // Step 5: Mandatory Post-Encode Validation
        lastVerification = await _verifyOutputFile(outputFile);
        if (lastVerification.isValid) {
          encodeSucceeded = true;
          onProgress?.call(1.0);

          // Clean up the pre-encode original file to prevent duplicate disk usage
          try {
            if (await input.exists() && input.path != outputFile.path) {
              await input.delete();
            }
          } catch (_) {}

          return VideoEncodeResult(
            file: outputFile,
            wasReencoded: true,
            wasSkippedAlreadyCompatible: false,
            durationSeconds: lastVerification.durationSeconds,
          );
        } else {
          failureReason = 'Mandatory post-encode verification failed: ${lastVerification.reason}';
          // Clean up bad output file immediately
          if (await outputFile.exists()) {
            await outputFile.delete();
          }

          // If frame rate or audio parameters failed, attempt a one-time retry with -vsync cfr
          final retryCommand = buildCommand('-vsync cfr');
          session = await FFmpegKit.execute(retryCommand);
          returnCode = await session.getReturnCode();
          if (ReturnCode.isSuccess(returnCode)) {
            final retryVerification = await _verifyOutputFile(outputFile);
            if (retryVerification.isValid) {
              encodeSucceeded = true;
              onProgress?.call(1.0);

              try {
                if (await input.exists() && input.path != outputFile.path) {
                  await input.delete();
                }
              } catch (_) {}

              return VideoEncodeResult(
                file: outputFile,
                wasReencoded: true,
                wasSkippedAlreadyCompatible: false,
                durationSeconds: retryVerification.durationSeconds,
              );
            } else {
              failureReason = 'Retry post-encode verification failed: ${retryVerification.reason}';
              if (await outputFile.exists()) {
                await outputFile.delete();
              }
            }
          }
        }
      } else {
        final failLog = await session.getFailStackTrace();
        failureReason = 'FFmpeg returned non-zero exit code $returnCode. Details: $failLog';
      }
    } catch (e) {
      failureReason = 'Exception during FFmpeg execution: $e';
    } finally {
      FFmpegKitConfig.enableStatisticsCallback(null);
    }

    // Step 6: Safe Fallback
    // If re-encoding or mandatory verification failed, DO NOT ship a broken file.
    // Clean up partial output and safely return the original file with a clear warning note.
    if (!encodeSucceeded) {
      try {
        if (await outputFile.exists()) {
          await outputFile.delete();
        }
      } catch (_) {}

      return VideoEncodeResult(
        file: input,
        wasReencoded: false,
        wasSkippedAlreadyCompatible: false,
        warningMessage: 'Saved, but compatibility optimization failed: $failureReason',
        durationSeconds: totalDurationSeconds,
      );
    }

    return VideoEncodeResult(
      file: input,
      wasReencoded: false,
      wasSkippedAlreadyCompatible: false,
    );
  }

  /// Mandatory Post-Encode Validation:
  /// 1. Video codec_name is exactly "h264" (or "avc1"). Rejects "hevc", "vp9", etc. (BUG 1)
  /// 2. Audio codec_name is exactly "aac" (or "mp4a").
  /// 3. Video and audio stream durations match within ±0.5 seconds (BUG 2: timebase/sample_rate desync).
  /// 4. r_frame_rate equals avg_frame_rate exactly (BUG 4: true CFR confirmed).
  /// 5. Audio channel count is 2 (stereo) with valid channel configuration (BUG 3).
  Future<_VerificationOutcome> _verifyOutputFile(File file) async {
    if (!await file.exists()) {
      return const _VerificationOutcome(false, 'Output file was not created');
    }

    final length = await file.length();
    if (length < 10240) {
      return _VerificationOutcome(false, 'Output file is unnaturally small ($length bytes)');
    }

    // Verify MP4 ftyp box signature in the first 32 bytes
    try {
      final raf = await file.open(mode: FileMode.read);
      final header = await raf.read(32);
      await raf.close();

      bool hasFtyp = false;
      for (int i = 0; i < header.length - 4; i++) {
        if (header[i] == 0x66 && // 'f'
            header[i + 1] == 0x74 && // 't'
            header[i + 2] == 0x79 && // 'y'
            header[i + 3] == 0x70) { // 'p'
          hasFtyp = true;
          break;
        }
      }

      if (!hasFtyp) {
        return const _VerificationOutcome(false, 'Missing MP4 ftyp signature box in header');
      }
    } catch (e) {
      return _VerificationOutcome(false, 'Failed reading header: $e');
    }

    // FFprobe mandatory post-encode validation
    try {
      final probe = await FFprobeKit.getMediaInformation(file.path);
      final info = probe.getMediaInformation();
      if (info == null) {
        return const _VerificationOutcome(false, 'FFprobe could not parse media info');
      }

      final fileDurationStr = info.getDuration();
      final fileDuration = double.tryParse(fileDurationStr ?? '0') ?? 0.0;
      if (fileDuration <= 0.0) {
        return _VerificationOutcome(false, 'Output reports invalid file duration: $fileDuration s');
      }

      final streams = info.getStreams();
      StreamInformation? videoStream;
      StreamInformation? audioStream;

      for (final s in streams) {
        final type = s.getType()?.toLowerCase();
        if (type == 'video' && videoStream == null) {
          videoStream = s;
        } else if (type == 'audio' && audioStream == null) {
          audioStream = s;
        }
      }

      // Check 1: Video stream exists and codec_name is exactly "h264" (or "avc1")
      if (videoStream == null) {
        return const _VerificationOutcome(false, 'Output does not contain a video stream');
      }
      final videoCodec = videoStream.getCodec()?.toLowerCase() ?? '';
      if (videoCodec != 'h264' && videoCodec != 'avc1') {
        return _VerificationOutcome(
          false,
          'BUG 1 FAILED: Video codec is "$videoCodec", expected exactly "h264" (AVC). Non-H.264 codecs like HEVC/VP9 are rejected.',
        );
      }

      // Check 4: r_frame_rate equals avg_frame_rate exactly (true CFR confirmed)
      final rFrameRate = videoStream.getRealFrameRate();
      final avgFrameRate = videoStream.getAverageFrameRate();
      if (rFrameRate == null || avgFrameRate == null) {
        return const _VerificationOutcome(false, 'BUG 4 FAILED: Could not determine video frame rates');
      }
      if (rFrameRate != avgFrameRate) {
        return _VerificationOutcome(
          false,
          'BUG 4 FAILED: Frame rate is not constant (CFR). r_frame_rate ($rFrameRate) != avg_frame_rate ($avgFrameRate).',
        );
      }

      // If video has an audio track, check audio integrity
      if (audioStream != null) {
        // Check 2: Audio codec_name is exactly "aac" (or "mp4a")
        final audioCodec = audioStream.getCodec()?.toLowerCase() ?? '';
        if (audioCodec != 'aac' && audioCodec != 'mp4a') {
          return _VerificationOutcome(
            false,
            'BUG 2/3 FAILED: Audio codec is "$audioCodec", expected exactly "aac".',
          );
        }

        // Check 3: Video and audio stream durations match within ±0.5 seconds
        final videoDurationStr = videoStream.getDuration() ?? fileDurationStr;
        final audioDurationStr = audioStream.getDuration() ?? fileDurationStr;
        final videoDuration = double.tryParse(videoDurationStr ?? '0') ?? fileDuration;
        final audioDuration = double.tryParse(audioDurationStr ?? '0') ?? fileDuration;

        final durationDiff = (videoDuration - audioDuration).abs();
        if (durationDiff > 0.5) {
          return _VerificationOutcome(
            false,
            'BUG 2 FAILED: Audio/Video duration desync. Video duration: ${videoDuration.toStringAsFixed(2)}s, '
            'Audio duration: ${audioDuration.toStringAsFixed(2)}s (diff: ${durationDiff.toStringAsFixed(2)}s > 0.5s). '
            'Indicates audio timescale / sample rate mismatch.',
          );
        }

        // Check 5: Audio channel count is 2 (standard stereo)
        final channels = audioStream.getChannels();
        if (channels != null && channels != 2) {
          return _VerificationOutcome(
            false,
            'BUG 3 FAILED: Audio channel count is $channels, expected 2 (stereo).',
          );
        }

        // Check audio timebase vs sample rate if available
        final sampleRate = audioStream.getSampleRate();
        final timeBase = audioStream.getTimeBase();
        if (sampleRate != null && timeBase != null) {
          final tbParts = timeBase.split('/');
          if (tbParts.length == 2) {
            final denom = double.tryParse(tbParts[1]) ?? 0.0;
            final sRateNum = double.tryParse(sampleRate) ?? 0.0;
            if (sRateNum > 0 && denom > 0 && (denom / sRateNum < 0.8 || denom / sRateNum > 1.2) && denom != 90000) {
              return _VerificationOutcome(
                false,
                'BUG 2 FAILED: Audio timebase ($timeBase) mismatched with sample rate ($sampleRate Hz).',
              );
            }
          }
        }
      }

      return _VerificationOutcome(true, 'OK', durationSeconds: fileDuration);
    } catch (e) {
      return _VerificationOutcome(false, 'Probe exception during validation: $e');
    }
  }
}

class _VerificationOutcome {
  final bool isValid;
  final String reason;
  final double durationSeconds;

  const _VerificationOutcome(this.isValid, this.reason, {this.durationSeconds = 0.0});
}
