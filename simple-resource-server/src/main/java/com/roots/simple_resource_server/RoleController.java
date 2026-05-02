package com.roots.simple_resource_server;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "${web.client.origin:http://localhost:3000}")
@RestController
@RequestMapping("/api/role")
public class RoleController {

    @PreAuthorize("hasAuthority('WEB_CLIENT_READ') and hasRole('PASTOR')")
    @GetMapping("/pastor")
    public String pastor() {
        return "I am a pastor";
    }

    @PreAuthorize("hasAuthority('WEB_CLIENT_READ') and hasRole('DEACON')")
    @GetMapping("/deacon")
    public String deacon() {
        return "I am a deacon";
    }

    @PreAuthorize("hasAuthority('WEB_CLIENT_READ') and hasRole('SMALL_GROUP_LEADER')")
    @GetMapping("/small-group-leader")
    public String smallGroupLeader() {
        return "I am a small group leader";
    }

    @PreAuthorize("hasAuthority('WEB_CLIENT_READ') and hasRole('VICE_SMALL_GROUP_LEADER')")
    @GetMapping("/vice-small-group-leader")
    public String viceSmallGroupLeader() {
        return "I am a vice small group leader";
    }

    @PreAuthorize("hasAuthority('WEB_CLIENT_READ') and hasRole('MEMBER')")
    @GetMapping("/member")
    public String member() {
        return "I am a member";
    }

    @GetMapping("/guest")
    public String guest() {
        return "I am a guest";
    }
}
