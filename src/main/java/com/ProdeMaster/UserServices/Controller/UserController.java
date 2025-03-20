package com.ProdeMaster.UserServices.Controller;

import com.ProdeMaster.UserServices.Security.JwtUtil;
import com.ProdeMaster.UserServices.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/")
    public ResponseEntity<?> users() {
        return ResponseEntity.ok(userService.usersNames());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> user(@RequestHeader("Authorization") String token,  @PathVariable Long id) {
        String tokenVerify = token.substring(7);
        String username = jwtUtil.validateToken(tokenVerify);
        return ResponseEntity.ok(userService.userProfile(username));
    }

}