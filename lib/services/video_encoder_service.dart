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
  /// - Video Codec: H.264 (libx264)
  /// - Constant Frame Rate (CFR): 30 fps (-r 30 -vsync cfr)
  /// - Audio Codec: AAC (128 kbps stereo)
  /// - Container: MP4 with faststart (+faststart for instant moov atom positioning)
  /// - Preset: fast (balanced mobile CPU performance and quality)
  ///
  /// Features:
  /// 1. Pre-inspection: Skips re-encoding if the video is already H.264 + CFR + AAC.
  /// 2. Asynchronous execution with [onProgress] reporting (0.0 to 1.0).
  /// 3. Post-encode verification (file size, MP4 ftyp header, stream sanity via FFprobe).
  /// 4. Automatic fallback: Returns the original [input] file if encoding or verification fails,
  ///    logging the failure and setting [warningMessage] rather than failing the download.
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
              // Check if r_frame_rate matches avg_frame_rate (indicating CFR)
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

    // Step 4: Build FFmpeg command
    // -y: overwrite output
    // -c:v libx264: H.264 video codec (GPL via x264)
    // -preset fast: balanced speed vs compression
    // -crf 23: visually lossless standard CRF
    // -r 30 -vsync cfr: enforce constant 30 fps
    // -c:a aac -b:a 128k: standard universal AAC audio
    // -movflags +faststart: relocate moov atom to start of file for streaming/editor support
    final command =
        '-y -i "${input.path}" '
        '-c:v libx264 -preset $preset -crf $crf '
        '-r $targetFps -vsync cfr '
        '-c:a aac -b:a 128k '
        '-movflags +faststart '
        '"$outputFilePath"';

    bool encodeSucceeded = false;
    String? failureReason;

    try {
      final session = await FFmpegKit.execute(command);
      final returnCode = await session.getReturnCode();

      if (ReturnCode.isSuccess(returnCode)) {
        // Step 5: Post-encode verification
        final verification = await _verifyOutputFile(outputFile);
        if (verification.isValid) {
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
            durationSeconds: verification.durationSeconds,
          );
        } else {
          failureReason = 'Output verification failed: ${verification.reason}';
        }
      } else {
        final failLog = await session.getFailStackTrace();
        failureReason = 'FFmpeg returned exit code $returnCode. Log: $failLog';
      }
    } catch (e) {
      failureReason = 'Exception during FFmpeg execution: $e';
    } finally {
      FFmpegKitConfig.enableStatisticsCallback(null);
    }

    // Step 6: Graceful Fallback
    // If re-encoding fails, keep the original downloaded file and return it with a warning
    if (!encodeSucceeded) {
      // Clean up corrupt partial output file if generated
      try {
        if (await outputFile.exists()) {
          await outputFile.delete();
        }
      } catch (_) {}

      return VideoEncodeResult(
        file: input,
        wasReencoded: false,
        wasSkippedAlreadyCompatible: false,
        warningMessage: 'Saved, but may not be compatible with all editing apps. ($failureReason)',
        durationSeconds: totalDurationSeconds,
      );
    }

    return VideoEncodeResult(
      file: input,
      wasReencoded: false,
      wasSkippedAlreadyCompatible: false,
    );
  }

  /// Verifies that [file] exists, is non-empty, possesses an MP4 ftyp header,
  /// and probes as a valid video stream with non-zero duration.
  Future<_VerificationOutcome> _verifyOutputFile(File file) async {
    if (!await file.exists()) {
      return _VerificationOutcome(false, 'Output file was not created');
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
        return _VerificationOutcome(false, 'Missing MP4 ftyp signature box in header');
      }
    } catch (e) {
      return _VerificationOutcome(false, 'Failed reading header: $e');
    }

    // FFprobe verification for stream and duration integrity
    try {
      final probe = await FFprobeKit.getMediaInformation(file.path);
      final info = probe.getMediaInformation();
      if (info == null) {
        return _VerificationOutcome(false, 'FFprobe could not parse media info');
      }

      final duration = double.tryParse(info.getDuration() ?? '0') ?? 0.0;
      if (duration <= 0.0) {
        return _VerificationOutcome(false, 'Output reports zero or negative duration ($duration)');
      }

      bool hasVideo = false;
      for (final s in info.getStreams()) {
        if (s.getType()?.toLowerCase() == 'video') {
          hasVideo = true;
          break;
        }
      }

      if (!hasVideo) {
        return _VerificationOutcome(false, 'Output does not contain a video stream');
      }

      return _VerificationOutcome(true, 'OK', durationSeconds: duration);
    } catch (e) {
      return _VerificationOutcome(false, 'Probe exception: $e');
    }
  }
}

class _VerificationOutcome {
  final bool isValid;
  final String reason;
  final double durationSeconds;

  const _VerificationOutcome(this.isValid, this.reason, {this.durationSeconds = 0.0});
}
