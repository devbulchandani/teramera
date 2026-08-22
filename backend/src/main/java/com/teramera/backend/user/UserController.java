package com.teramera.backend.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/me")
public class UserController {

    public record UpdateNameRequest(String name) {}
    public record Profile(String id, String phone, String email, String name, String avatarUrl) {}

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public Profile me(Authentication auth) {
        return toProfile(users.byId(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @PatchMapping
    public Profile updateName(@RequestBody UpdateNameRequest request, Authentication auth) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be 1-60 characters");
        }
        users.updateName(auth.getName(), name);
        return me(auth);
    }

    private static Profile toProfile(UserRepository.User user) {
        return new Profile(
                user.id(),
                user.phone() == null ? "" : user.phone(),
                user.email() == null ? "" : user.email(),
                user.name() == null ? "" : user.name(),
                user.avatarUrl() == null ? "" : user.avatarUrl()
        );
    }
}
