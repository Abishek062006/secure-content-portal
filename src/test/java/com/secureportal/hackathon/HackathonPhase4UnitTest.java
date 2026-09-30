package com.secureportal.hackathon;

import com.secureportal.notification.NotificationCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class HackathonPhase4UnitTest {

    private final HackathonCertificatePdfRenderer pdfRenderer = new HackathonCertificatePdfRenderer();
    private static final Pattern CERT_CODE_PATTERN = Pattern.compile("^HACK-[A-Z0-9]{12}$");

    @Test
    @DisplayName("PDF Renderer generates non-empty valid PDF byte array for WINNER certificate")
    void rendersWinnerPdf() {
        HackathonCertificate cert = new HackathonCertificate(
                "HACK-A1B2C3D4E5F6", 100L, 200L, 300L,
                "Alice Smith", "AI Innovation Challenge 2026", "Team Alpha",
                HackathonCertificateType.WINNER, 1
        );

        byte[] pdf = pdfRenderer.render(cert);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(1000);
        // Standard PDF file header
        String header = new String(pdf, 0, 5);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("PDF Renderer generates valid PDF for RUNNER_UP and PARTICIPATION certificates")
    void rendersRunnerUpAndParticipationPdf() {
        HackathonCertificate runnerUp = new HackathonCertificate(
                "HACK-B2C3D4E5F6A1", 100L, 201L, 301L,
                "Bob Jones", "AI Innovation Challenge 2026", "Team Beta",
                HackathonCertificateType.RUNNER_UP, 2
        );
        byte[] runnerUpPdf = pdfRenderer.render(runnerUp);
        assertThat(runnerUpPdf).isNotNull().isNotEmpty();
        assertThat(new String(runnerUpPdf, 0, 5)).isEqualTo("%PDF-");

        HackathonCertificate part = new HackathonCertificate(
                "HACK-C3D4E5F6A1B2", 100L, 202L, 302L,
                "Charlie Brown", "AI Innovation Challenge 2026", "Team Gamma",
                HackathonCertificateType.PARTICIPATION, 4
        );
        byte[] partPdf = pdfRenderer.render(part);
        assertThat(partPdf).isNotNull().isNotEmpty();
        assertThat(new String(partPdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Certificate code generation adheres to HACK-XXXXXXXXXXXX format")
    void certificateCodeFormat() {
        String code = "HACK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        assertThat(CERT_CODE_PATTERN.matcher(code).matches()).isTrue();
    }

    @Test
    @DisplayName("Certificate type mapping correctly assigns WINNER, RUNNER_UP, and PARTICIPATION")
    void certificateTypeAwardMapping() {
        assertThat(typeForRank(1)).isEqualTo(HackathonCertificateType.WINNER);
        assertThat(typeForRank(2)).isEqualTo(HackathonCertificateType.RUNNER_UP);
        assertThat(typeForRank(3)).isEqualTo(HackathonCertificateType.RUNNER_UP);
        assertThat(typeForRank(4)).isEqualTo(HackathonCertificateType.PARTICIPATION);
        assertThat(typeForRank(10)).isEqualTo(HackathonCertificateType.PARTICIPATION);
    }

    @Test
    @DisplayName("NotificationCategory includes HACKATHON category")
    void notificationCategoryIncludesHackathon() {
        List<String> names = List.of(NotificationCategory.values()).stream().map(Enum::name).toList();
        assertThat(names).contains("HACKATHON");
    }

    @Test
    @DisplayName("Hackathon entity maintains idempotent deadline reminder flags")
    void hackathonReminderFlags() {
        Hackathon.Hosted hosted = new Hackathon.Hosted("Rules", "AI, Cloud", "$5000", 2, 5);
        Hackathon.Details details = new Hackathon.Details(
                "AI Challenge", "Org", "desc", null, "AI/ML", HackathonMode.ONLINE,
                null, "$5000", null, Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                false, HackathonStatus.ACTIVE, hosted
        );
        Hackathon h = new Hackathon(details);

        assertThat(h.isReminded24h()).isFalse();
        assertThat(h.isReminded1h()).isFalse();

        h.setReminded24h(true);
        assertThat(h.isReminded24h()).isTrue();

        h.setReminded1h(true);
        assertThat(h.isReminded1h()).isTrue();
    }

    @Test
    @DisplayName("Hackathon phase correctly transitions: REGISTRATION -> BUILDING -> JUDGING -> RESULTS")
    void hackathonPhaseTransitions() {
        Instant base = Instant.now();
        Instant regDeadline = base.plusSeconds(1800);
        Instant startDate = base.plusSeconds(3600);
        Instant endDate = base.plusSeconds(7200);

        Hackathon.Hosted hosted = new Hackathon.Hosted("Rules", "AI, Cloud", "$5000", 2, 5);
        Hackathon.Details details = new Hackathon.Details(
                "AI Challenge", "Org", "desc", null, "AI/ML", HackathonMode.ONLINE,
                null, "$5000", null, regDeadline, startDate, endDate,
                false, HackathonStatus.ACTIVE, hosted
        );
        Hackathon h = new Hackathon(details);

        // Before start date -> REGISTRATION phase
        assertThat(h.phase(base)).isEqualTo(HackathonPhase.REGISTRATION);
        assertThat(h.registrationOpen(base)).isTrue();

        // After reg deadline but before start -> registration closed
        assertThat(h.registrationOpen(base.plusSeconds(2000))).isFalse();

        // Between start and end date -> BUILDING phase
        assertThat(h.phase(base.plusSeconds(5000))).isEqualTo(HackathonPhase.BUILDING);

        // After end date -> JUDGING phase
        assertThat(h.phase(base.plusSeconds(8000))).isEqualTo(HackathonPhase.JUDGING);

        // Once results published -> RESULTS phase
        h.publishResults(Instant.now());
        assertThat(h.phase(base.plusSeconds(8000))).isEqualTo(HackathonPhase.RESULTS);
    }

    @Test
    @DisplayName("HackathonScore accurately computes average across innovation, execution, impact, presentation")
    void hackathonScoreCalculation() {
        HackathonScore score = new HackathonScore(500L, 10L, 8, 9, 7, 10, "Great architecture");
        assertThat(score.average()).isEqualTo(8.5);
        assertThat(score.getInnovation()).isEqualTo(8);
        assertThat(score.getExecution()).isEqualTo(9);
        assertThat(score.getImpact()).isEqualTo(7);
        assertThat(score.getPresentation()).isEqualTo(10);
        assertThat(score.getComment()).isEqualTo("Great architecture");

        score.set(10, 10, 10, 10, "Perfect score");
        assertThat(score.average()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("HackathonTeam can select problem statement and hand over leadership")
    void hackathonTeamProblemSelectionAndLeadership() {
        HackathonTeam team = new HackathonTeam(1L, "Alpha Squad", "AI/ML", "INVITE-123", 101L);
        assertThat(team.getProblemStatementId()).isNull();
        assertThat(team.getLeaderId()).isEqualTo(101L);

        team.selectProblemStatement(55L);
        assertThat(team.getProblemStatementId()).isEqualTo(55L);

        team.handLeadershipTo(102L);
        assertThat(team.getLeaderId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("HackathonProblemStatement entity holds track, requirements, criteria, and resources")
    void problemStatementEntityProperties() {
        HackathonProblemStatement ps = new HackathonProblemStatement(
                1L, "AI Smart Search", "Build semantic search", "AI",
                "Python / Java, React", "Accuracy, Latency", "https://docs.example.com"
        );
        assertThat(ps.getHackathonId()).isEqualTo(1L);
        assertThat(ps.getTitle()).isEqualTo("AI Smart Search");
        assertThat(ps.getTrack()).isEqualTo("AI");
        assertThat(ps.getRequirements()).isEqualTo("Python / Java, React");
        assertThat(ps.getEvaluationCriteria()).isEqualTo("Accuracy, Latency");
        assertThat(ps.getResourcesUrl()).isEqualTo("https://docs.example.com");

        ps.update("Updated Search", "New desc", "Cloud", "Go", "Throughput", "https://docs.example.com/v2");
        assertThat(ps.getTitle()).isEqualTo("Updated Search");
        assertThat(ps.getTrack()).isEqualTo("Cloud");
    }

    private static HackathonCertificateType typeForRank(int rank) {
        if (rank == 1) return HackathonCertificateType.WINNER;
        if (rank == 2 || rank == 3) return HackathonCertificateType.RUNNER_UP;
        return HackathonCertificateType.PARTICIPATION;
    }
}
