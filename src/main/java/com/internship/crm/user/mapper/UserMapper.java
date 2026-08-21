package com.internship.crm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.crm.user.domain.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus data access entry point for users. */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    default Optional<User> findByUsernameIgnoreCase(String username) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .apply("LOWER(username) = LOWER({0})", username)
                .last("LIMIT 1");
        return Optional.ofNullable(selectOne(query));
    }

    default boolean existsByUsernameIgnoreCase(String username) {
        return selectCount(new LambdaQueryWrapper<User>()
                .apply("LOWER(username) = LOWER({0})", username)) > 0;
    }

    default boolean existsByEmailIgnoreCase(String email) {
        return selectCount(new LambdaQueryWrapper<User>()
                .apply("LOWER(email) = LOWER({0})", email)) > 0;
    }
}
