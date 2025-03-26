package com.ProdeMaster.UserServices.Controller;

import com.ProdeMaster.UserServices.Security.JwtUtil;
import com.ProdeMaster.UserServices.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/")
    public ResponseEntity<?> users() {
        LOGGER.info("All user");
        return ResponseEntity.ok(userService.usersNames());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> user(@RequestHeader("Authorization") String token,  @PathVariable Long id) {
        String tokenVerify = token.substring(7);
        String username = jwtUtil.validateToken(tokenVerify);
        LOGGER.info("Profile user: {}", username);
        return ResponseEntity.ok(userService.userProfile(username));
    }

}