package com.secureportal.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String headline;

    @Column(length = 2600)
    private String about;

    private String location;

    private String website;

    @Column(name = "avatar_key")
    private String avatarKey;

    @Column(name = "avatar_mime")
    private String avatarMime;

    @Column(name = "banner_key")
    private String bannerKey;

    @Column(name = "banner_mime")
    private String bannerMime;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Profile() {
        // for JPA
    }

    public Profile(Long userId) {
        this.userId = userId;
    }

    public void setDetails(String headline, String about, String location, String website) {
        this.headline = headline;
        this.about = about;
        this.location = location;
        this.website = website;
        this.updatedAt = Instant.now();
    }

    public void setAvatar(String key, String mime) {
        this.avatarKey = key;
        this.avatarMime = mime;
        this.updatedAt = Instant.now();
    }

    public void setBanner(String key, String mime) {
        this.bannerKey = key;
        this.bannerMime = mime;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getHeadline() {
        return headline;
    }

    public String getAbout() {
        return about;
    }

    public String getLocation() {
        return location;
    }

    public String getWebsite() {
        return website;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public String getAvatarMime() {
        return avatarMime;
    }

    public String getBannerKey() {
        return bannerKey;
    }

    public String getBannerMime() {
        return bannerMime;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
