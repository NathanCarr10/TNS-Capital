package com.neueda.leap.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Redirect controller for Swagger UI convenience paths
 */
@Controller
public class SwaggerRedirectController {
    
    @GetMapping("/swagger-ui.html")
    public RedirectView redirectSwaggerUI() {
        return new RedirectView("/swagger-ui/index.html");
    }
    
    @GetMapping("/swagger")
    public RedirectView redirectSwagger() {
        return new RedirectView("/swagger-ui/index.html");
    }
    
    @GetMapping("/docs")
    public RedirectView redirectDocs() {
        return new RedirectView("/swagger-ui/index.html");
    }
}
