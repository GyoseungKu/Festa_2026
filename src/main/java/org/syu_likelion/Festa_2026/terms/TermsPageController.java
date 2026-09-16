package org.syu_likelion.Festa_2026.terms;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TermsPageController {
    @GetMapping("/terms/service")
    public String service() {
        return "terms/service";
    }

    @GetMapping("/terms/privacy")
    public String privacy() {
        return "terms/privacy";
    }
}
