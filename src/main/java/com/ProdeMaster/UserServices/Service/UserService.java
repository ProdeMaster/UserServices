package com.ProdeMaster.UserServices.Service;

import com.ProdeMaster.UserServices.Dto.UpdateUserDto;
import com.ProdeMaster.UserServices.Dto.UserDto;
import com.ProdeMaster.UserServices.Model.UserModel;
import com.ProdeMaster.UserServices.Repository.UserRepository;
import com.ProdeMaster.UserServices.Security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String getAuthenticatedUser(String username, String password) {
        Optional<UserModel> user = userRepository.findByUsername(username);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            return jwtUtil.generateToken(username);
        }
        return null;
    }

    public UserModel registerUser(UserModel user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public Optional<UserDto> userProfile(String userName) {
        return userRepository.findByUsername(userName).map(user -> new UserDto(user.getId(), user.getUsername(), user.getEmail()));
    }

    public List<String> getUsersNames() {
        System.out.println("GET USERS NAMES");
        List<UserModel> users = userRepository.findAll();
        System.out.println(users);
        return users.stream().map(UserModel::getUsername).collect(Collectors.toList());
    }

//    public List<UserModel> searchUsers(String username, String email) {
//        return userRepository.searchUsers(username, email);
//    }

    public void updateUser(UpdateUserDto userData, String username, String token) {
        Optional<UserModel> user = userRepository.findByUsername(username);
        if (user.isPresent() && Objects.equals(user.get().getUsername(), jwtUtil.validateToken(token))) {
            user.get().setEmail (userData.getEmail());
            user.get().setUsername(userData.getUsername());
            userRepository.save(user.get());
        }
        else {
            throw new RuntimeException("Usuario no encontrado o token inválido");
        }
    }

    public Optional<UserDto> softDeleteUser(String token) {
        String tokenVerify = token.substring(7);
        String userName = jwtUtil.validateToken(tokenVerify);
        Optional<UserModel> user = userRepository.findByUsername(userName);
        if (user.isPresent()) {
            user.get().setDeleted(true);
            userRepository.save(user.get());
            return userRepository.findByUsername(userName).map(User -> new UserDto(User.getId(), User.getUsername(), User.getEmail()));
        }
        else {
            throw new RuntimeException("Usuario no encontrado o token inválido");
        }
    }

    public void ActiveUser (String username, String password) {
        Optional<UserModel> user = userRepository.findByUsername(username);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            user.get().setDeleted(false);
            userRepository.save(user.get());
        }
        else {
            throw new RuntimeException("Usuario no encontrado o token inválido");
        }
    }
}
