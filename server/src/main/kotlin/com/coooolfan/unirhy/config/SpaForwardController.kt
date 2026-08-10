package com.coooolfan.unirhy.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaForwardController {

    @GetMapping("/{path:^(?!api$|assets$|ws$)[^.]*}/**")
    fun forward(): String = "forward:/index.html"
}
