package com.secureportal.user;

import com.secureportal.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A promote-only admin UI is a one-way ratchet — a mis-click has no undo
 * short of editing the database directly. Both directions live here
 * together with two guards that only matter for the revoke path: an admin
 * can't remove their own access (locks them out of the page that would let
 * them undo it), and the last remaining admin can't be demoted by anyone
 * (locks *everyone* out, permanently, since role is no longer just a
 * seeded env-var allow-list once this UI exists).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void promoteToAdmin(Long targetUserId, String actorEmail) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (target.getRole() == Role.ADMIN) {
            return;
        }

        target.setRole(Role.ADMIN);
        auditService.log(actorEmail, "GRANT_ADMIN", null, "Granted admin to " + target.getEmail());
    }

    @Transactional
    public void demoteToViewer(Long targetUserId, String actorEmail, Long actingUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (target.getId().equals(actingUserId)) {
            throw new UserManagementException("You can't remove your own admin access.");
        }

        if (target.getRole() != Role.ADMIN) {
            return;
        }

        if (userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new UserManagementException("Can't remove the last remaining admin.");
        }

        target.setRole(Role.VIEWER);
        auditService.log(actorEmail, "REVOKE_ADMIN", null, "Revoked admin from " + target.getEmail());
    }
}
