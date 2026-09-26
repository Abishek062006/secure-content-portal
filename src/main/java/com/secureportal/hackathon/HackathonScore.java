package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One judge's scores for one submission on four criteria, each 1 to 10. */
@Entity
@Table(name = "hackathon_scores")
public class HackathonScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private Long submissionId;

    @Column(name = "judge_id", nullable = false, updatable = false)
    private Long judgeId;

    @Column(name = "innovation", nullable = false)
    private int innovation;

    @Column(name = "execution", nullable = false)
    private int execution;

    @Column(name = "impact", nullable = false)
    private int impact;

    @Column(name = "presentation", nullable = false)
    private int presentation;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    protected HackathonScore() {
        // for JPA
    }

    public HackathonScore(Long submissionId, Long judgeId, int innovation, int execution, int impact, int presentation, String comment) {
        this.submissionId = submissionId;
        this.judgeId = judgeId;
        set(innovation, execution, impact, presentation, comment);
    }

    public final void set(int innovation, int execution, int impact, int presentation, String comment) {
        this.innovation = innovation;
        this.execution = execution;
        this.impact = impact;
        this.presentation = presentation;
        this.comment = comment;
        this.scoredAt = Instant.now();
    }

    /** The mean of the four criteria, 1 to 10. */
    public double average() {
        return (innovation + execution + impact + presentation) / 4.0;
    }

    public Long getId() {
        return id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getJudgeId() {
        return judgeId;
    }

    public int getInnovation() {
        return innovation;
    }

    public int getExecution() {
        return execution;
    }

    public int getImpact() {
        return impact;
    }

    public int getPresentation() {
        return presentation;
    }

    public String getComment() {
        return comment;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

}
