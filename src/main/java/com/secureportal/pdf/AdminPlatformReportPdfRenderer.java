package com.secureportal.pdf;

import com.secureportal.analytics.PlatformAnalytics;
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

/** One-page A4 snapshot of {@link PlatformAnalytics} for admins, drawn with PDFBox built-in fonts —
 *  same approach as {@link com.secureportal.certificate.CertificatePdfRenderer} and
 *  {@link AcademicTranscriptPdfRenderer}. */
@Component
public class AdminPlatformReportPdfRenderer {

    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();
    private static final float LM = 40f;
    private static final float TW = PW - 2 * LM;

    private static final Color TEAL = new Color(0x0F, 0x4C, 0x75);
    private static final Color TEAL_MID = new Color(0x1B, 0x6C, 0xA8);
    private static final Color WHITE = Color.WHITE;
    private static final Color INK = new Color(0x1E, 0x29, 0x3B);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color ROW_ALT = new Color(0xF0, 0xF7, 0xFF);
    private static final Color ROW_LINE = new Color(0xBF, 0xD7, 0xEA);
    private static final Color GREEN = new Color(0x14, 0x53, 0x2D);
    private static final Color STAT_BG = new Color(0xE8, 0xF3, 0xFD);

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private PDFont regular;
    private PDFont bold;

