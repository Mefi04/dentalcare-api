package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.PayloadTooLargeException;
import com.dentalcare.api.exception.UnprocessableEntityException;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.springframework.web.multipart.MultipartFile;

/** Bounded structural checks; this is deliberately not an antimalware scanner. */
final class ClinicalDocumentStructureValidator {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final Pattern PDF_VERSION = Pattern.compile("%PDF-[1-2]\\.[0-9]");
    private static final Pattern PDF_OBJECT = Pattern.compile("(?:^|\\s)\\d+\\s+\\d+\\s+obj(?:\\s|$)");
    private static final Pattern PDF_END_OBJECT = Pattern.compile("(?:^|\\s)endobj(?:\\s|$)");
    private static final Pattern PDF_CATALOG = Pattern.compile("/Type\\s*/Catalog(?:\\s|<|\\[|/)");
    private static final Pattern PDF_START_XREF = Pattern.compile("startxref\\s+(\\d+)\\s+%%EOF\\s*$", Pattern.DOTALL);
    private static final int PDF_TAIL_SIZE = 65_536;
    private static final int PDF_OVERLAP = 256;

    private final MultipartFile file;
    private final long maximum;
    private final String maximumLabel;

    private ClinicalDocumentStructureValidator(MultipartFile file, long maximum, String maximumLabel) {
        this.file = file;
        this.maximum = maximum;
        this.maximumLabel = maximumLabel;
    }

    static long validate(MultipartFile file, String contentType, long maximum, String maximumLabel) {
        ClinicalDocumentStructureValidator validator =
                new ClinicalDocumentStructureValidator(file, maximum, maximumLabel);
        return switch (contentType) {
            case "application/pdf" -> validator.pdf();
            case "image/jpeg" -> validator.jpeg();
            case "image/png" -> validator.png();
            default -> throw invalid(contentType);
        };
    }

