package com.secureportal.certificate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws the certificate as a one-page landscape A4 PDF using only built-in fonts. */
@Component
public class CertificatePdfRenderer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final Color ACCENT = new Color(0x0A, 0x7D, 0x6D);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    public byte[] render(Certificate certificate) {
        PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(size);
            doc.addPage(page);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float w = size.getWidth();
            float h = size.getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setStrokingColor(ACCENT);
                cs.setLineWidth(6);
                cs.addRect(28, 28, w - 56, h - 56);
                cs.stroke();
                cs.setLineWidth(1);
                cs.addRect(40, 40, w - 80, h - 80);
                cs.stroke();

                centered(cs, bold, 14, ACCENT, "SECURE CONTENT PORTAL", w, h - 110);
                centered(cs, bold, 40, INK, "Certificate of Completion", w, h - 165);
                centered(cs, regular, 14, MUTED, "This certifies that", w, h - 215);

                String name = fit(certificate.getRecipientName(), bold, 34, w - 160);
                centered(cs, bold, 34, INK, name, w, h - 268);
                cs.setStrokingColor(MUTED);
                cs.setLineWidth(0.5f);
                float nameWidth = Math.min(w - 160, width(bold, 34, name) + 60);
                cs.moveTo((w - nameWidth) / 2, h - 280);
                cs.lineTo((w + nameWidth) / 2, h - 280);
                cs.stroke();

                centered(cs, regular, 14, MUTED, "has successfully completed the course", w, h - 320);
                float y = h - 362;
                for (String line : wrap(certificate.getCourseTitle(), bold, 24, w - 160)) {
                    centered(cs, bold, 24, ACCENT, line, w, y);
                    y -= 30;
                }

                centered(cs, regular, 12, MUTED, "Issued " + DATE.format(certificate.getIssuedAt()), w, 105);
                centered(cs, regular, 10, MUTED,
                        "Certificate ID " + certificate.getCode() + "  -  verify it in the portal at /verify/" + certificate.getCode(),
                        w, 85);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render certificate " + certificate.getId(), e);
        }
    }

    private static void centered(PDPageContentStream cs, PDFont font, float size, Color color, String text,
                                float pageWidth, float y) throws IOException {
        String safe = sanitize(text, font);
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset((pageWidth - width(font, size, safe)) / 2, y);
        cs.showText(safe);
        cs.endText();
    }

    /** Built-in fonts only cover Latin-1; anything else becomes '?' instead of failing the download. */
    static String sanitize(String text, PDFont font) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static float width(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(sanitize(text, font)) / 1000f * size;
    }

    private static String fit(String text, PDFont font, float size, float maxWidth) throws IOException {
        String value = text;
        while (value.length() > 1 && width(font, size, value) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(font, size, candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
    }
}