    public byte[] render(PlatformAnalytics analytics) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = renderHeader(cs);
                y = renderKpiGrid(cs, analytics, y);
                y = renderTopCoursesTable(cs, analytics.topCourses(), y);
                renderContentStats(cs, analytics, y);
                renderFooter(cs);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Admin report render failed", e);
        }
    }

    private float renderHeader(PDPageContentStream cs) throws IOException {
        float bH = 80f;
        float bY = PH - bH;

        cs.setStrokingColor(TEAL);
        cs.setLineWidth(1.5f);
        cs.addRect(LM - 4, LM - 4, TW + 8, PH - 2 * LM + 8);
        cs.stroke();

        fillRect(cs, TEAL, LM - 4, bY, TW + 8, bH);
        txt(cs, bold, 15, WHITE, "GRADIENTNOVAAI — ADMIN PLATFORM REPORT", LM + 10, bY + 50);
        txt(cs, bold, 10, new Color(0xAD, 0xD8, 0xE6), "CONFIDENTIAL: For administrator use only", LM + 10, bY + 28);
        txtRight(cs, regular, 8.5f, new Color(0xAD, 0xD8, 0xE6), "Generated: " + DATE_TIME.format(Instant.now()), PW - LM - 4, bY + 28);

        return bY - 22;
    }

    private float renderKpiGrid(PDPageContentStream cs, PlatformAnalytics a, float y) throws IOException {
        sectionHeading(cs, "PLATFORM OVERVIEW — KEY METRICS", y);
        y -= 28;

        PlatformAnalytics.Totals t = a.totals();
        PlatformAnalytics.Funnel f = a.funnel();
        PlatformAnalytics.Quiz q = a.quiz();

        float cardW = (TW - 9f) / 4f;
        float cardH = 52f;
        float gap = 3f;
        float[] col = {LM, LM + cardW + gap, LM + 2 * (cardW + gap), LM + 3 * (cardW + gap)};

        statCard(cs, "Total Learners", String.valueOf(t.learners()), "+" + t.newLearners30() + " last 30d", col[0], y - cardH, cardW, cardH);
        statCard(cs, "Total Enrollments", String.valueOf(t.enrollments()), "+" + t.enrollments30() + " last 30d", col[1], y - cardH, cardW, cardH);
        statCard(cs, "Published Courses", String.valueOf(t.publishedCourses()), t.draftCourses() + " drafts", col[2], y - cardH, cardW, cardH);
        statCard(cs, "Certificates", String.valueOf(t.certificates()), "+" + t.certificates30() + " last 30d", col[3], y - cardH, cardW, cardH);
        y -= cardH + gap;

        String avgScore = q.averageScore() != null ? String.format("%.1f%%", q.averageScore()) : "N/A";
        String passRate = q.passRate() != null ? String.format("%.1f%%", q.passRate() * 100) : "N/A";
        statCard(cs, "Active (7 days)", String.valueOf(t.activeLearners7()), "unique learners", col[0], y - cardH, cardW, cardH);
        statCard(cs, "Course Completions", String.valueOf(f.completed()), f.certified() + " certified", col[1], y - cardH, cardW, cardH);
        statCard(cs, "Quiz Attempts", String.valueOf(q.attempts()), "avg score: " + avgScore, col[2], y - cardH, cardW, cardH);
        statCard(cs, "Quiz Pass Rate", passRate, q.attempts30() + " last 30d", col[3], y - cardH, cardW, cardH);
        y -= cardH + 22;

        return y;
    }

    private void statCard(PDPageContentStream cs, String label, String value, String sub, float x, float y, float w, float h) throws IOException {
        fillRect(cs, STAT_BG, x, y, w, h);
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.5f);
        cs.addRect(x, y, w, h);
        cs.stroke();
        fillRect(cs, TEAL_MID, x, y, 3.5f, h);

        txt(cs, regular, 7.5f, MUTED, label, x + 9, y + h - 14);
        txt(cs, bold, 17, TEAL, value, x + 9, y + h - 33);
        txt(cs, regular, 7.5f, MUTED, sub, x + 9, y + 8);
    }

    private float renderTopCoursesTable(PDPageContentStream cs, List<PlatformAnalytics.CourseRow> courses, float y) throws IOException {
        sectionHeading(cs, "TOP COURSES BY ENROLLMENT", y);
        y -= 26;

        float rH = 18f;
        float xT = LM, xCat = LM + 188, xE = LM + 310, xComp = LM + 380, xCert = LM + 450;

        fillRect(cs, TEAL_MID, LM, y - rH, TW, rH);
        colHeader(cs, "Course Title", xT + 4, y - 12);
        colHeader(cs, "Category", xCat + 4, y - 12);
        colHeader(cs, "Enrolled", xE + 2, y - 12);
        colHeader(cs, "Completed", xComp + 2, y - 12);
        colHeader(cs, "Certified", xCert + 2, y - 12);
        y -= rH;

        if (courses.isEmpty()) {
            txt(cs, regular, 9, MUTED, "No course data available.", xT + 4, y - 12);
            return y - rH - 18;
        }

        int n = 0;
        for (PlatformAnalytics.CourseRow c : courses) {
            if (n >= 10) break;
            if (n % 2 == 0) fillRect(cs, ROW_ALT, LM, y - rH, TW, rH);
            rowLine(cs, y);

            txt(cs, bold, 8, TEAL_MID, "#" + (n + 1), xT + 2, y - 12);
            txt(cs, regular, 9, INK, fit(c.title(), 175), xT + 20, y - 12);
            txt(cs, regular, 9, MUTED, fit(c.category() != null ? c.category() : "--", 115), xCat + 4, y - 12);
            txt(cs, regular, 9, INK, String.valueOf(c.enrollments()), xE + 2, y - 12);
            txt(cs, regular, 9, GREEN, String.valueOf(c.completed()), xComp + 2, y - 12);
            txt(cs, regular, 9, TEAL_MID, String.valueOf(c.certificates()), xCert + 2, y - 12);
            y -= rH;
            n++;
        }
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.5f);
        cs.moveTo(LM, y);
        cs.lineTo(LM + TW, y);
        cs.stroke();
        return y - 20;
    }

    private void renderContentStats(PDPageContentStream cs, PlatformAnalytics a, float y) throws IOException {
        sectionHeading(cs, "CONTENT & COMMUNITY STATS", y);
        y -= 28;

        PlatformAnalytics.Content content = a.content();
        PlatformAnalytics.Community community = a.community();
        PlatformAnalytics.Funnel funnel = a.funnel();

        float cardW = (TW - 6f) / 3f;
        float cardH = 54f;
        float gap = 3f;
        float[] xs = {LM, LM + cardW + gap, LM + 2 * (cardW + gap)};

        statCard(cs, "Total Lessons", String.valueOf(content.lessons()), content.materials() + " materials", xs[0], y - cardH, cardW, cardH);
        statCard(cs, "Quiz Questions", String.valueOf(content.questions()), content.aiQuestions() + " AI-generated", xs[1], y - cardH, cardW, cardH);
        statCard(cs, "Community Posts", String.valueOf(community.posts()), "+" + community.posts30() + " last 30d", xs[2], y - cardH, cardW, cardH);
        y -= cardH + gap;

        statCard(cs, "Learners Started", String.valueOf(funnel.started()), "of " + funnel.enrolled() + " enrolled", xs[0], y - cardH, cardW, cardH);
        statCard(cs, "Fully Completed", String.valueOf(funnel.completed()), "courses finished", xs[1], y - cardH, cardW, cardH);
        statCard(cs, "Comments & Reactions", String.valueOf(community.comments() + community.reactions()), community.comments() + " comments", xs[2], y - cardH, cardW, cardH);
    }

    private void renderFooter(PDPageContentStream cs) throws IOException {
        float fy = LM + 16;
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.75f);
        cs.moveTo(LM, fy + 12);
        cs.lineTo(LM + TW, fy + 12);
        cs.stroke();
        txt(cs, regular, 7.5f, MUTED, "This report is confidential and intended for GradientNovaAI administrators only.", LM, fy);
        txtRight(cs, bold, 7.5f, TEAL, "ADMIN PLATFORM REPORT", LM + TW, fy);
    }

    private void sectionHeading(PDPageContentStream cs, String label, float y) throws IOException {
        fillRect(cs, TEAL, LM, y - 16, 3.5f, 16);
        txt(cs, bold, 10.5f, TEAL, label, LM + 8, y - 12);
    }

    private void colHeader(PDPageContentStream cs, String text, float x, float y) throws IOException {
        txt(cs, bold, 8.5f, WHITE, text, x, y);
    }

    private void rowLine(PDPageContentStream cs, float y) throws IOException {
        cs.setStrokingColor(ROW_LINE);
        cs.setLineWidth(0.35f);
        cs.moveTo(LM, y);
        cs.lineTo(LM + TW, y);
        cs.stroke();
    }

    private void fillRect(PDPageContentStream cs, Color c, float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
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
}
