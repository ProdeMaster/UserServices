package com.ProdeMaster.UserServices.Controller;

import com.ProdeMaster.UserServices.Model.UserModel;
import com.ProdeMaster.UserServices.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String token = userService.authenticate(credentials.get("username"), credentials.get("password"));
        LOGGER.info("Login user: {}", credentials.get("username"));
        if (token != null) {
            return ResponseEntity.ok(Map.of("token", token));
        }
        return ResponseEntity.status(401).body("Credenciales inválidas");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserModel user) {
        LOGGER.info("Register user: {}", user.getUsername());
        return ResponseEntity.ok(userService.registerUser(user));
    }
}
