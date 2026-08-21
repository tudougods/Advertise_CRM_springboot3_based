package com.internship.crm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.crm.user.domain.User;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus data access entry point for users. */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
