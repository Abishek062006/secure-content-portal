package com.secureportal.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "attempt_questions")
public class AttemptQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(nullable = false)
    private int position;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    /** Original option positions in the order this learner sees them, e.g. "2,0,3,1". */
    @Column(name = "option_order", nullable = false, length = 16)
    private String optionOrder;

    /** The ORIGINAL position of the option the learner picked, not its position on screen. */
    @Column(name = "selected_index")
    private Integer selectedIndex;

    private Boolean correct;

    protected AttemptQuestion() {
        // for JPA
    }

    public AttemptQuestion(UUID attemptId, int position, UUID questionId, String optionOrder) {
        this.attemptId = attemptId;
        this.position = position;
        this.questionId = questionId;
        this.optionOrder = optionOrder;
    }

    public void answer(int selectedIndex, boolean correct) {
        this.selectedIndex = selectedIndex;
        this.correct = correct;
    }

    public void mark(boolean correct) {
        this.correct = correct;
    }

    /** Position on screen -> original option position. */
    public int originalIndexOf(int displayedIndex) {
        return Integer.parseInt(optionOrder.split(",")[displayedIndex]);
    }

    /** Original option position -> position on screen. */
    public int displayedIndexOf(int originalIndex) {
        String[] order = optionOrder.split(",");
        for (int i = 0; i < order.length; i++) {
            if (Integer.parseInt(order[i]) == originalIndex) {
                return i;
            }
        }
        return -1;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getOptionOrder() {
        return optionOrder;
    }

    public Integer getSelectedIndex() {
        return selectedIndex;
    }

    public Boolean getCorrect() {
        return correct;
    }
}
