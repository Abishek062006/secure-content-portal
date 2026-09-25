package com.secureportal.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class CourseCreateForm {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    private String title;

    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    @Size(max = 80, message = "Category must be 80 characters or fewer")
    private String category;

    private MultipartFile thumbnail;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public MultipartFile getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(MultipartFile thumbnail) {
        this.thumbnail = thumbnail;
    }
}
