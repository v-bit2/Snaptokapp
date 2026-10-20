package com.example.service

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native MP4 FastStart (qt-faststart) Utility.
 *
 * Repositions the MP4 'moov' atom before the 'mdat' atom and updates all chunk offsets
 * in 'stco' (32-bit) and 'co64' (64-bit) sample tables.
 *
 * CRITICAL FOR ALIGHT MOTION & CAPCUT COMPATIBILITY:
 * Mobile video editors (especially Alight Motion) use native Android MediaExtractor / AssetReader
 * which read the beginning of the file to discover stream layouts. If the 'moov' box is at the
 * end of the file (standard Android MediaMuxer default), Alight Motion fails to read the media file
 * with "Failed Starting Asset Reader" / "fail to read media file".
 *
 * Relocating 'moov' before 'mdat' allows instant parsing and zero-seeking asset initialization.
 */
object Mp4FastStart {

    private const val TAG = "Mp4FastStart"

    data class AtomInfo(
        val type: String,
        val offset: Long,
        val size: Long,
        val headerSize: Int
    )

    /**
     * Checks if the file already has 'moov' before 'mdat'.
     */
    fun hasFastStart(file: File): Boolean {
        if (!file.exists() || file.length() < 32L) return false
        try {
            RandomAccessFile(file, "r").use { raf ->
                var pos = 0L
                val fileLen = raf.length()
                var moovPos = -1L
                var mdatPos = -1L

                while (pos < fileLen - 8L && (moovPos == -1L || mdatPos == -1L)) {
                    raf.seek(pos)
                    val sizeInt = raf.readInt().toLong() and 0xFFFFFFFFL
                    val typeBytes = ByteArray(4)
                    raf.readFully(typeBytes)
                    val type = String(typeBytes, Charsets.US_ASCII)

                    if (type == "moov") {
                        moovPos = pos
                    } else if (type == "mdat") {
                        mdatPos = pos
                    }

                    if (sizeInt == 1L) {
                        val largeSize = raf.readLong()
                        if (largeSize <= 0) break
                        pos += largeSize
                    } else if (sizeInt == 0L) {
                        break
                    } else {
                        pos += sizeInt
                    }
                }

                if (moovPos != -1L && mdatPos != -1L) {
                    return moovPos < mdatPos
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "hasFastStart check error: ${e.localizedMessage}")
        }
        return false
    }

    /**
     * Applies FastStart to the given MP4 file:
     * Relocates the 'moov' atom before 'mdat' and adjusts chunk offsets by the size of 'moov'.
     *
     * @return true if faststart was applied or already present, false on error.
     */
    fun process(file: File): Boolean {
        if (!file.exists() || file.length() < 64L) {
            Log.w(TAG, "File too small or missing for FastStart: ${file.path}")
            return false
        }

        if (hasFastStart(file)) {
            Log.d(TAG, "File already has FastStart ('moov' before 'mdat'): ${file.name}")
            return true
        }

        val tempOutputFile = File(file.parentFile, "${file.name}.faststart.tmp")

        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                val atoms = mutableListOf<AtomInfo>()
                var pos = 0L

                // 1. Scan top-level atoms
                while (pos < fileLength - 8L) {
                    raf.seek(pos)
                    val sizeInt = raf.readInt().toLong() and 0xFFFFFFFFL
                    val typeBytes = ByteArray(4)
                    raf.readFully(typeBytes)
                    val type = String(typeBytes, Charsets.US_ASCII)

                    var atomSize = sizeInt
                    var headerSize = 8

                    if (sizeInt == 1L) {
                        atomSize = raf.readLong()
                        headerSize = 16
                    } else if (sizeInt == 0L) {
                        atomSize = fileLength - pos
                    }

                    if (atomSize <= 0 || pos + atomSize > fileLength) {
                        break
                    }

                    atoms.add(AtomInfo(type, pos, atomSize, headerSize))
                    pos += atomSize
                }

                val moovAtom = atoms.firstOrNull { it.type == "moov" }
                val mdatAtom = atoms.firstOrNull { it.type == "mdat" }

                if (moovAtom == null || mdatAtom == null) {
                    Log.w(TAG, "Cannot apply FastStart: moov=$moovAtom, mdat=$mdatAtom")
                    return false
                }

                if (moovAtom.offset < mdatAtom.offset) {
                    Log.d(TAG, "moov is already before mdat.")
                    return true
                }

                val moovSize = moovAtom.size
                if (moovSize > 64 * 1024 * 1024L) { // Safety: moov larger than 64MB is unusual for clips
                    Log.w(TAG, "moov atom too large to buffer: $moovSize bytes")
                    return false
                }

                // 2. Read moov atom into memory
                raf.seek(moovAtom.offset)
                val moovBytes = ByteArray(moovSize.toInt())
                raf.readFully(moovBytes)

                // 3. Adjust all stco (32-bit chunk offset) and co64 (64-bit chunk offset) tables
                adjustChunkOffsets(moovBytes, moovSize)

                // 4. Write output file:
                // Atoms preceding mdat (typically 'ftyp'), then the adjusted 'moov', then 'mdat' and rest
                FileOutputStream(tempOutputFile).use { fos ->
                    // Write atoms before mdat
                    for (atom in atoms) {
                        if (atom.type != "moov" && atom.offset < mdatAtom.offset) {
                            copySlice(raf, atom.offset, atom.size, fos)
                        }
                    }

                    // Write adjusted moov box
                    fos.write(moovBytes)

                    // Write mdat box and everything after mdat (except original moov)
                    for (atom in atoms) {
                        if (atom.type != "moov" && atom.offset >= mdatAtom.offset) {
                            copySlice(raf, atom.offset, atom.size, fos)
                        }
                    }

                    fos.flush()
                    fos.fd.sync()
                }
            }

            // Replace original file with faststart file
            if (tempOutputFile.exists() && tempOutputFile.length() > 0L) {
                if (file.delete() && tempOutputFile.renameTo(file)) {
                    Log.i(TAG, "FastStart successfully applied to ${file.name} (moov relocated to front)")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying FastStart: ${e.localizedMessage}", e)
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
        }

        return false
    }

    /**
     * Recursively traverses atoms inside the 'moov' byte buffer and updates chunk offset tables
     * ('stco' and 'co64') by adding offsetShift.
     */
    private fun adjustChunkOffsets(moovBuffer: ByteArray, offsetShift: Long) {
        val byteBuffer = ByteBuffer.wrap(moovBuffer).order(ByteOrder.BIG_ENDIAN)
        val limit = moovBuffer.size

        // Find all occurrences of 'stco' and 'co64'
        var i = 8
        while (i < limit - 8) {
            val fourCc = (moovBuffer[i].toInt() and 0xFF shl 24) or
                    (moovBuffer[i + 1].toInt() and 0xFF shl 16) or
                    (moovBuffer[i + 2].toInt() and 0xFF shl 8) or
                    (moovBuffer[i + 3].toInt() and 0xFF)

            // 'stco' = 0x7374636F
            if (fourCc == 0x7374636F) {
                val atomStart = i - 4
                if (atomStart >= 0 && atomStart + 16 <= limit) {
                    byteBuffer.position(atomStart)
                    val atomSize = byteBuffer.int.toLong() and 0xFFFFFFFFL
                    byteBuffer.position(atomStart + 12) // Skip size (4), 'stco' (4), version+flags (4)
                    val entryCount = byteBuffer.int

                    if (atomStart + 16 + (entryCount * 4) <= limit && entryCount > 0) {
                        for (entry in 0 until entryCount) {
                            val currentPos = byteBuffer.position()
                            val oldOffset = byteBuffer.int.toLong() and 0xFFFFFFFFL
                            val newOffset = oldOffset + offsetShift
                            byteBuffer.position(currentPos)
                            byteBuffer.putInt((newOffset and 0xFFFFFFFFL).toInt())
                        }
                        Log.d(TAG, "Adjusted stco table: $entryCount entries shifted by $offsetShift bytes")
                    }
                }
            }
            // 'co64' = 0x636F3634
            else if (fourCc == 0x636F3634) {
                val atomStart = i - 4
                if (atomStart >= 0 && atomStart + 16 <= limit) {
                    byteBuffer.position(atomStart)
                    val atomSize = byteBuffer.int.toLong() and 0xFFFFFFFFL
                    byteBuffer.position(atomStart + 12)
                    val entryCount = byteBuffer.int

                    if (atomStart + 16 + (entryCount * 8) <= limit && entryCount > 0) {
                        for (entry in 0 until entryCount) {
                            val currentPos = byteBuffer.position()
                            val oldOffset = byteBuffer.long
                            val newOffset = oldOffset + offsetShift
                            byteBuffer.position(currentPos)
                            byteBuffer.putLong(newOffset)
                        }
                        Log.d(TAG, "Adjusted co64 table: $entryCount entries shifted by $offsetShift bytes")
                    }
                }
            }

            i++
        }
    }

    private fun copySlice(raf: RandomAccessFile, offset: Long, length: Long, fos: FileOutputStream) {
        raf.seek(offset)
        var remaining = length
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0L) {
            val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read < 0) break
            fos.write(buffer, 0, read)
            remaining -= read
        }
    }
}
