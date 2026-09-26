package com.secureportal.hackathon;

import com.secureportal.gamification.GamificationService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HackathonService {

    private final HackathonRepository hackathonRepository;
    private final HackathonRegistrationRepository registrationRepository;
    private final GamificationService gamificationService;

    public HackathonService(HackathonRepository hackathonRepository,
                            HackathonRegistrationRepository registrationRepository,
                            GamificationService gamificationService) {
        this.hackathonRepository = hackathonRepository;
        this.registrationRepository = registrationRepository;
        this.gamificationService = gamificationService;
    }

    @PostConstruct
    public void seedDefaultHackathons() {
        if (hackathonRepository.count() == 0) {
            Instant now = Instant.now();
            Hackathon h1 = new Hackathon(
                    "Global Generative AI & Agentic Code Sprint 2026",
                    "Google AI & GradientNova",
                    "Build next-generation autonomous AI agents and multimodal applications. Compete globally with top engineers and showcase your project to lead tech sponsors.",
                    "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80",
                    "AI & Data Science",
                    "ONLINE",
                    "Global / Virtual",
                    "$25,000 Prize Pool",
                    "https://devpost.com",
                    now.plus(14, ChronoUnit.DAYS),
                    now.plus(15, ChronoUnit.DAYS),
                    now.plus(18, ChronoUnit.DAYS),
                    true,
                    "ACTIVE",
                    30
            );

            Hackathon h2 = new Hackathon(
                    "Cloud-Native Distributed Systems Hackathon",
                    "AWS & Spring Cloud Guild",
                    "Design high-throughput, fault-tolerant cloud microservices using Spring Boot, Kubernetes, and Redis. Show off real-time telemetry and scaling architecture.",
                    "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=800&q=80",
                    "Cloud & Infrastructure",
                    "ONLINE",
                    "Global / Virtual",
                    "$15,000 + AWS Credits",
                    "https://unstop.com",
                    now.plus(7, ChronoUnit.DAYS),
                    now.plus(8, ChronoUnit.DAYS),
                    now.plus(10, ChronoUnit.DAYS),
                    true,
                    "ACTIVE",
                    25
            );

            Hackathon h3 = new Hackathon(
                    "Full-Stack Web3 & SaaS Challenge",
                    "Meta & Devfolio",
                    "Create end-to-end full stack web applications with modern UI/UX design, real-time web sockets, and ultra-fast APIs.",
                    "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=800&q=80",
                    "Engineering & Web Dev",
                    "HYBRID",
                    "San Francisco & Online",
                    "$10,000 Cash Prize",
                    "https://devfolio.co",
                    now.plus(21, ChronoUnit.DAYS),
                    now.plus(22, ChronoUnit.DAYS),
                    now.plus(24, ChronoUnit.DAYS),
                    false,
                    "UPCOMING",
                    25
            );

            Hackathon h4 = new Hackathon(
                    "NextGen UI/UX Designathon",
                    "Dribbble & Figma Collective",
                    "Design futuristic glassmorphism user interfaces and seamless design systems for learning & community platforms.",
                    "https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?auto=format&fit=crop&w=800&q=80",
                    "UI/UX & Design",
                    "ONLINE",
                    "Virtual",
                    "$5,000 Prize Pool",
                    "https://dribbble.com",
                    now.plus(5, ChronoUnit.DAYS),
                    now.plus(6, ChronoUnit.DAYS),
                    now.plus(7, ChronoUnit.DAYS),
                    false,
                    "ACTIVE",
                    20
            );

            hackathonRepository.saveAll(List.of(h1, h2, h3, h4));
        }
    }

    public List<Map<String, Object>> getHackathonsForUser(String stream, String mode, Long userId) {
        List<Hackathon> list;
        if (stream != null && !stream.equalsIgnoreCase("all")) {
            list = hackathonRepository.findByStreamIgnoreCaseOrderByFeaturedDescCreatedAtDesc(stream);
        } else {
            list = hackathonRepository.findAllByOrderByFeaturedDescCreatedAtDesc();
        }

        if (mode != null && !mode.equalsIgnoreCase("all")) {
            list = list.stream()
                    .filter(h -> h.getMode().equalsIgnoreCase(mode))
                    .collect(Collectors.toList());
        }

        List<HackathonRegistration> userRegs = userId != null ? registrationRepository.findByUserId(userId) : List.of();
        Map<Long, Boolean> registeredMap = userRegs.stream()
                .collect(Collectors.toMap(HackathonRegistration::getHackathonId, r -> true, (a, b) -> a));

        return list.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("title", h.getTitle());
            map.put("organizer", h.getOrganizer());
            map.put("description", h.getDescription());
            map.put("bannerUrl", h.getBannerUrl());
            map.put("stream", h.getStream());
            map.put("mode", h.getMode());
            map.put("location", h.getLocation());
            map.put("prizePool", h.getPrizePool());
            map.put("registrationUrl", h.getRegistrationUrl());
            map.put("registrationDeadline", h.getRegistrationDeadline());
            map.put("eventStartDate", h.getEventStartDate());
            map.put("eventEndDate", h.getEventEndDate());
            map.put("featured", h.isFeatured());
            map.put("status", h.getStatus());
            map.put("pointsReward", h.getPointsReward());
            map.put("participantCount", registrationRepository.countByHackathonId(h.getId()));
            map.put("isRegistered", registeredMap.getOrDefault(h.getId(), false));
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> registerUserForHackathon(Long userId, Long hackathonId) {
        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));

        boolean alreadyReg = registrationRepository.existsByHackathonIdAndUserId(hackathonId, userId);
        if (alreadyReg) {
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("alreadyRegistered", true);
            res.put("message", "Already registered for this hackathon");
            res.put("registrationUrl", hackathon.getRegistrationUrl());
            return res;
        }

        int points = hackathon.getPointsReward();
        HackathonRegistration reg = new HackathonRegistration(hackathonId, userId, points);
        registrationRepository.save(reg);

        gamificationService.awardPoints(userId, points, "HACKATHON_REGISTER", "Registered for hackathon: " + hackathon.getTitle(), hackathon.getStream());

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("alreadyRegistered", false);
        res.put("pointsEarned", points);
        res.put("message", "Registered successfully! Earned +" + points + " XP.");
        res.put("registrationUrl", hackathon.getRegistrationUrl());
        return res;
    }

    @Transactional
    public Hackathon createHackathon(Hackathon h) {
        h.setCreatedAt(Instant.now());
        h.setUpdatedAt(Instant.now());
        return hackathonRepository.save(h);
    }

    @Transactional
    public Hackathon updateHackathon(Long id, Hackathon updated) {
        Hackathon existing = hackathonRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));
        existing.setTitle(updated.getTitle());
        if (updated.getOrganizer() != null && !updated.getOrganizer().isBlank()) {
            existing.setOrganizer(updated.getOrganizer());
        }
        existing.setDescription(updated.getDescription());
        existing.setBannerUrl(updated.getBannerUrl());
        existing.setStream(updated.getStream());
        existing.setMode(updated.getMode());
        existing.setLocation(updated.getLocation());
        existing.setPrizePool(updated.getPrizePool());
        existing.setRegistrationUrl(updated.getRegistrationUrl());
        existing.setRegistrationDeadline(updated.getRegistrationDeadline());
        existing.setEventStartDate(updated.getEventStartDate());
        existing.setEventEndDate(updated.getEventEndDate());
        existing.setFeatured(updated.isFeatured());
        existing.setStatus(updated.getStatus());
        existing.setPointsReward(updated.getPointsReward());
        existing.setUpdatedAt(Instant.now());
        return hackathonRepository.save(existing);
    }

    @Transactional
    public void deleteHackathon(Long id) {
        hackathonRepository.deleteById(id);
    }
}
