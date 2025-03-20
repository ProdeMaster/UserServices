package com.ProdeMaster.UserServices.Service;

import com.ProdeMaster.UserServices.Model.UserModel;
import com.ProdeMaster.UserServices.Repository.UserRepository;
import com.ProdeMaster.UserServices.Security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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

    public String authenticate(String username, String password) {
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

    public Optional<UserModel> userProfile(String userName) {
        return userRepository.findByUsername(userName);
    }

    public List<String> usersNames() {
        List<UserModel> users = userRepository.findAll();
        return users.stream().map(UserModel::getUsername).collect(Collectors.toList());
    }
}
