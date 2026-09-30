package com.secureportal.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.ai.LlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumeKeywordsTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final ResumeKeywords keywords = new ResumeKeywords(llm, new ObjectMapper());

    @Test
    void theAiListIsUsedWhenItGivesEnoughAndEveryKeywordIsTidiedIntoASkillLabel() {
        when(llm.complete(anyString(), anyString())).thenReturn(
                "```json\n{\"keywords\":[\"Java\",\"Spring Boot\",\"java\",\"<script>Kafka</script>\",\"   \",\"PostgreSQL\"]}\n```");

        assertThat(keywords.extract("Some resume text")).containsExactly("Java", "Spring Boot", "script Kafka /script", "PostgreSQL");
    }

    @Test
    void withoutTheAiTheKnownTechnologiesAreFoundInTheOrderTheResumeMentionsThem() {
        when(llm.complete(anyString(), anyString())).thenThrow(new AiNotConfiguredException());

        String resume = "Built services in Node.js and PostgreSQL, deployed with Docker on AWS. Wrote the front end in React Native "
                + "and some JavaScript tooling. Comfortable with C++ and C#.";
        assertThat(keywords.extract(resume))
                .containsExactly("Node.js", "PostgreSQL", "Docker", "AWS", "React Native", "JavaScript", "C++", "C#");
    }

    @Test
    void everydayWordsOnlyCountWhenWrittenTheWayTheTechnologyIs() {
        assertThat(ResumeKeywords.fromKnownList("I led the rest of the team through a swift spring release.")).isEmpty();
        assertThat(ResumeKeywords.fromKnownList("Designed a REST API in Spring and Rust.")).containsExactly("REST", "Spring", "Rust");
        // A name inside a longer word isn't a match: Java isn't in JavaScript, SQL isn't in MySQL.
        assertThat(ResumeKeywords.fromKnownList("JavaScript and MySQL")).containsExactly("JavaScript", "MySQL");
    }

    @Test
    void aTooShortAiAnswerIsToppedUpFromTheKnownList() {
        when(llm.complete(anyString(), anyString())).thenReturn("{\"keywords\":[\"Kotlin\"]}");

        assertThat(keywords.extract("Android apps in Kotlin with Firebase and Git.")).containsExactly("Kotlin", "Android", "Firebase", "Git");
    }
}
