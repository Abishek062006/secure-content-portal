package com.secureportal.api;

import com.secureportal.api.dto.ProfileDto;
import com.secureportal.api.dto.ProfileDto.EntryDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.profile.EntryKind;
import com.secureportal.profile.Profile;
import com.secureportal.profile.ProfileNotFoundException;
import com.secureportal.profile.ProfileService;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/** A member's own profile (edit) and anyone's profile (read). */
@RestController
public class ProfileApiController {

    private final ProfileService profileService;
    private final ProfileAssembler assembler;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public ProfileApiController(ProfileService profileService, ProfileAssembler assembler, UserRepository userRepository,
                                StorageService storageService) {
        this.profileService = profileService;
        this.assembler = assembler;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    public record DetailsRequest(String headline, String about, String location, String website) {
    }

    public record EntryRequest(EntryKind kind, String title, String subtitle, Integer startYear, Integer endYear,
                               String description) {
    }

    @GetMapping("/api/profile")
    public ProfileDto mine(@AuthenticationPrincipal AppPrincipal principal) {
        return view(principal.getUserId(), principal);
    }

    @GetMapping("/api/profiles/{userId}")
    public ProfileDto profile(@PathVariable Long userId, @AuthenticationPrincipal AppPrincipal principal) {
        return view(userId, principal);
    }

    @PutMapping("/api/profile")
    public ProfileDto update(@RequestBody DetailsRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        profileService.update(principal.getUserId(), request.headline(), request.about(), request.location(), request.website());
        return view(principal.getUserId(), principal);
    }

    @PostMapping("/api/profile/avatar")
    public ProfileDto avatar(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AppPrincipal principal) {
        profileService.setImage(principal.getUserId(), false, file);
        return view(principal.getUserId(), principal);
    }

    @PostMapping("/api/profile/banner")
    public ProfileDto banner(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AppPrincipal principal) {
        profileService.setImage(principal.getUserId(), true, file);
        return view(principal.getUserId(), principal);
    }

    @DeleteMapping("/api/profile/avatar")
    public ProfileDto removeAvatar(@AuthenticationPrincipal AppPrincipal principal) {
        profileService.removeImage(principal.getUserId(), false);
        return view(principal.getUserId(), principal);
    }

    @DeleteMapping("/api/profile/banner")
    public ProfileDto removeBanner(@AuthenticationPrincipal AppPrincipal principal) {
        profileService.removeImage(principal.getUserId(), true);
        return view(principal.getUserId(), principal);
    }

    @PostMapping("/api/profile/entries")
    public EntryDto addEntry(@RequestBody EntryRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        if (request.kind() == null) {
            throw new com.secureportal.profile.InvalidProfileException("Choose what you're adding.");
        }
        return assembler.entry(profileService.addEntry(principal.getUserId(), request.kind(), request.title(),
                request.subtitle(), request.startYear(), request.endYear(), request.description()));
    }

    @PutMapping("/api/profile/entries/{id}")
    public EntryDto updateEntry(@PathVariable UUID id, @RequestBody EntryRequest request,
                                @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.entry(profileService.updateEntry(principal.getUserId(), id, request.title(), request.subtitle(),
                request.startYear(), request.endYear(), request.description()));
    }

    @DeleteMapping("/api/profile/entries/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        profileService.deleteEntry(principal.getUserId(), id);
    }

    @GetMapping("/api/profiles/{userId}/avatar")
    public ResponseEntity<byte[]> avatarImage(@PathVariable Long userId) {
        Profile profile = profileService.get(userId);
        return image(profile.getAvatarKey(), profile.getAvatarMime());
    }

    @GetMapping("/api/profiles/{userId}/banner")
    public ResponseEntity<byte[]> bannerImage(@PathVariable Long userId) {
        Profile profile = profileService.get(userId);
        return image(profile.getBannerKey(), profile.getBannerMime());
    }

    private ProfileDto view(Long userId, AppPrincipal viewer) {
        User user = userRepository.findById(userId).orElseThrow(ProfileNotFoundException::new);
        return assembler.profile(user, viewer.getUserId());
    }

    private ResponseEntity<byte[]> image(String key, String mime) {
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try (StorageObject object = storageService.get(key, null, null)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(object.content().readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read profile image " + key, e);
        }
    }
}
