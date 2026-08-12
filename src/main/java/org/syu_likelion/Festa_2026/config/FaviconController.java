package org.syu_likelion.Festa_2026.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FaviconController {
    @GetMapping("/favicon.ico")
    String favicon() {
        return "redirect:/images/favicon.svg";
    }
}
