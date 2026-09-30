package com.secureportal.quiz;

/** How closely an AI-generated question ties to the transcript it came from. Never set for a manually
 *  typed or CSV-imported question — there's nothing to classify it against. */
public enum TranscriptGrounding {
    /** The answer is something the transcript states outright — a fact, number, name or step. */
    DIRECT,
    /** The question needs connecting or applying ideas from the transcript, not a single stated answer. */
    RELATED
}
