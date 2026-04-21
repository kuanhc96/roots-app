package com.roots.simple_resource_server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role")
public class RoleController {

    @GetMapping("/pastor")
    public String pastor() {
        return "I am a pastor";
    }

    @GetMapping("/deacon")
    public String deacon() {
        return "I am a deacon";
    }

    @GetMapping("/small-group-leader")
    public String smallGroupLeader() {
        return "I am a small group leader";
    }

    @GetMapping("/vice-small-group-leader")
    public String viceSmallGroupLeader() {
        return "I am a vice small group leader";
    }

    @GetMapping("/member")
    public String member() {
        return "I am a member";
    }

    @GetMapping("/guest")
    public String guest() {
        return "I am a guest";
    }
}
