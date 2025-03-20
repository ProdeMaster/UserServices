package com.ProdeMaster.UserServices.Controller;

import com.ProdeMaster.UserServices.Model.UserModel;
import com.ProdeMaster.UserServices.Service.UserService;
import com.ProdeMaster.UserServices.Security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String token = userService.authenticate(credentials.get("username"), credentials.get("password"));
        if (token != null) {
            return ResponseEntity.ok(Map.of("token", token));
        }
        return ResponseEntity.status(401).body("Credenciales inválidas");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserModel user) {
        return ResponseEntity.ok(userService.registerUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> user(@RequestHeader("Authorization") String token,  @PathVariable Long ig) {
        JwtUtil jwtUtil = new JwtUtil();
        UserService userService = new UserService();
        String username = jwtUtil.validateToken(token);

        return ResponseEntity.ok(userService.userProfile(username));
    }
}
