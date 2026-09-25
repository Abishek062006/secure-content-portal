package com.secureportal.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class QuestionOption {

    @Column(name = "option_text", nullable = false, length = 500)
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    protected QuestionOption() {
        // for JPA
    }

    public QuestionOption(String text, boolean correct) {
        this.text = text;
        this.correct = correct;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return correct;
    }
}
