package com.secureportal.quiz;

/** What styles of question an AI generation run should write. */
public enum QuestionMix {
    /** Concept questions, plus coding ones when the lecture teaches code. */
    MIXED,
    /** Multiple choice only. */
    CHOICE,
    /** As many fill-the-code and predict-the-output questions as the lecture supports. */
    CODING
}
