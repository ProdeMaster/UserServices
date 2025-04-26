package com.ProdeMaster.UserServices.Controller;

import com.ProdeMaster.UserServices.Dto.UpdateUserDto;
import com.ProdeMaster.UserServices.Security.JwtUtil;
import com.ProdeMaster.UserServices.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userService.getUsersNames());
    }

    @PutMapping("")
    public ResponseEntity<?> updateUser(@RequestHeader("Authorization") String token, @RequestBody UpdateUserDto updateData) {
        try {
            String tokenVerify = token.substring(7);
            String username = jwtUtil.validateToken(tokenVerify);
            userService.updateUser(updateData, username, token);
            LOGGER.info("Profile update user: {}", username);
            return ResponseEntity.ok().build();
        }
        catch (Exception e) {
            LOGGER.info("Update error: {}", e.getCause());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@RequestHeader("Authorization") String token,  @PathVariable Long id) {
        String tokenVerify = token.substring(7);
        String username = jwtUtil.validateToken(tokenVerify);
        LOGGER.info("Profile user: {}", username);
        return ResponseEntity.ok(userService.userProfile(username));
    }

//    @GetMapping("/search")
//    public ResponseEntity<?> getUsersByName(@RequestParam String name) {
//
//    }
}