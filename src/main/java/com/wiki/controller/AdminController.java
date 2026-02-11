package com.wiki.controller;

import com.wiki.model.Role;
import com.wiki.model.User;
import com.wiki.repository.RoleRepository;
import com.wiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", username);

        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());
        result.put("roles", roles);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(user -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", user.getId());
                    map.put("username", user.getUsername());
                    map.put("displayName", user.getDisplayName());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRoles() {
        List<Map<String, Object>> roles = roleRepository.findAll().stream()
                .map(role -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", role.getId());
                    map.put("name", role.getName());
                    map.put("description", role.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }
}
