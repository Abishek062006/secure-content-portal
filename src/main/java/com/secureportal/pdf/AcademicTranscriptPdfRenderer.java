package com.secureportal.pdf;

import com.secureportal.certificate.Certificate;
import com.secureportal.user.User;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Draws a one-page A4 "academic transcript" — enrollments, progress, certificates and badges — using
 *  only PDFBox built-in fonts, the same approach as {@link com.secureportal.certificate.CertificatePdfRenderer}. */
@Component
public class AcademicTranscriptPdfRenderer {

    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();
    private static final float TOP = PH - 28f;
    private static final float BOT = 28f;
    private static final float LEFT = 30f;
    private static final float TW = PW - 2 * LEFT;

    private static final Color NAVY = new Color(0x1A, 0x27, 0x4E);
    private static final Color NAVY_MID = new Color(0x2D, 0x3E, 0x72);
    private static final Color WHITE = Color.WHITE;
    private static final Color INK = new Color(0x1E, 0x29, 0x3B);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color ROW_ALT = new Color(0xF1, 0xF5, 0xF9);
    private static final Color ROW_LINE = new Color(0xCB, 0xD5, 0xE1);
    private static final Color GREEN_DARK = new Color(0x14, 0x53, 0x2D);
    private static final Color AMBER_DARK = new Color(0x78, 0x35, 0x09);
    private static final Color CODE_BLUE = new Color(0x1D, 0x4E, 0xD8);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public record CourseProgressSummary(String courseTitle, String category, Instant enrolledAt,
                                        int progressPercent, boolean completed) {
    }

    public record BadgeRecord(String badgeTitle, String category, Instant earnedAt) {
    }

    private PDFont regular;
    private PDFont bold;

