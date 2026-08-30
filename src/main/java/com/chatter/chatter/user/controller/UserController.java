package com.chatter.chatter.user.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatter.chatter.user.dto.UserDTO;
import com.chatter.chatter.user.security.AuthenticatedUser;
import com.chatter.chatter.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Lets a client find someone to start a chat with. */
    @GetMapping("/search")
    public List<UserDTO> search(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @RequestParam("q") String query) {
        return userService.search(query, principal.id()).stream().map(UserDTO::from).toList();
    }
}
