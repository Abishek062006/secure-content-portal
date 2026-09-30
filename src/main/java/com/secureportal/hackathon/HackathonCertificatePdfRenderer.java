package com.secureportal.hackathon;

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

/** Draws a one-page landscape A4 PDF certificate for hackathon achievements. */
@Component
public class HackathonCertificatePdfRenderer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private static final Color GOLD = new Color(0xB4, 0x53, 0x09);
    private static final Color SILVER = new Color(0x33, 0x41, 0x55);
    private static final Color TEAL = new Color(0x0A, 0x7D, 0x6D);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    public byte[] render(HackathonCertificate cert) {
        PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(size);
            doc.addPage(page);

            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float w = size.getWidth();
            float h = size.getHeight();

            Color accent = switch (cert.getType()) {
                case WINNER -> GOLD;
                case RUNNER_UP -> SILVER;
                case PARTICIPATION -> TEAL;
            };

            String headline = switch (cert.getType()) {
                case WINNER -> "Certificate of Excellence";
                case RUNNER_UP -> "Certificate of Achievement";
                case PARTICIPATION -> "Certificate of Participation";
            };

            String distinction = switch (cert.getType()) {
                case WINNER -> "1ST PLACE WINNER";
                case RUNNER_UP -> "RUNNER UP (TOP 3 FINALIST)";
                case PARTICIPATION -> "OFFICIAL HACKATHON PARTICIPANT";
            };

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Outer double border
                cs.setStrokingColor(accent);
                cs.setLineWidth(6);
                cs.addRect(28, 28, w - 56, h - 56);
                cs.stroke();

                cs.setLineWidth(1);
                cs.addRect(40, 40, w - 80, h - 80);
                cs.stroke();

                // Brand header
                centered(cs, bold, 13, accent, "GRADIENTNOVAAI HACKATHONS", w, h - 105);
                centered(cs, bold, 36, INK, headline, w, h - 155);
                centered(cs, bold, 12, accent, distinction, w, h - 180);

                centered(cs, regular, 14, MUTED, "This certifies that", w, h - 225);

                // Recipient Name
                String name = fit(cert.getRecipientName(), bold, 32, w - 160);
                centered(cs, bold, 32, INK, name, w, h - 270);
                cs.setStrokingColor(MUTED);
                cs.setLineWidth(0.5f);
                float nameWidth = Math.min(w - 160, width(bold, 32, name) + 60);
                cs.moveTo((w - nameWidth) / 2, h - 282);
                cs.lineTo((w + nameWidth) / 2, h - 282);
                cs.stroke();

                // Team info
                String teamLine = (cert.getTeamName() != null && !cert.getTeamName().isBlank())
                        ? "as a distinguished member of Team \"" + cert.getTeamName() + "\""
                        : "as an active participant";
                centered(cs, regular, 13, MUTED, teamLine, w, h - 315);
                centered(cs, regular, 13, MUTED, "has successfully competed in the hosted hackathon", w, h - 340);

                // Hackathon Title
                float y = h - 378;
                for (String line : wrap(cert.getHackathonTitle(), bold, 22, w - 160)) {
                    centered(cs, bold, 22, accent, line, w, y);
                    y -= 26;
                }

                // Footer
                centered(cs, regular, 11, MUTED, "Issued " + DATE.format(cert.getIssuedAt()), w, 105);
                centered(cs, regular, 10, MUTED,
                        "Certificate ID: " + cert.getCode() + "  -  verify at /verify/" + cert.getCode(),
                        w, 85);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render hackathon certificate " + cert.getId(), e);
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

    static String sanitize(String text, PDFont font) {
        if (text == null) return "";
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
        String value = text != null ? text : "";
        while (value.length() > 1 && width(font, size, value) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
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