    private long pdf() {
        byte[] header = new byte[16];
        byte[] tail = new byte[PDF_TAIL_SIZE];
        int headerLength = 0;
        int tailLength = 0;
        long total = 0;
        boolean object = false;
        boolean endObject = false;
        boolean catalog = false;
        byte[] overlap = new byte[0];
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) continue;
                total = checked(total, read);
                if (headerLength < header.length) {
                    int copied = Math.min(header.length - headerLength, read);
                    System.arraycopy(buffer, 0, header, headerLength, copied);
                    headerLength += copied;
                }
                tailLength = appendTail(tail, tailLength, buffer, read);
                byte[] scan = new byte[overlap.length + read];
                System.arraycopy(overlap, 0, scan, 0, overlap.length);
                System.arraycopy(buffer, 0, scan, overlap.length, read);
                String text = new String(scan, StandardCharsets.ISO_8859_1);
                object |= PDF_OBJECT.matcher(text).find();
                endObject |= PDF_END_OBJECT.matcher(text).find();
                catalog |= PDF_CATALOG.matcher(text).find();
                overlap = Arrays.copyOfRange(scan, Math.max(0, scan.length - PDF_OVERLAP), scan.length);
            }
        } catch (PayloadTooLargeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unreadable(exception);
        }

        String headerText = new String(header, 0, headerLength, StandardCharsets.ISO_8859_1);
        String tailText = new String(tail, 0, tailLength, StandardCharsets.ISO_8859_1);
        Matcher startXref = PDF_START_XREF.matcher(tailText);
        if (!PDF_VERSION.matcher(headerText).lookingAt() || !object || !endObject || !catalog || !startXref.find())
            throw invalid("PDF");
        long offset;
        try {
            offset = Long.parseLong(startXref.group(1));
        } catch (NumberFormatException exception) {
            throw invalid("PDF");
        }
        if (offset < 5 || offset >= total || !validPdfReference(offset)) throw invalid("PDF");
        return total;
    }

    private boolean validPdfReference(long offset) {
        try (InputStream input = file.getInputStream()) {
            input.skipNBytes(offset);
            String target = new String(input.readNBytes(4096), StandardCharsets.ISO_8859_1);
            if (target.startsWith("xref"))
                return target.contains("trailer") && target.matches("(?s).*?/Root\\s+\\d+\\s+\\d+\\s+R.*");
            return PDF_OBJECT.matcher(target).lookingAt()
                    && target.matches("(?s).*?/Type\\s*/XRef(?:\\s|<|\\[|/).*")
                    && target.matches("(?s).*?/Root\\s+\\d+\\s+\\d+\\s+R.*");
        } catch (IOException exception) {
            throw unreadable(exception);
        }
    }

    private long jpeg() {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(file.getInputStream()))) {
            if (input.readUnsignedByte() != 0xff || input.readUnsignedByte() != 0xd8) throw invalid("JPEG");
            long total = 2;
            boolean frame = false;
            boolean scan = false;
            boolean entropy = false;
            while (true) {
                int prefix = input.readUnsignedByte();
                total = checked(total, 1);
                if (entropy && prefix != 0xff) continue;
                if (prefix != 0xff) throw invalid("JPEG");
                int marker;
                do {
                    marker = input.readUnsignedByte();
                    total = checked(total, 1);
                } while (marker == 0xff);
                if (entropy && marker == 0x00) continue;
                if (marker == 0xd9) {
                    if (!frame || !scan || input.read() != -1) throw invalid("JPEG");
                    return total;
                }
                if (marker >= 0xd0 && marker <= 0xd7) {
                    if (!entropy) throw invalid("JPEG");
                    continue;
                }
                if (marker == 0xd8 || marker == 0x01) throw invalid("JPEG");
                int length = input.readUnsignedShort();
                total = checked(total, 2);
                if (length < 2) throw invalid("JPEG");
                int payload = length - 2;
                if (isStartOfFrame(marker)) {
                    if (payload < 6) throw invalid("JPEG");
                    byte[] descriptor = input.readNBytes(payload);
                    int components = descriptor.length > 5 ? descriptor[5] & 0xff : 0;
                    if (descriptor.length != payload || (descriptor[0] & 0xff) != 8
                            || unsignedShort(descriptor, 1) == 0 || unsignedShort(descriptor, 3) == 0
                            || components == 0 || payload != 6 + 3 * components)
                        throw invalid("JPEG");
                    frame = true;
                } else if (marker == 0xda) {
                    byte[] descriptor = input.readNBytes(payload);
                    int components = descriptor.length > 0 ? descriptor[0] & 0xff : 0;
                    if (!frame || descriptor.length != payload || components == 0
                            || payload != 1 + 2 * components + 3) throw invalid("JPEG");
                } else {
                    input.skipNBytes(payload);
                }
                total = checked(total, payload);
                if (marker == 0xda) {
                    scan = true;
                    entropy = true;
                } else if (entropy) {
                    entropy = false;
                }
            }
        } catch (EOFException exception) {
            throw invalid("JPEG");
        } catch (PayloadTooLargeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unreadable(exception);
        }
    }

    private long png() {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(file.getInputStream()))) {
            byte[] signature = input.readNBytes(PNG_SIGNATURE.length);
            if (!Arrays.equals(signature, PNG_SIGNATURE)) throw invalid("PNG");
            long total = signature.length;
            boolean first = true;
            boolean sawIdat = false;
            while (true) {
                long length = Integer.toUnsignedLong(input.readInt());
                byte[] type = input.readNBytes(4);
                total = checked(total, 8);
                if (type.length != 4 || length > maximum - total - 4) throw invalid("PNG");
                String name = new String(type, StandardCharsets.US_ASCII);
                if (!name.matches("[A-Za-z]{4}") || first && (!"IHDR".equals(name) || length != 13)
                        || !first && "IHDR".equals(name)) throw invalid("PNG");
                CRC32 crc = new CRC32();
                crc.update(type);
                long remaining = length;
                byte[] buffer = new byte[8192];
                byte[] ihdr = "IHDR".equals(name) ? new byte[13] : null;
                int ihdrOffset = 0;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) throw invalid("PNG");
                    crc.update(buffer, 0, read);
                    if (ihdr != null) {
                        System.arraycopy(buffer, 0, ihdr, ihdrOffset, read);
                        ihdrOffset += read;
                    }
                    remaining -= read;
                }
                long declaredCrc = Integer.toUnsignedLong(input.readInt());
                total = checked(total, length + 4);
                if (crc.getValue() != declaredCrc) throw invalid("PNG");
                if (ihdr != null && !validIhdr(ihdr)) throw invalid("PNG");
                sawIdat |= "IDAT".equals(name);
                first = false;
                if ("IEND".equals(name)) {
                    if (length != 0 || !sawIdat || input.read() != -1) throw invalid("PNG");
                    return total;
                }
            }
        } catch (EOFException exception) {
            throw invalid("PNG");
        } catch (PayloadTooLargeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unreadable(exception);
        }
    }

    private long checked(long current, long added) {
        long result = current + added;
        if (added < 0 || result < current || result > maximum)
            throw new PayloadTooLargeException("File size exceeds maximum allowed limit of " + maximumLabel);
        return result;
    }

    private static int appendTail(byte[] tail, int current, byte[] source, int length) {
        if (length >= tail.length) {
            System.arraycopy(source, length - tail.length, tail, 0, tail.length);
            return tail.length;
        }
        int keep = Math.min(current, tail.length - length);
        if (keep > 0) System.arraycopy(tail, current - keep, tail, 0, keep);
        System.arraycopy(source, 0, tail, keep, length);
        return keep + length;
    }

    private static int unsignedShort(byte[] value, int offset) {
        return (value[offset] & 0xff) << 8 | value[offset + 1] & 0xff;
    }

    private static boolean validIhdr(byte[] value) {
        long width = Integer.toUnsignedLong((value[0] & 0xff) << 24 | (value[1] & 0xff) << 16
                | (value[2] & 0xff) << 8 | value[3] & 0xff);
        long height = Integer.toUnsignedLong((value[4] & 0xff) << 24 | (value[5] & 0xff) << 16
                | (value[6] & 0xff) << 8 | value[7] & 0xff);
        int depth = value[8] & 0xff;
        int color = value[9] & 0xff;
        boolean validDepth = switch (color) {
            case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
            case 2, 4, 6 -> depth == 8 || depth == 16;
            case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
            default -> false;
        };
        return width > 0 && height > 0 && validDepth && value[10] == 0 && value[11] == 0
                && (value[12] == 0 || value[12] == 1);
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
    }

    private static UnprocessableEntityException unreadable(IOException cause) {
        return new UnprocessableEntityException("Failed to inspect uploaded file content", cause);
    }

    private static UnprocessableEntityException invalid(String format) {
        return new UnprocessableEntityException("File content does not match " + format + " format or is truncated");
    }
}