    public byte[] render(User user, int totalXp, int streakDays, List<CourseProgressSummary> courses,
                         List<Certificate> certs, List<BadgeRecord> badges) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = renderHeader(cs);
                y = renderProfileBlock(cs, user, totalXp, streakDays, certs.size(), y);
                y = renderCourseTable(cs, courses, y);
                renderCredentialsTable(cs, certs, badges, y);
                renderFooter(cs);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Transcript render failed", e);
        }
    }

    private float renderHeader(PDPageContentStream cs) throws IOException {
        float bH = 78f;
        float bY = TOP - bH;

        cs.setStrokingColor(NAVY);
        cs.setLineWidth(1.5f);
        cs.addRect(LEFT, BOT, TW, TOP - BOT);
        cs.stroke();

        fillRect(cs, NAVY, LEFT, bY, TW, bH);
        txt(cs, bold, 15, WHITE, "GRADIENTNOVAAI LEARNING PLATFORM", LEFT + 12, bY + 50);
        txt(cs, bold, 10, new Color(0xBF, 0xDB, 0xFF), "OFFICIAL ACADEMIC LEARNING TRANSCRIPT", LEFT + 12, bY + 28);
        txtRight(cs, regular, 8.5f, new Color(0xBF, 0xDB, 0xFF), "Issued: " + DATE.format(Instant.now()), LEFT + TW - 12, bY + 28);

        return bY - 18;
    }

    private float renderProfileBlock(PDPageContentStream cs, User user, int xp, int streak, int certCount, float y) throws IOException {
        sectionHeading(cs, "LEARNER PROFILE", y);
        y -= 26;

        float bH = 58f;
        fillRect(cs, ROW_ALT, LEFT, y - bH, TW, bH);
        borderRect(cs, ROW_LINE, LEFT, y - bH, TW, bH);

        float c1 = LEFT + 10, c2 = LEFT + 195, c3 = LEFT + 380;

        float r1 = y - 19;
        kv(cs, "Name:", safe(user.getDisplayName()), c1, r1);
        kv(cs, "Email:", safe(user.getEmail()), c2, r1);
        kv(cs, "Role:", user.getRole().name(), c3, r1);

        float r2 = y - 41;
        txt(cs, bold, 9, INK, "Total XP:", c1, r2);
        txt(cs, regular, 9, NAVY_MID, xp + " XP", c1 + sw(bold, 9, "Total XP:") + 3, r2);
        kv(cs, "Streak:", streak + " Days", c2, r2);
        kv(cs, "Certificates:", certCount + "", c3, r2);

        return y - bH - 22;
    }

    private float renderCourseTable(PDPageContentStream cs, List<CourseProgressSummary> courses, float y) throws IOException {
        sectionHeading(cs, "COURSE ENROLLMENTS & PERFORMANCE", y);
        y -= 26;

        float rH = 18f;
        float xT = LEFT, xC = LEFT + 186, xD = LEFT + 313, xPg = LEFT + 419, xSt = LEFT + 463;
        float wT = 184f, wC = 125f;

        fillRect(cs, NAVY_MID, LEFT, y - rH, TW, rH);
        colHeader(cs, "Course Title", xT + 5, y - 12);
        colHeader(cs, "Category", xC + 5, y - 12);
        colHeader(cs, "Enrolled Date", xD + 5, y - 12);
        colHeader(cs, "Progress", xPg + 3, y - 12);
        colHeader(cs, "Status", xSt + 5, y - 12);
        y -= rH;

        if (courses.isEmpty()) {
            txt(cs, regular, 9, MUTED, "No course enrollments recorded yet.", xT + 4, y - 12);
            return y - rH - 18;
        }

        int n = 0;
        for (CourseProgressSummary c : courses) {
            if (n >= 11) break;
            if (n % 2 == 0) fillRect(cs, ROW_ALT, LEFT, y - rH, TW, rH);
            rowLine(cs, y);

            txt(cs, regular, 9, INK, fit(c.courseTitle(), wT - 8), xT + 5, y - 12);
            txt(cs, regular, 9, MUTED, fit(c.category() != null ? c.category() : "General", wC - 8), xC + 5, y - 12);
            txt(cs, regular, 9, MUTED, c.enrolledAt() != null ? DATE.format(c.enrolledAt()) : "--", xD + 5, y - 12);
            txt(cs, regular, 9, INK, c.progressPercent() + "%", xPg + 3, y - 12);

            boolean done = c.completed();
            txt(cs, bold, 9, done ? GREEN_DARK : AMBER_DARK, done ? "Completed" : "In Progress", xSt + 5, y - 12);
            y -= rH;
            n++;
        }
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.5f);
        cs.moveTo(LEFT, y);
        cs.lineTo(LEFT + TW, y);
        cs.stroke();

        return y - 20;
    }

    private void renderCredentialsTable(PDPageContentStream cs, List<Certificate> certs, List<BadgeRecord> badges, float y) throws IOException {
        sectionHeading(cs, "VERIFIED CERTIFICATES & BADGES", y);
        y -= 26;

        float rH = 18f;
        float xT = LEFT, xD = LEFT + 298, xC = LEFT + 420;

        fillRect(cs, NAVY_MID, LEFT, y - rH, TW, rH);
        colHeader(cs, "Certificate / Badge Title", xT + 5, y - 12);
        colHeader(cs, "Issued / Earned Date", xD + 5, y - 12);
        colHeader(cs, "Credential Code", xC + 5, y - 12);
        y -= rH;

        if (certs.isEmpty() && badges.isEmpty()) {
            txt(cs, regular, 9, MUTED, "No certificates or badges earned yet.", xT + 5, y - 12);
            return;
        }

        int n = 0;
        for (Certificate cert : certs) {
            if (n >= 5) break;
            if (n % 2 == 0) fillRect(cs, ROW_ALT, LEFT, y - rH, TW, rH);
            rowLine(cs, y);
            txt(cs, regular, 9, INK, fit(cert.getCourseTitle(), 280), xT + 5, y - 12);
            txt(cs, regular, 9, MUTED, DATE.format(cert.getIssuedAt()), xD + 5, y - 12);
            txt(cs, bold, 9, CODE_BLUE, cert.getCode(), xC + 5, y - 12);
            y -= rH;
            n++;
        }
        for (BadgeRecord b : badges) {
            if (n >= 9) break;
            if (n % 2 == 0) fillRect(cs, ROW_ALT, LEFT, y - rH, TW, rH);
            rowLine(cs, y);
            txt(cs, regular, 9, INK, "Badge: " + fit(b.badgeTitle(), 265), xT + 5, y - 12);
            txt(cs, regular, 9, MUTED, b.earnedAt() != null ? DATE.format(b.earnedAt()) : "--", xD + 5, y - 12);
            txt(cs, regular, 9, MUTED, b.category() != null ? b.category() : "", xC + 5, y - 12);
            y -= rH;
            n++;
        }
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.5f);
        cs.moveTo(LEFT, y);
        cs.lineTo(LEFT + TW, y);
        cs.stroke();
    }

    private void renderFooter(PDPageContentStream cs) throws IOException {
        float fy = BOT + 12;
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.75f);
        cs.moveTo(LEFT, fy + 12);
        cs.lineTo(LEFT + TW, fy + 12);
        cs.stroke();
        txt(cs, regular, 7.5f, MUTED, "Auto-generated official academic transcript — GradientNovaAI Learning Platform.", LEFT, fy);
        txtRight(cs, bold, 7.5f, NAVY, "VERIFIED ACADEMIC RECORD", LEFT + TW, fy);
    }

    private void sectionHeading(PDPageContentStream cs, String label, float y) throws IOException {
        fillRect(cs, NAVY, LEFT, y - 16, 3.5f, 16);
        txt(cs, bold, 10.5f, NAVY, label, LEFT + 8, y - 12);
    }

    private void colHeader(PDPageContentStream cs, String text, float x, float y) throws IOException {
        txt(cs, bold, 8.5f, WHITE, text, x, y);
    }

    private void kv(PDPageContentStream cs, String key, String val, float x, float y) throws IOException {
        txt(cs, bold, 9, INK, key, x, y);
        txt(cs, regular, 9, INK, " " + val, x + sw(bold, 9, key), y);
    }

    private void rowLine(PDPageContentStream cs, float y) throws IOException {
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.35f);
        cs.moveTo(LEFT, y);
        cs.lineTo(LEFT + TW, y);
        cs.stroke();
    }

    private void fillRect(PDPageContentStream cs, Color c, float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void borderRect(PDPageContentStream cs, Color c, float x, float y, float w, float h) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(0.5f);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void txt(PDPageContentStream cs, PDFont f, float sz, Color col, String raw, float x, float y) throws IOException {
        String s = sanitize(raw, f);
        if (s.isEmpty()) return;
        cs.beginText();
        cs.setFont(f, sz);
        cs.setNonStrokingColor(col);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private void txtRight(PDPageContentStream cs, PDFont f, float sz, Color col, String raw, float rx, float y) throws IOException {
        String s = sanitize(raw, f);
        if (s.isEmpty()) return;
        txt(cs, f, sz, col, s, rx - sw(f, sz, s), y);
    }

    private float sw(PDFont f, float sz, String t) {
        try {
            return f.getStringWidth(sanitize(t, f)) / 1000f * sz;
        } catch (IOException e) {
            return 0;
        }
    }

    private String fit(String text, float maxPts) {
        if (text == null) return "";
        String v = text;
        while (v.length() > 3 && sw(regular, 9, v) > maxPts) v = v.substring(0, v.length() - 1);
        return (v.length() < text.length()) ? v.substring(0, Math.max(1, v.length() - 1)) + "..." : v;
    }

    private static String sanitize(String text, PDFont font) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (Exception ignored) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
