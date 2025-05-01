package com.ProdeMaster.UserServices.Controller;

import com.ProdeMaster.UserServices.Dto.UpdateUserDto;
import com.ProdeMaster.UserServices.Dto.UserDto;
import com.ProdeMaster.UserServices.Security.JwtUtil;
import com.ProdeMaster.UserServices.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.stream.Stream;

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
           UserDto newUser = userService.updateUser(token, updateData);
            LOGGER.info("Profile update user: {}", newUser.getUsername());
            return ResponseEntity.ok(newUser);
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

    @DeleteMapping("")
    public ResponseEntity<?> deleteUser(@RequestHeader("Authorization") String token) {
        Optional<UserDto> user = userService.softDeleteUser(token);
        LOGGER.info("Delete user: {}", user.get().getUsername());
        return ResponseEntity.ok(user);
    }
    @GetMapping("/search")
    public ResponseEntity<?> getSearchUser(@RequestHeader("Authorization") String token, @RequestParam String username, @RequestParam String email) {
        LOGGER.info("Search user: {}", username);
        Stream<UserDto> users = userService.searchUsers(token, username, email);
        return ResponseEntity.ok(users);
    }
}