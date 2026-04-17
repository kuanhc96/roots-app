package com.roots.authserver;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-file, non-API requests to index.html so that
 * Nuxt's client-side router handles them correctly.
 */
@Controller
public class SpaController {

    @GetMapping(value = { "/"})
    public String forward() {
        return "forward:/index.html";
    }
}
