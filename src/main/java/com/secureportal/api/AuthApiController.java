package com.secureportal.api;

import com.secureportal.api.dto.MeResponse;
import com.secureportal.api.dto.UserDto;
import com.secureportal.auth.AppPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The frontend's auth-state check on load — permitAll, since it has to work when signed out. */
@RestController
public class AuthApiController {

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal == null) {
            return MeResponse.anonymous();
        }
        return MeResponse.of(UserDto.from(principal));
    }
}
